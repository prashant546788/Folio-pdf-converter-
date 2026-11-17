package com.example.data.cloud

import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.model.CloudProvider
import java.security.MessageDigest
import java.security.SecureRandom

object CloudAuthConfig {
    const val GOOGLE_DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val GOOGLE_EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email"
    const val GOOGLE_PROFILE_SCOPE = "https://www.googleapis.com/auth/userinfo.profile"

    const val REDIRECT_SCHEME = "foliopdf"
    const val REDIRECT_HOST = "oauth"

    fun redirectUriFor(provider: CloudProvider): String = when (provider) {
        CloudProvider.GOOGLE_DRIVE -> "$REDIRECT_SCHEME://$REDIRECT_HOST/google"
        CloudProvider.DROPBOX -> "$REDIRECT_SCHEME://$REDIRECT_HOST/dropbox"
        CloudProvider.ONEDRIVE -> "$REDIRECT_SCHEME://$REDIRECT_HOST/onedrive"
    }

    fun isProviderConfigured(provider: CloudProvider): Boolean = when (provider) {
        CloudProvider.GOOGLE_DRIVE -> BuildConfig.GOOGLE_OAUTH_CLIENT_ID.isNotBlank()
        CloudProvider.DROPBOX ->
            BuildConfig.DROPBOX_APP_KEY.isNotBlank() &&
                BuildConfig.DROPBOX_APP_KEY != "YOUR_DROPBOX_APP_KEY"
        CloudProvider.ONEDRIVE ->
            BuildConfig.ONEDRIVE_CLIENT_ID.isNotBlank() &&
                BuildConfig.ONEDRIVE_CLIENT_ID != "YOUR_ONEDRIVE_CLIENT_ID"
    }

    fun configurationHint(provider: CloudProvider): String? = when (provider) {
        CloudProvider.GOOGLE_DRIVE ->
            if (isProviderConfigured(provider)) null
            else "Google Drive OAuth Client ID is not configured."
        CloudProvider.DROPBOX ->
            if (isProviderConfigured(provider)) null
            else "Set DROPBOX_APP_KEY in AI Studio Secrets with redirect URI foliopdf://oauth/dropbox."
        CloudProvider.ONEDRIVE ->
            if (isProviderConfigured(provider)) null
            else "Set ONEDRIVE_CLIENT_ID in AI Studio Secrets with redirect URI foliopdf://oauth/onedrive."
    }

    fun generatePkcePair(): Pair<String, String> {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val verifier = Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return verifier to challenge
    }

    fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun buildAuthorizeUrl(
        provider: CloudProvider,
        codeChallenge: String,
        state: String
    ): Uri {
        val redirect = redirectUriFor(provider)
        return when (provider) {
            CloudProvider.DROPBOX -> Uri.parse("https://www.dropbox.com/oauth2/authorize").buildUpon()
                .appendQueryParameter("client_id", BuildConfig.DROPBOX_APP_KEY)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", redirect)
                .appendQueryParameter("code_challenge", codeChallenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("token_access_type", "offline")
                .appendQueryParameter("state", state)
                .build()

            CloudProvider.ONEDRIVE ->
                Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/authorize").buildUpon()
                    .appendQueryParameter("client_id", BuildConfig.ONEDRIVE_CLIENT_ID)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("redirect_uri", redirect)
                    .appendQueryParameter("scope", "Files.ReadWrite User.Read offline_access")
                    .appendQueryParameter("code_challenge", codeChallenge)
                    .appendQueryParameter("code_challenge_method", "S256")
                    .appendQueryParameter("state", state)
                    .build()

            CloudProvider.GOOGLE_DRIVE ->
                Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
                    .appendQueryParameter("client_id", BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("redirect_uri", redirect)
                    .appendQueryParameter("scope", "$GOOGLE_DRIVE_SCOPE $GOOGLE_EMAIL_SCOPE $GOOGLE_PROFILE_SCOPE")
                    .appendQueryParameter("code_challenge", codeChallenge)
                    .appendQueryParameter("code_challenge_method", "S256")
                    .appendQueryParameter("access_type", "offline")
                    .appendQueryParameter("state", state)
                    .build()
        }
    }
}
