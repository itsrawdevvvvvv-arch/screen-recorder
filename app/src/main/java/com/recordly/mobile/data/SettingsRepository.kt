package com.recordly.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.recordly.mobile.model.CanvasStyleConfig
import com.recordly.mobile.model.GroqSettings
import com.recordly.mobile.model.KeyboardTrimMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "recordly_settings")

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val WHISPER_MODEL = stringPreferencesKey("whisper_model")
        val CHAT_MODEL = stringPreferencesKey("chat_model")
        val VISION_MODEL = stringPreferencesKey("vision_model")
        val KEYBOARD_TRIM_MODE = stringPreferencesKey("keyboard_trim_mode")
        val AUTO_ZOOM_ENABLED = booleanPreferencesKey("auto_zoom_enabled")
        val AUTO_AI_SYNC = booleanPreferencesKey("auto_ai_sync_on_stop")
        val MOUSE_SENSITIVITY = floatPreferencesKey("mouse_sensitivity")
        val RECORD_INTERNAL_AUDIO = booleanPreferencesKey("record_internal_audio")
        val RECORD_MIC = booleanPreferencesKey("record_microphone")
        val CANVAS_STYLE_JSON = stringPreferencesKey("canvas_style_json")
    }

    val groqSettingsFlow: Flow<GroqSettings> = context.dataStore.data.map { prefs ->
        val mode = prefs[Keys.KEYBOARD_TRIM_MODE]?.let {
            runCatching { KeyboardTrimMode.valueOf(it) }.getOrNull()
        } ?: KeyboardTrimMode.REMOVE

        GroqSettings(
            apiKey = prefs[Keys.GROQ_API_KEY].orEmpty(),
            whisperModel = prefs[Keys.WHISPER_MODEL] ?: "whisper-large-v3-turbo",
            chatModel = prefs[Keys.CHAT_MODEL] ?: "llama-3.3-70b-versatile",
            visionModel = prefs[Keys.VISION_MODEL] ?: "qwen/qwen3.8-27b",
            keyboardTrimMode = mode,
            autoZoomEnabled = prefs[Keys.AUTO_ZOOM_ENABLED] ?: true,
            autoAiSyncOnStop = prefs[Keys.AUTO_AI_SYNC] ?: false,
            mouseSensitivity = prefs[Keys.MOUSE_SENSITIVITY] ?: 1.0f,
            recordInternalAudio = prefs[Keys.RECORD_INTERNAL_AUDIO] ?: true,
            recordMicrophone = prefs[Keys.RECORD_MIC] ?: true
        )
    }

    val canvasStyleFlow: Flow<CanvasStyleConfig> = context.dataStore.data.map { prefs ->
        prefs[Keys.CANVAS_STYLE_JSON]?.let { raw ->
            runCatching { json.decodeFromString<CanvasStyleConfig>(raw) }.getOrNull()
        } ?: CanvasStyleConfig()
    }

    suspend fun getGroqSettings(): GroqSettings = groqSettingsFlow.first()

    suspend fun getCanvasStyle(): CanvasStyleConfig = canvasStyleFlow.first()

    suspend fun updateGroqSettings(settings: GroqSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GROQ_API_KEY] = settings.apiKey.trim()
            prefs[Keys.WHISPER_MODEL] = settings.whisperModel.trim().ifEmpty { "whisper-large-v3-turbo" }
            prefs[Keys.CHAT_MODEL] = settings.chatModel.trim().ifEmpty { "llama-3.3-70b-versatile" }
            prefs[Keys.VISION_MODEL] = settings.visionModel.trim().ifEmpty { "qwen/qwen3.8-27b" }
            prefs[Keys.KEYBOARD_TRIM_MODE] = settings.keyboardTrimMode.name
            prefs[Keys.AUTO_ZOOM_ENABLED] = settings.autoZoomEnabled
            prefs[Keys.AUTO_AI_SYNC] = settings.autoAiSyncOnStop
            prefs[Keys.MOUSE_SENSITIVITY] = settings.mouseSensitivity.coerceIn(0.3f, 3.0f)
            prefs[Keys.RECORD_INTERNAL_AUDIO] = settings.recordInternalAudio
            prefs[Keys.RECORD_MIC] = settings.recordMicrophone
        }
    }

    suspend fun updateCanvasStyle(style: CanvasStyleConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CANVAS_STYLE_JSON] = json.encodeToString(style)
        }
    }
}
