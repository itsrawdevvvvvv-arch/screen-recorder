package com.recordly.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.recordly.mobile.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "recordly_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SHOW_FLOATING_MENU = booleanPreferencesKey("show_floating_menu")
        val CONTACT_SHEET_INTERVAL = floatPreferencesKey("contact_sheet_interval")
        val MAX_KEYFRAMES = intPreferencesKey("max_keyframes")
        val RECORD_MIC = booleanPreferencesKey("record_microphone")
    }

    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            showFloatingMenu = prefs[Keys.SHOW_FLOATING_MENU] ?: true,
            contactSheetIntervalSec = prefs[Keys.CONTACT_SHEET_INTERVAL] ?: 1.5f,
            maxKeyframes = prefs[Keys.MAX_KEYFRAMES] ?: 24,
            recordMicrophone = prefs[Keys.RECORD_MIC] ?: true
        )
    }

    suspend fun getAppSettings(): AppSettings = appSettingsFlow.first()

    suspend fun updateAppSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_FLOATING_MENU] = settings.showFloatingMenu
            prefs[Keys.CONTACT_SHEET_INTERVAL] = settings.contactSheetIntervalSec
            prefs[Keys.MAX_KEYFRAMES] = settings.maxKeyframes
            prefs[Keys.RECORD_MIC] = settings.recordMicrophone
        }
    }

    suspend fun toggleFloatingMenu() {
        val current = getAppSettings()
        updateAppSettings(current.copy(showFloatingMenu = !current.showFloatingMenu))
    }
}
