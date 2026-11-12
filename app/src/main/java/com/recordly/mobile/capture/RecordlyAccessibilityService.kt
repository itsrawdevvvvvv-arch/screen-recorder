package com.recordly.mobile.capture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

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
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Optional fallback when Shizuku isn't tracking raw mouse coordinates
                if (ShizukuMouseBridge.status.value != ShizukuStatus.TRACKING) {
                    val src = event.source ?: return
                    val rect = Rect()
                    src.getBoundsInScreen(rect)
                    val dm = resources.displayMetrics
                    if (!rect.isEmpty && dm.widthPixels > 0 && dm.heightPixels > 0) {
                        val cx = rect.centerX().toFloat() / dm.widthPixels.toFloat()
                        val cy = rect.centerY().toFloat() / dm.heightPixels.toFloat()
                        ActiveSessionTracker.recordMouseSample(cx, cy, isDown = true, isUp = true, button = 1)
                    }
                    src.recycle()
                }
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
