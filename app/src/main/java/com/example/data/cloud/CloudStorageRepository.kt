package com.example.data.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.example.BuildConfig
import com.example.data.local.CloudTokenStore
import com.example.data.local.StoredCloudToken
import com.example.data.pdf.awaitResult
import com.example.model.CloudAccountState
import com.example.model.CloudProvider
import com.example.model.CloudUploadRecord
import com.example.model.RemoteDrivePdfItem
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed class GoogleAuthLaunchOutcome {
    data class Authorized(val account: CloudAccountState) : GoogleAuthLaunchOutcome()
    data class NeedsResolution(val intentSender: android.content.IntentSender) : GoogleAuthLaunchOutcome()
    data class Failed(val message: String) : GoogleAuthLaunchOutcome()
}

class CloudStorageRepository(
    private val context: Context,
    private val tokenStore: CloudTokenStore
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    fun observeAccounts(): Flow<List<CloudAccountState>> =
        tokenStore.observeTokens().map { tokenMap ->
            CloudProvider.entries.map { provider ->
                val token = tokenMap[provider]
                CloudAccountState(
                    provider = provider,
                    isConnected = token != null,
                    accountEmail = token?.email,
                    accountName = token?.displayName,
                    expiresAtMs = token?.expiresAtMs ?: 0L,
                    isConfigured = CloudAuthConfig.isProviderConfigured(provider),
                    configHint = CloudAuthConfig.configurationHint(provider)
                )
            }
        }

    suspend fun authorizeGoogleDrive(): GoogleAuthLaunchOutcome {
        return try {
            val scopes = listOf(
                Scope(CloudAuthConfig.GOOGLE_DRIVE_SCOPE),
                Scope(CloudAuthConfig.GOOGLE_EMAIL_SCOPE),
                Scope(CloudAuthConfig.GOOGLE_PROFILE_SCOPE)
            )
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(scopes)
                .build()

            val result: AuthorizationResult = Identity.getAuthorizationClient(context)
                .authorize(request)
                .awaitResult()

            if (result.hasResolution()) {
                val pending = result.pendingIntent
                if (pending != null) {
                    GoogleAuthLaunchOutcome.NeedsResolution(pending.intentSender)
                } else {
                    GoogleAuthLaunchOutcome.Failed("Google Drive consent screen could not be opened.")
                }
            } else {
                val accessToken = result.accessToken
                if (accessToken.isNullOrBlank()) {
                    GoogleAuthLaunchOutcome.Failed("No access token returned by Google Play Services.")
                } else {
                    val account = finalizeGoogleToken(accessToken)
                    GoogleAuthLaunchOutcome.Authorized(account)
                }
            }
        } catch (e: Exception) {
            GoogleAuthLaunchOutcome.Failed(
                e.localizedMessage ?: "Google Drive sign-in requires a Google account on this device."
            )
        }
    }

    suspend fun handleGoogleAuthIntentResult(data: Intent?): Result<CloudAccountState> = runCatching {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        val accessToken = result.accessToken ?: error("Google authorization returned no access token.")
        finalizeGoogleToken(accessToken)
    }

    private suspend fun finalizeGoogleToken(accessToken: String): CloudAccountState {
        val (email, name) = fetchGoogleUserInfo(accessToken)
        val token = StoredCloudToken(
            accessToken = accessToken,
            expiresAtMs = System.currentTimeMillis() + 55 * 60 * 1000L,
            email = email,
            displayName = name
        )
        tokenStore.saveToken(CloudProvider.GOOGLE_DRIVE, token)
        return CloudAccountState(
            provider = CloudProvider.GOOGLE_DRIVE,
            isConnected = true,
            accountEmail = email,
            accountName = name,
            expiresAtMs = token.expiresAtMs,
            isConfigured = true
        )
    }

    suspend fun launchBrowserOAuth(provider: CloudProvider): Result<Unit> = runCatching {
        if (!CloudAuthConfig.isProviderConfigured(provider)) {
            error(CloudAuthConfig.configurationHint(provider) ?: "Provider app key not configured.")
        }
        val (verifier, challenge) = CloudAuthConfig.generatePkcePair()
        val state = CloudAuthConfig.generateState()
        tokenStore.savePendingPkce(provider, verifier, state)

        val authUri = CloudAuthConfig.buildAuthorizeUrl(provider, challenge, state)
        val customTabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabs.launchUrl(context, authUri)
    }

    suspend fun handleOAuthRedirect(uri: Uri): Result<CloudAccountState> = withContext(Dispatchers.IO) {
        runCatching {
            val provider = when (uri.path?.trim('/')) {
                "dropbox" -> CloudProvider.DROPBOX
                "onedrive" -> CloudProvider.ONEDRIVE
                "google" -> CloudProvider.GOOGLE_DRIVE
                else -> error("Unknown OAuth provider callback: ${uri.path}")
            }
            val errorParam = uri.getQueryParameter("error")
            if (!errorParam.isNullOrBlank()) {
                val desc = uri.getQueryParameter("error_description") ?: errorParam
                error("Authorization cancelled or denied: $desc")
            }

            val code = uri.getQueryParameter("code") ?: error("Authorization code missing in callback.")
            val returnedState = uri.getQueryParameter("state")
            val (verifier, expectedState) = tokenStore.consumePendingPkce(provider)
                ?: error("OAuth session expired. Please try connecting again.")

            if (returnedState != expectedState) {
                error("OAuth state mismatch. Please try connecting again.")
            }

            exchangeCodeForToken(provider, code, verifier)
        }
    }

    private suspend fun exchangeCodeForToken(
        provider: CloudProvider,
        code: String,
        verifier: String
    ): CloudAccountState = withContext(Dispatchers.IO) {
        val redirect = CloudAuthConfig.redirectUriFor(provider)
        val tokenUrl = when (provider) {
            CloudProvider.DROPBOX -> "https://api.dropboxapi.com/oauth2/token"
            CloudProvider.ONEDRIVE -> "https://login.microsoftonline.com/common/oauth2/v2.0/token"
            CloudProvider.GOOGLE_DRIVE -> "https://oauth2.googleapis.com/token"
        }

        val formBuilder = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirect)
            .add("code_verifier", verifier)

        when (provider) {
            CloudProvider.DROPBOX -> formBuilder.add("client_id", BuildConfig.DROPBOX_APP_KEY)
            CloudProvider.ONEDRIVE -> formBuilder.add("client_id", BuildConfig.ONEDRIVE_CLIENT_ID)
            CloudProvider.GOOGLE_DRIVE -> formBuilder.add("client_id", BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
        }

        val request = Request.Builder()
            .url(tokenUrl)
            .post(formBuilder.build())
            .build()

        val bodyText = httpClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("Token exchange failed (${resp.code}): $text")
            }
            text
        }

        val json = JSONObject(bodyText)
        val accessToken = json.getString("access_token")
        val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L

        val (email, displayName) = when (provider) {
            CloudProvider.DROPBOX -> fetchDropboxAccount(accessToken)
            CloudProvider.ONEDRIVE -> fetchOneDriveAccount(accessToken)
            CloudProvider.GOOGLE_DRIVE -> fetchGoogleUserInfo(accessToken)
        }

        tokenStore.saveToken(
            provider,
            StoredCloudToken(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMs = expiresAtMs,
                email = email,
                displayName = displayName
            )
        )

        CloudAccountState(
            provider = provider,
            isConnected = true,
            accountEmail = email,
            accountName = displayName,
            expiresAtMs = expiresAtMs,
            isConfigured = true
        )
    }

    suspend fun disconnect(provider: CloudProvider) {
        tokenStore.clearToken(provider)
    }

    suspend fun uploadPdf(
        provider: CloudProvider,
        filePath: String,
        fileName: String,
        existingDriveFileId: String? = null,
        metadataProperties: Map<String, String> = emptyMap()
    ): Result<CloudUploadRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(filePath)
            if (!file.exists()) error("Local PDF file not found.")

            var token = tokenStore.getToken(provider)
                ?: error("${provider.displayName} is not connected. Authenticate first.")

            if (token.expiresAtMs > 0L && System.currentTimeMillis() > token.expiresAtMs - 60_000L) {
                token = refreshAccessTokenIfPossible(provider, token) ?: token
            }

            when (provider) {
                CloudProvider.GOOGLE_DRIVE -> uploadToGoogleDriveWithRetry(
                    accessToken = token.accessToken,
                    file = file,
                    fileName = fileName,
                    existingDriveFileId = existingDriveFileId,
                    metadataProperties = metadataProperties
                )
                CloudProvider.DROPBOX -> uploadToDropbox(token.accessToken, file, fileName)
                CloudProvider.ONEDRIVE -> uploadToOneDrive(token.accessToken, file, fileName)
            }
        }
    }

    suspend fun listGoogleDrivePdfs(): Result<List<RemoteDrivePdfItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val initialToken = tokenStore.getToken(CloudProvider.GOOGLE_DRIVE)?.accessToken
                ?: error("Google Drive is not connected. Authenticate first.")
            try {
                listGoogleDrivePdfsInternal(initialToken)
            } catch (e: DriveUnauthorizedException) {
                val refreshed = trySilentGoogleRefresh() ?: throw e
                listGoogleDrivePdfsInternal(refreshed)
            }
        }
    }

    suspend fun downloadGoogleDrivePdf(
        driveFileId: String,
        destinationFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val initialToken = tokenStore.getToken(CloudProvider.GOOGLE_DRIVE)?.accessToken
                ?: error("Google Drive is not connected. Authenticate first.")
            try {
                downloadGoogleDrivePdfInternal(initialToken, driveFileId, destinationFile)
            } catch (e: DriveUnauthorizedException) {
                val refreshed = trySilentGoogleRefresh() ?: throw e
                downloadGoogleDrivePdfInternal(refreshed, driveFileId, destinationFile)
            }
        }
    }

    suspend fun deleteGoogleDrivePdf(driveFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val initialToken = tokenStore.getToken(CloudProvider.GOOGLE_DRIVE)?.accessToken
                ?: error("Google Drive is not connected.")
            try {
                deleteGoogleDrivePdfInternal(initialToken, driveFileId)
            } catch (e: DriveUnauthorizedException) {
                val refreshed = trySilentGoogleRefresh() ?: throw e
                deleteGoogleDrivePdfInternal(refreshed, driveFileId)
            }
        }
    }

    private suspend fun trySilentGoogleRefresh(): String? {
        return when (val outcome = authorizeGoogleDrive()) {
            is GoogleAuthLaunchOutcome.Authorized -> {
                tokenStore.getToken(CloudProvider.GOOGLE_DRIVE)?.accessToken
            }
            else -> null
        }
    }

    private suspend fun refreshAccessTokenIfPossible(
        provider: CloudProvider,
        current: StoredCloudToken
    ): StoredCloudToken? {
        if (provider == CloudProvider.GOOGLE_DRIVE) {
            return when (authorizeGoogleDrive()) {
                is GoogleAuthLaunchOutcome.Authorized -> tokenStore.getToken(CloudProvider.GOOGLE_DRIVE)
                else -> null
            }
        }
        val refresh = current.refreshToken ?: return null
        val url = when (provider) {
            CloudProvider.DROPBOX -> "https://api.dropboxapi.com/oauth2/token"
            CloudProvider.ONEDRIVE -> "https://login.microsoftonline.com/common/oauth2/v2.0/token"
            CloudProvider.GOOGLE_DRIVE -> return null
        }
        val clientId = when (provider) {
            CloudProvider.DROPBOX -> BuildConfig.DROPBOX_APP_KEY
            CloudProvider.ONEDRIVE -> BuildConfig.ONEDRIVE_CLIENT_ID
            CloudProvider.GOOGLE_DRIVE -> return null
        }
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", clientId)
            .build()
        val req = Request.Builder().url(url).post(body).build()
        return try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string().orEmpty())
                val updated = current.copy(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token").ifBlank { refresh },
                    expiresAtMs = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
                )
                updated
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun uploadToGoogleDriveWithRetry(
        accessToken: String,
        file: File,
        fileName: String,
        existingDriveFileId: String?,
        metadataProperties: Map<String, String>
    ): CloudUploadRecord {
        return try {
            uploadToGoogleDrive(
                accessToken = accessToken,
                file = file,
                fileName = fileName,
                existingDriveFileId = existingDriveFileId,
                metadataProperties = metadataProperties
            )
        } catch (e: DriveUnauthorizedException) {
            val refreshed = trySilentGoogleRefresh() ?: throw e
            uploadToGoogleDrive(
                accessToken = refreshed,
                file = file,
                fileName = fileName,
                existingDriveFileId = existingDriveFileId,
                metadataProperties = metadataProperties
            )
        }
    }

    private fun uploadToGoogleDrive(
        accessToken: String,
        file: File,
        fileName: String,
        existingDriveFileId: String? = null,
        metadataProperties: Map<String, String> = emptyMap()
    ): CloudUploadRecord {
        val folderId = findOrCreateGoogleDriveFolder(accessToken)
        val targetFileId = existingDriveFileId ?: findExistingDrivePdfId(accessToken, folderId, fileName)

        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("mimeType", "application/pdf")
            if (targetFileId == null && folderId != null) {
                put("parents", org.json.JSONArray().put(folderId))
            }
            if (metadataProperties.isNotEmpty()) {
                val propsObj = JSONObject()
                metadataProperties.forEach { (k, v) ->
                    if (v.isNotBlank()) propsObj.put(k, v.take(110))
                }
                put("appProperties", propsObj)
            }
        }.toString()

        val multipart = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addPart(file.asRequestBody("application/pdf".toMediaType()))
            .build()

        val requestBuilder = Request.Builder()
            .header("Authorization", "Bearer $accessToken")

        val request = if (targetFileId != null) {
            requestBuilder
                .url("https://www.googleapis.com/upload/drive/v3/files/$targetFileId?uploadType=multipart&fields=id,name,webViewLink")
                .patch(multipart)
                .build()
        } else {
            requestBuilder
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink")
                .post(multipart)
                .build()
        }

        val respJson = httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) throw DriveUnauthorizedException()
            if (!resp.isSuccessful) error("Google Drive upload failed (${resp.code}): $body")
            JSONObject(body)
        }

        val remoteId = respJson.optString("id").takeIf { it.isNotBlank() }
        return CloudUploadRecord(
            provider = CloudProvider.GOOGLE_DRIVE,
            fileName = fileName,
            remotePathOrLink = respJson.optString("webViewLink").ifBlank { remoteId },
            remoteFileId = remoteId,
            uploadedAtMs = System.currentTimeMillis(),
            success = true,
            message = if (targetFileId != null) "Synced update to My Drive / FolioPDF" else "Backed up to My Drive / FolioPDF"
        )
    }

    private fun listGoogleDrivePdfsInternal(accessToken: String): List<RemoteDrivePdfItem> {
        val folderId = findGoogleDriveFolderIdOnly(accessToken)
        val query = if (folderId != null) {
            "'$folderId' in parents and mimeType='application/pdf' and trashed=false"
        } else {
            "mimeType='application/pdf' and trashed=false"
        }
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val fields = URLEncoder.encode(
            "files(id,name,size,modifiedTime,md5Checksum,webViewLink,appProperties)",
            "UTF-8"
        )
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$encodedQ&orderBy=modifiedTime+desc&pageSize=100&fields=$fields")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val respJson = httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) throw DriveUnauthorizedException()
            if (!resp.isSuccessful) error("Failed to list Google Drive backups (${resp.code}): $body")
            JSONObject(body)
        }

        val filesArray = respJson.optJSONArray("files") ?: return emptyList()
        val result = ArrayList<RemoteDrivePdfItem>(filesArray.length())
        for (i in 0 until filesArray.length()) {
            val obj = filesArray.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) continue
            val props = obj.optJSONObject("appProperties")
            result.add(
                RemoteDrivePdfItem(
                    driveFileId = id,
                    fileName = name,
                    sizeBytes = obj.optString("size").toLongOrNull() ?: obj.optLong("size", 0L),
                    modifiedTimeMs = parseIso8601Utc(obj.optString("modifiedTime")),
                    md5Checksum = obj.optString("md5Checksum").takeIf { it.isNotBlank() },
                    webViewLink = obj.optString("webViewLink").takeIf { it.isNotBlank() },
                    pageCountHint = props?.optString("pageCount")?.toIntOrNull() ?: 0,
                    ocrEnabledHint = props?.optString("ocrEnabled")?.toBooleanStrictOrNull() ?: false,
                    isPasswordProtectedHint = props?.optString("isPasswordProtected")?.toBooleanStrictOrNull() ?: false,
                    securitySummaryHint = props?.optString("securitySummary")?.takeIf { it.isNotBlank() }
                )
            )
        }
        return result
    }

    private fun downloadGoogleDrivePdfInternal(
        accessToken: String,
        driveFileId: String,
        destinationFile: File
    ) {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (resp.code == 401) throw DriveUnauthorizedException()
            if (!resp.isSuccessful) {
                error("Google Drive download failed (${resp.code}): ${resp.body?.string().orEmpty()}")
            }
            val bodyStream = resp.body?.byteStream() ?: error("Empty download stream from Google Drive.")
            destinationFile.parentFile?.mkdirs()
            destinationFile.outputStream().use { out ->
                bodyStream.copyTo(out)
            }
        }
    }

    private fun deleteGoogleDrivePdfInternal(accessToken: String, driveFileId: String) {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$driveFileId")
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (resp.code == 401) throw DriveUnauthorizedException()
            if (!resp.isSuccessful && resp.code != 404) {
                error("Google Drive delete failed (${resp.code}): ${resp.body?.string().orEmpty()}")
            }
        }
    }

    private fun findExistingDrivePdfId(
        accessToken: String,
        folderId: String?,
        fileName: String
    ): String? {
        return try {
            val escapedName = fileName.replace("'", "\\'")
            val qStr = if (folderId != null) {
                "name='$escapedName' and '$folderId' in parents and trashed=false"
            } else {
                "name='$escapedName' and trashed=false"
            }
            val q = URLEncoder.encode(qStr, "UTF-8")
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val files = JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
                if (files != null && files.length() > 0) {
                    files.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findGoogleDriveFolderIdOnly(accessToken: String): String? {
        val q = URLEncoder.encode(
            "name='FolioPDF' and mimeType='application/vnd.google-apps.folder' and trashed=false",
            "UTF-8"
        )
        val findReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name)")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        httpClient.newCall(findReq).execute().use { resp ->
            if (resp.code == 401) throw DriveUnauthorizedException()
            if (resp.isSuccessful) {
                val files = JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
                }
            }
        }
        return null
    }

    private fun findOrCreateGoogleDriveFolder(accessToken: String): String? {
        return try {
            findGoogleDriveFolderIdOnly(accessToken)?.let { return it }
            val createMeta = JSONObject().apply {
                put("name", "FolioPDF")
                put("mimeType", "application/vnd.google-apps.folder")
            }.toString()
            val createReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?fields=id")
                .header("Authorization", "Bearer $accessToken")
                .post(createMeta.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(createReq).execute().use { resp ->
                if (resp.code == 401) throw DriveUnauthorizedException()
                if (resp.isSuccessful) {
                    JSONObject(resp.body?.string().orEmpty()).optString("id").takeIf { it.isNotBlank() }
                } else null
            }
        } catch (e: DriveUnauthorizedException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun parseIso8601Utc(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(raw)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }

    private class DriveUnauthorizedException : Exception("Google Drive session expired. Please re-authenticate.")

    private fun uploadToDropbox(
        accessToken: String,
        file: File,
        fileName: String
    ): CloudUploadRecord {
        val dropboxArg = JSONObject().apply {
            put("path", "/FolioPDF/$fileName")
            put("mode", "add")
            put("autorename", true)
            put("mute", false)
        }.toString()

        val request = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/upload")
            .header("Authorization", "Bearer $accessToken")
            .header("Dropbox-API-Arg", dropboxArg)
            .post(file.asRequestBody("application/octet-stream".toMediaType()))
            .build()

        val respJson = httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Dropbox upload failed (${resp.code}): $body")
            JSONObject(body)
        }

        return CloudUploadRecord(
            provider = CloudProvider.DROPBOX,
            fileName = fileName,
            remotePathOrLink = respJson.optString("path_display").ifBlank { "/FolioPDF/$fileName" },
            uploadedAtMs = System.currentTimeMillis(),
            success = true,
            message = "Saved to Dropbox /FolioPDF"
        )
    }

    private fun uploadToOneDrive(
        accessToken: String,
        file: File,
        fileName: String
    ): CloudUploadRecord {
        val encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        val request = Request.Builder()
            .url("https://graph.microsoft.com/v1.0/me/drive/root:/FolioPDF/$encodedName:/content")
            .header("Authorization", "Bearer $accessToken")
            .put(file.asRequestBody("application/pdf".toMediaType()))
            .build()

        val respJson = httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("OneDrive upload failed (${resp.code}): $body")
            JSONObject(body)
        }

        return CloudUploadRecord(
            provider = CloudProvider.ONEDRIVE,
            fileName = fileName,
            remotePathOrLink = respJson.optString("webUrl").ifBlank { "Files/FolioPDF/$fileName" },
            uploadedAtMs = System.currentTimeMillis(),
            success = true,
            message = "Saved to OneDrive /FolioPDF"
        )
    }

    private fun fetchGoogleUserInfo(accessToken: String): Pair<String?, String?> {
        return try {
            val req = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null to "Google User"
                val json = JSONObject(resp.body?.string().orEmpty())
                val email = json.optString("email").takeIf { it.isNotBlank() }
                val name = json.optString("name").takeIf { it.isNotBlank() }
                email to name
            }
        } catch (_: Exception) {
            null to "Google Account"
        }
    }

    private fun fetchDropboxAccount(accessToken: String): Pair<String?, String?> {
        return try {
            val req = Request.Builder()
                .url("https://api.dropboxapi.com/2/users/get_current_account")
                .header("Authorization", "Bearer $accessToken")
                .post("null".toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null to "Dropbox User"
                val json = JSONObject(resp.body?.string().orEmpty())
                val email = json.optString("email").takeIf { it.isNotBlank() }
                val nameObj = json.optJSONObject("name")
                val name = nameObj?.optString("display_name")?.takeIf { it.isNotBlank() }
                email to name
            }
        } catch (_: Exception) {
            null to "Dropbox Account"
        }
    }

    private fun fetchOneDriveAccount(accessToken: String): Pair<String?, String?> {
        return try {
            val req = Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null to "OneDrive User"
                val json = JSONObject(resp.body?.string().orEmpty())
                val email = json.optString("mail")
                    .ifBlank { json.optString("userPrincipalName") }
                    .takeIf { it.isNotBlank() }
                val name = json.optString("displayName").takeIf { it.isNotBlank() }
                email to name
            }
        } catch (_: Exception) {
            null to "Microsoft Account"
        }
    }
}
