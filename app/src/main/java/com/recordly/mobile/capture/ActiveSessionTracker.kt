package com.recordly.mobile.capture

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveSessionTracker {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _lastSavedProjectId = MutableStateFlow<String?>(null)
    val lastSavedProjectId: StateFlow<String?> = _lastSavedProjectId.asStateFlow()

    private var startRealtimeMs = 0L
    private var pauseStartedMs = 0L
    private var totalPausedMs = 0L

    fun beginSession() {
        startRealtimeMs = SystemClock.elapsedRealtime()
        pauseStartedMs = 0L
        totalPausedMs = 0L
        _isPaused.value = false
        _elapsedMs.value = 0L
        _isRecording.value = true
    }

    fun pauseSession() {
        if (!_isRecording.value || _isPaused.value) return
        pauseStartedMs = SystemClock.elapsedRealtime()
        _isPaused.value = true
    }

    fun resumeSession() {
        if (!_isRecording.value || !_isPaused.value) return
        totalPausedMs += (SystemClock.elapsedRealtime() - pauseStartedMs).coerceAtLeast(0L)
        pauseStartedMs = 0L
        _isPaused.value = false
    }

    fun currentRecordingTimeMs(): Long {
        if (!_isRecording.value) return 0L
        val now = if (_isPaused.value && pauseStartedMs > 0L) pauseStartedMs else SystemClock.elapsedRealtime()
        return (now - startRealtimeMs - totalPausedMs).coerceAtLeast(0L)
    }

    fun updateElapsedTicker() {
        if (_isRecording.value) {
            _elapsedMs.value = currentRecordingTimeMs()
        }
    }

    fun endSession(): Long {
        val finalDuration = currentRecordingTimeMs()
        _isRecording.value = false
        _isPaused.value = false
        return finalDuration
    }

    fun markSavedProject(projectId: String) {
        _lastSavedProjectId.value = projectId
    }

    fun consumeLastSavedProject() {
        _lastSavedProjectId.value = null
    }
}
