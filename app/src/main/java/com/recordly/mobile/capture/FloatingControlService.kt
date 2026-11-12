package com.recordly.mobile.capture

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.recordly.mobile.MainActivity
import com.recordly.mobile.R
import com.recordly.mobile.RecordlyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class FloatingControlService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var rootWrapper: LinearLayout? = null
    private var timerText: TextView? = null
    private var pauseBtn: TextView? = null
    private var statusDot: View? = null
    private var keyboardTipPill: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        if (Settings.canDrawOverlays(this)) {
            attachFloatingBubble()
        } else {
            stopSelf()
        }
    }

    private fun attachFloatingBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 12, 12, 12)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(Color.parseColor("#E6141824"))
                setStroke(2, Color.parseColor("#6C5CE7"))
            }
            elevation = 20f
        }

        val dot = View(this).apply {
            val size = 24
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = 16
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF4757"))
            }
        }
        statusDot = dot

        val timer = TextView(this).apply {
            text = "00:00"
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#F1F5F9"))
            setPadding(0, 0, 18, 0)
        }
        timerText = timer

        val pause = TextView(this).apply {
            text = "Pause"
            textSize = 12f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(22, 12, 22, 12)
            background = GradientDrawable().apply {
                cornerRadius = 60f
                setColor(Color.parseColor("#1AFFFFFF"))
            }
            setOnClickListener {
                val action = if (ActiveSessionTracker.isPaused.value) {
                    ScreenRecordService.ACTION_RESUME
                } else {
                    ScreenRecordService.ACTION_PAUSE
                }
                startService(Intent(this@FloatingControlService, ScreenRecordService::class.java).setAction(action))
            }
        }
        pauseBtn = pause

        val stop = TextView(this).apply {
            text = "■ Stop"
            textSize = 12.5f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(24, 12, 24, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 12
            }
            background = GradientDrawable().apply {
                cornerRadius = 60f
                setColor(Color.parseColor("#FF4757"))
            }
            setOnClickListener {
                startService(
                    Intent(this@FloatingControlService, ScreenRecordService::class.java)
                        .setAction(ScreenRecordService.ACTION_STOP)
                )
                stopSelf()
            }
        }

        val kbTip = TextView(this).apply {
            text = "⌨ Keyboard detected · marked for auto-trim"
            textSize = 11f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFA502"))
            setPadding(20, 10, 20, 10)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10
            }
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#F0141824"))
                setStroke(2, Color.parseColor("#8CFFa502"))
            }
        }
        keyboardTipPill = kbTip

        pill.addView(dot)
        pill.addView(timer)
        pill.addView(pause)
        pill.addView(stop)
        wrapper.addView(pill)
        wrapper.addView(kbTip)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 140
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        pill.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    runCatching { windowManager?.updateViewLayout(wrapper, layoutParams) }
                    true
                }
                else -> false
            }
        }

        rootWrapper = wrapper
        windowManager?.addView(wrapper, layoutParams)

        scope.launch {
            combine(
                ActiveSessionTracker.isRecording,
                ActiveSessionTracker.isPaused,
                ActiveSessionTracker.elapsedMs,
                ActiveSessionTracker.isKeyboardCurrentlyVisible
            ) { recording, paused, elapsed, kbVisible ->
                QuadState(recording, paused, elapsed, kbVisible)
            }.collect { st ->
                val secs = (st.elapsed / 1000L).toInt()
                timerText?.text = String.format(Locale.US, "%02d:%02d", secs / 60, secs % 60)
                pauseBtn?.text = if (st.paused) "▶ Resume" else "❚❚ Pause"
                pauseBtn?.visibility = if (st.recording) View.VISIBLE else View.GONE
                keyboardTipPill?.visibility = if (st.recording && st.kbVisible) View.VISIBLE else View.GONE
                val dotColor = when {
                    st.kbVisible -> Color.parseColor("#FFA502")
                    st.paused -> Color.parseColor("#6B7280")
                    st.recording -> Color.parseColor("#FF4757")
                    else -> Color.parseColor("#00D2A0")
                }
                (statusDot?.background as? GradientDrawable)?.setColor(dotColor)
            }
        }
    }

    private data class QuadState(
        val recording: Boolean,
        val paused: Boolean,
        val elapsed: Long,
        val kbVisible: Boolean
    )

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, RecordlyApp.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Recordly Floating Studio")
            .setContentText("Floating recorder overlay active")
            .setContentIntent(intent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        rootWrapper?.let { runCatching { windowManager?.removeView(it) } }
        rootWrapper = null
        scope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 2002
    }
}
