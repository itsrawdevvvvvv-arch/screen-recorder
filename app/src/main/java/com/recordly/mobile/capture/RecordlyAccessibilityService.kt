package com.recordly.mobile.capture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.recordly.mobile.model.AccessibilityClick

object ClickLog {
    private val lock = Any()
    private val clicks = ArrayList<AccessibilityClick>()
    @Volatile private var startUptimeMs = -1L

    val isRecording: Boolean get() = startUptimeMs >= 0

    /** Call when the MediaProjection capture starts. */
    fun begin() {
        synchronized(lock) { clicks.clear() }
        startUptimeMs = SystemClock.uptimeMillis()
    }

    fun add(eventUptimeMs: Long, x: Float?, y: Float?) {
        val start = startUptimeMs
        if (start < 0) return
        val t = eventUptimeMs - start
        if (t < 0) return
        synchronized(lock) { clicks += AccessibilityClick(t, x, y) }
    }

    /** Call when recording stops. */
    fun end(): List<AccessibilityClick> {
        startUptimeMs = -1L
        return synchronized(lock) { clicks.toList() }
    }
}

class RecordlyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        checkKeyboardWindow()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                checkKeyboardWindow()
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                if (!ClickLog.isRecording) return
                var nx: Float? = null
                var ny: Float? = null
                val source = event.source
                if (source != null) {
                    val r = Rect()
                    source.getBoundsInScreen(r)
                    val (sw, sh) = screenSize()
                    if (!r.isEmpty && sw > 0 && sh > 0 &&
                        r.width().toLong() * r.height() < 0.4 * sw.toLong() * sh
                    ) {
                        nx = (r.exactCenterX() / sw).coerceIn(0f, 1f)
                        ny = (r.exactCenterY() / sh).coerceIn(0f, 1f)
                    }
                    source.recycle()
                }
                ClickLog.add(event.eventTime, nx, ny)
            }
        }
    }

    override fun onInterrupt() {
        ActiveSessionTracker.onKeyboardVisibilityChanged(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    private fun checkKeyboardWindow() {
        val imeVisible = runCatching {
            windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }.getOrDefault(false)
        ActiveSessionTracker.onKeyboardVisibilityChanged(imeVisible)
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    companion object {
        @Volatile
        var instance: RecordlyAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            val expected = "${context.packageName}/${RecordlyAccessibilityService::class.java.name}"
            while (colonSplitter.hasNext()) {
                if (colonSplitter.next().equals(expected, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}
