package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.ZipFilenameFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.axionDataStore: DataStore<Preferences> by preferencesDataStore(name = "axion_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SAF_TREE_URI = stringPreferencesKey("saf_tree_uri")
        val SAF_FOLDER_NAME = stringPreferencesKey("saf_folder_name")
        val ZIP_FORMAT = stringPreferencesKey("zip_filename_format")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val settingsFlow: Flow<AppSettings> = context.axionDataStore.data.map { prefs ->
        AppSettings(
            isDarkTheme = prefs[Keys.DARK_THEME] ?: true,
            safTreeUri = prefs[Keys.SAF_TREE_URI],
            safFolderDisplayName = prefs[Keys.SAF_FOLDER_NAME],
            zipFilenameFormat = ZipFilenameFormat.fromId(prefs[Keys.ZIP_FORMAT]),
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
        )
    }

    suspend fun getCurrentSettings(): AppSettings = settingsFlow.first()

    suspend fun setDarkTheme(enabled: Boolean) {
        context.axionDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setSafFolder(treeUri: String?, displayName: String?) {
        context.axionDataStore.edit { prefs ->
            if (treeUri == null) {
                prefs.remove(Keys.SAF_TREE_URI)
                prefs.remove(Keys.SAF_FOLDER_NAME)
            } else {
                prefs[Keys.SAF_TREE_URI] = treeUri
                if (displayName != null) {
                    prefs[Keys.SAF_FOLDER_NAME] = displayName
                }
            }
        }
    }

    suspend fun setZipFilenameFormat(format: ZipFilenameFormat) {
        context.axionDataStore.edit { it[Keys.ZIP_FORMAT] = format.id }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.axionDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }
}
