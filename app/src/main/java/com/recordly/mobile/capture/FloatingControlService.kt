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
            setPadding(22, 12, 12, 12)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(Color.parseColor("#F217191D")) // Dark Card
                setStroke(2, Color.parseColor("#40DCC9A9")) // Warm Sand border
            }
            elevation = 18f
        }

        val dot = View(this).apply {
            val size = 20
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = 14
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#B83A2D")) // Terracotta
            }
        }
        statusDot = dot

        val timer = TextView(this).apply {
            text = "00:00"
            textSize = 13.5f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#F8FAFC"))
            setPadding(0, 0, 16, 0)
        }
        timerText = timer

        val pause = TextView(this).apply {
            text = "Pause"
            textSize = 11.5f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.parseColor("#DCC9A9")) // Warm sand
            setPadding(20, 10, 20, 10)
            background = GradientDrawable().apply {
                cornerRadius = 60f
                setColor(Color.parseColor("#22262E"))
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
            textSize = 12f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(22, 10, 22, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 10
            }
            background = GradientDrawable().apply {
                cornerRadius = 60f
                setColor(Color.parseColor("#B83A2D")) // Terracotta
            }
            setOnClickListener {
                startService(
                    Intent(this@FloatingControlService, ScreenRecordService::class.java)
                        .setAction(ScreenRecordService.ACTION_STOP)
                )
                stopSelf()
            }
        }

        pill.addView(dot)
        pill.addView(timer)
        pill.addView(pause)
        pill.addView(stop)
        wrapper.addView(pill)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 180
        }

        pill.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > 10.0) {
                            isDragging = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager?.updateViewLayout(wrapper, params)
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(wrapper, params)
        rootWrapper = wrapper

        observeSessionState()
    }

    private fun observeSessionState() {
        scope.launch {
            combine(
                ActiveSessionTracker.isRecording,
                ActiveSessionTracker.isPaused,
                ActiveSessionTracker.elapsedMs
            ) { rec, paused, elapsed ->
                Triple(rec, paused, elapsed)
            }.collect { (rec, paused, elapsed) ->
                val secs = (elapsed / 1000L).toInt()
                timerText?.text = String.format(Locale.US, "%02d:%02d", secs / 60, secs % 60)
                pauseBtn?.text = if (paused) "▶ Resume" else "❚❚ Pause"
                val dotColor = when {
                    paused -> Color.parseColor("#9DA3AE")
                    rec -> Color.parseColor("#B83A2D") // Terracotta
                    else -> Color.parseColor("#4E6851") // Forest Sage
                }
                (statusDot?.background as? GradientDrawable)?.setColor(dotColor)
            }
        }
    }

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
            .setContentText("Floating recording controls active")
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
