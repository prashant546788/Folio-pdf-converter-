package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CloudProvider
import com.example.model.ConversionSettings
import com.example.model.MarginOption
import com.example.model.PageOrientation
import com.example.model.PageSizeOption
import com.example.model.PdfFontFamily
import com.example.model.PdfFontSizePreset
import com.example.model.PdfLineSpacingPreset
import com.example.model.PdfPageTheme
import com.example.model.PdfTextAlignment
import com.example.model.PdfTextColorPreset
import com.example.model.QualityPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.folioDataStore: DataStore<Preferences> by preferencesDataStore(name = "folio_prefs")

data class StoredCloudToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtMs: Long = 0L,
    val email: String? = null,
    val displayName: String? = null
)

class CloudTokenStore(private val context: Context) {

    private fun accessTokenKey(provider: CloudProvider) =
        stringPreferencesKey("cloud_${provider.name}_access_token")

    private fun refreshTokenKey(provider: CloudProvider) =
        stringPreferencesKey("cloud_${provider.name}_refresh_token")

    private fun expiresAtKey(provider: CloudProvider) =
        longPreferencesKey("cloud_${provider.name}_expires_at")

    private fun emailKey(provider: CloudProvider) =
        stringPreferencesKey("cloud_${provider.name}_email")

    private fun displayNameKey(provider: CloudProvider) =
        stringPreferencesKey("cloud_${provider.name}_name")

    private fun pkceVerifierKey(provider: CloudProvider) =
        stringPreferencesKey("cloud_${provider.name}_pkce_verifier")

    private fun pkceStateKey(provider: CloudProvider) =
        stringPreferencesKey("cloud_${provider.name}_pkce_state")

    private val pageSizeKey = stringPreferencesKey("settings_page_size")
    private val orientationKey = stringPreferencesKey("settings_orientation")
    private val marginKey = stringPreferencesKey("settings_margin")
    private val qualityKey = stringPreferencesKey("settings_quality")
    private val ocrEnabledKey = booleanPreferencesKey("settings_ocr_enabled")
    private val pageNumbersKey = booleanPreferencesKey("settings_page_numbers")
    private val fontFamilyKey = stringPreferencesKey("settings_font_family")
    private val fontSizeKey = stringPreferencesKey("settings_font_size")
    private val lineSpacingKey = stringPreferencesKey("settings_line_spacing")
    private val textAlignmentKey = stringPreferencesKey("settings_text_alignment")
    private val textColorKey = stringPreferencesKey("settings_text_color")
    private val pageThemeKey = stringPreferencesKey("settings_page_theme")
    private val includeHeaderTitleKey = booleanPreferencesKey("settings_include_header_title")
    private val showWatermarkKey = booleanPreferencesKey("settings_show_watermark")
    private val watermarkTextKey = stringPreferencesKey("settings_watermark_text")
    private val autoSyncDriveKey = booleanPreferencesKey("cloud_drive_auto_sync")
    private val lastDriveSyncAtKey = longPreferencesKey("cloud_drive_last_sync_at")

    val autoSyncDriveFlow: Flow<Boolean> = context.folioDataStore.data.map { prefs ->
        prefs[autoSyncDriveKey] ?: false
    }

    val lastDriveSyncAtFlow: Flow<Long> = context.folioDataStore.data.map { prefs ->
        prefs[lastDriveSyncAtKey] ?: 0L
    }

    suspend fun setAutoSyncDrive(enabled: Boolean) {
        context.folioDataStore.edit { prefs ->
            prefs[autoSyncDriveKey] = enabled
        }
    }

    suspend fun setLastDriveSyncAt(timestampMs: Long) {
        context.folioDataStore.edit { prefs ->
            prefs[lastDriveSyncAtKey] = timestampMs
        }
    }

    val settingsFlow: Flow<ConversionSettings> = context.folioDataStore.data.map { prefs ->
        ConversionSettings(
            pageSize = prefs[pageSizeKey]?.let { name ->
                PageSizeOption.entries.firstOrNull { it.name == name }
            } ?: PageSizeOption.A4,
            orientation = prefs[orientationKey]?.let { name ->
                PageOrientation.entries.firstOrNull { it.name == name }
            } ?: PageOrientation.AUTO,
            margin = prefs[marginKey]?.let { name ->
                MarginOption.entries.firstOrNull { it.name == name }
            } ?: MarginOption.NARROW,
            quality = prefs[qualityKey]?.let { name ->
                QualityPreset.entries.firstOrNull { it.name == name }
            } ?: QualityPreset.HIGH,
            enableOcr = prefs[ocrEnabledKey] ?: true,
            addPageNumbers = prefs[pageNumbersKey] ?: true,
            fontFamily = prefs[fontFamilyKey]?.let { name ->
                PdfFontFamily.entries.firstOrNull { it.name == name }
            } ?: PdfFontFamily.CLASSIC_SANS,
            fontSize = prefs[fontSizeKey]?.let { name ->
                PdfFontSizePreset.entries.firstOrNull { it.name == name }
            } ?: PdfFontSizePreset.STANDARD,
            lineSpacing = prefs[lineSpacingKey]?.let { name ->
                PdfLineSpacingPreset.entries.firstOrNull { it.name == name }
            } ?: PdfLineSpacingPreset.BALANCED,
            textAlignment = prefs[textAlignmentKey]?.let { name ->
                PdfTextAlignment.entries.firstOrNull { it.name == name }
            } ?: PdfTextAlignment.LEFT,
            textColor = prefs[textColorKey]?.let { name ->
                PdfTextColorPreset.entries.firstOrNull { it.name == name }
            } ?: PdfTextColorPreset.CHARCOAL,
            pageTheme = prefs[pageThemeKey]?.let { name ->
                PdfPageTheme.entries.firstOrNull { it.name == name }
            } ?: PdfPageTheme.PURE_WHITE,
            includeHeaderTitle = prefs[includeHeaderTitleKey] ?: true,
            showWatermark = prefs[showWatermarkKey] ?: false,
            watermarkText = prefs[watermarkTextKey] ?: ""
        )
    }

    suspend fun saveSettings(settings: ConversionSettings) {
        context.folioDataStore.edit { prefs ->
            prefs[pageSizeKey] = settings.pageSize.name
            prefs[orientationKey] = settings.orientation.name
            prefs[marginKey] = settings.margin.name
            prefs[qualityKey] = settings.quality.name
            prefs[ocrEnabledKey] = settings.enableOcr
            prefs[pageNumbersKey] = settings.addPageNumbers
            prefs[fontFamilyKey] = settings.fontFamily.name
            prefs[fontSizeKey] = settings.fontSize.name
            prefs[lineSpacingKey] = settings.lineSpacing.name
            prefs[textAlignmentKey] = settings.textAlignment.name
            prefs[textColorKey] = settings.textColor.name
            prefs[pageThemeKey] = settings.pageTheme.name
            prefs[includeHeaderTitleKey] = settings.includeHeaderTitle
            prefs[showWatermarkKey] = settings.showWatermark
            prefs[watermarkTextKey] = settings.watermarkText
        }
    }

    fun observeTokens(): Flow<Map<CloudProvider, StoredCloudToken?>> =
        context.folioDataStore.data.map { prefs ->
            CloudProvider.entries.associateWith { provider ->
                val access = prefs[accessTokenKey(provider)]
                if (access.isNullOrBlank()) {
                    null
                } else {
                    StoredCloudToken(
                        accessToken = access,
                        refreshToken = prefs[refreshTokenKey(provider)],
                        expiresAtMs = prefs[expiresAtKey(provider)] ?: 0L,
                        email = prefs[emailKey(provider)],
                        displayName = prefs[displayNameKey(provider)]
                    )
                }
            }
        }

    suspend fun getToken(provider: CloudProvider): StoredCloudToken? {
        val prefs = context.folioDataStore.data.first()
        val access = prefs[accessTokenKey(provider)] ?: return null
        if (access.isBlank()) return null
        return StoredCloudToken(
            accessToken = access,
            refreshToken = prefs[refreshTokenKey(provider)],
            expiresAtMs = prefs[expiresAtKey(provider)] ?: 0L,
            email = prefs[emailKey(provider)],
            displayName = prefs[displayNameKey(provider)]
        )
    }

    suspend fun saveToken(provider: CloudProvider, token: StoredCloudToken) {
        context.folioDataStore.edit { prefs ->
            prefs[accessTokenKey(provider)] = token.accessToken
            if (token.refreshToken != null) {
                prefs[refreshTokenKey(provider)] = token.refreshToken
            } else {
                prefs.remove(refreshTokenKey(provider))
            }
            prefs[expiresAtKey(provider)] = token.expiresAtMs
            if (token.email != null) {
                prefs[emailKey(provider)] = token.email
            } else {
                prefs.remove(emailKey(provider))
            }
            if (token.displayName != null) {
                prefs[displayNameKey(provider)] = token.displayName
            } else {
                prefs.remove(displayNameKey(provider))
            }
        }
    }

    suspend fun clearToken(provider: CloudProvider) {
        context.folioDataStore.edit { prefs ->
            prefs.remove(accessTokenKey(provider))
            prefs.remove(refreshTokenKey(provider))
            prefs.remove(expiresAtKey(provider))
            prefs.remove(emailKey(provider))
            prefs.remove(displayNameKey(provider))
        }
    }

    suspend fun savePendingPkce(provider: CloudProvider, verifier: String, state: String) {
        context.folioDataStore.edit { prefs ->
            prefs[pkceVerifierKey(provider)] = verifier
            prefs[pkceStateKey(provider)] = state
        }
    }

    suspend fun consumePendingPkce(provider: CloudProvider): Pair<String, String>? {
        val prefs = context.folioDataStore.data.first()
        val verifier = prefs[pkceVerifierKey(provider)]
        val state = prefs[pkceStateKey(provider)]
        context.folioDataStore.edit {
            it.remove(pkceVerifierKey(provider))
            it.remove(pkceStateKey(provider))
        }
        return if (!verifier.isNullOrBlank() && !state.isNullOrBlank()) {
            verifier to state
        } else {
            null
        }
    }
}
