package com.recordly.mobile.capture

import android.os.SystemClock
import com.recordly.mobile.model.MouseSample
import com.recordly.mobile.model.TimeInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

object ActiveSessionTracker {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isKeyboardCurrentlyVisible = MutableStateFlow(false)
    val isKeyboardCurrentlyVisible: StateFlow<Boolean> = _isKeyboardCurrentlyVisible.asStateFlow()

    private val _latestCursor = MutableStateFlow(0.5f to 0.5f)
    val latestCursor: StateFlow<Pair<Float, Float>> = _latestCursor.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _lastSavedProjectId = MutableStateFlow<String?>(null)
    val lastSavedProjectId: StateFlow<String?> = _lastSavedProjectId.asStateFlow()

    private var startRealtimeMs = 0L
    private var pauseStartedMs = 0L
    private var totalPausedMs = 0L
    private var activeKeyboardStartMs: Long? = null

    private val mouseBuffer = Collections.synchronizedList(mutableListOf<MouseSample>())
    private val keyboardBuffer = Collections.synchronizedList(mutableListOf<TimeInterval>())

    fun beginSession() {
        mouseBuffer.clear()
        keyboardBuffer.clear()
        startRealtimeMs = SystemClock.elapsedRealtime()
        pauseStartedMs = 0L
        totalPausedMs = 0L
        activeKeyboardStartMs = null
        _isPaused.value = false
        _isKeyboardCurrentlyVisible.value = false
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

    fun recordMouseSample(xNorm: Float, yNorm: Float, isDown: Boolean, isUp: Boolean, button: Int) {
        if (!_isRecording.value || _isPaused.value) return
        val clampedX = xNorm.coerceIn(0f, 1f)
        val clampedY = yNorm.coerceIn(0f, 1f)
        _latestCursor.value = clampedX to clampedY

        val nowMs = currentRecordingTimeMs()
        val last = synchronized(mouseBuffer) { mouseBuffer.lastOrNull() }
        val movedEnough = last == null ||
            kotlin.math.abs(last.xNorm - clampedX) > 0.004f ||
            kotlin.math.abs(last.yNorm - clampedY) > 0.004f ||
            (nowMs - last.timestampMs) >= 33L

        if (isDown || isUp || movedEnough) {
            mouseBuffer.add(
                MouseSample(
                    timestampMs = nowMs,
                    xNorm = clampedX,
                    yNorm = clampedY,
                    isDown = isDown,
                    isUp = isUp,
                    button = button
                )
            )
        }
    }

    fun onKeyboardVisibilityChanged(visible: Boolean) {
        _isKeyboardCurrentlyVisible.value = visible
        if (!_isRecording.value || _isPaused.value) return
        val nowMs = currentRecordingTimeMs()
        if (visible) {
            if (activeKeyboardStartMs == null) {
                activeKeyboardStartMs = nowMs
            }
        } else {
            val start = activeKeyboardStartMs
            if (start != null) {
                activeKeyboardStartMs = null
                if (nowMs - start >= 250L) {
                    keyboardBuffer.add(TimeInterval(startMs = start, endMs = nowMs, label = "Phone Keyboard", trim = true))
                }
            }
        }
    }

    fun endSession(): Triple<Long, List<MouseSample>, List<TimeInterval>> {
        val finalDuration = currentRecordingTimeMs()
        activeKeyboardStartMs?.let { start ->
            if (finalDuration - start >= 250L) {
                keyboardBuffer.add(TimeInterval(startMs = start, endMs = finalDuration, label = "Phone Keyboard", trim = true))
            }
        }
        activeKeyboardStartMs = null
        _isRecording.value = false
        _isPaused.value = false
        _isKeyboardCurrentlyVisible.value = false

        val mice = synchronized(mouseBuffer) { mouseBuffer.toList() }
        val keyboards = synchronized(keyboardBuffer) { keyboardBuffer.toList() }
        return Triple(finalDuration, mice, keyboards)
    }

    fun markSavedProject(projectId: String) {
        _lastSavedProjectId.value = projectId
    }

    fun consumeLastSavedProject() {
        _lastSavedProjectId.value = null
    }
}
