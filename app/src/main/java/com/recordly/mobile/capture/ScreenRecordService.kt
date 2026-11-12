package com.recordly.mobile.capture

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.recordly.mobile.MainActivity
import com.recordly.mobile.R
import com.recordly.mobile.RecordlyApp
import com.recordly.mobile.editor.AutoZoomGenerator
import com.recordly.mobile.model.AiEditPlan
import com.recordly.mobile.model.EditSegment
import com.recordly.mobile.model.RecordingProject
import com.recordly.mobile.model.SegmentAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null

    private var currentProjectId: String = ""
    private var currentOutputFile: File? = null
    private var recordWidth: Int = 1080
    private var recordHeight: Int = 1920
    private var hasAudioTrack: Boolean = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopAndFinalizeRecording()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startRecording(resultCode, data)
                } else {
                    stopSelf()
                }
            }
            ACTION_PAUSE -> {
                runCatching { mediaRecorder?.pause() }
                ActiveSessionTracker.pauseSession()
                updateNotification("Recording paused")
            }
            ACTION_RESUME -> {
                runCatching { mediaRecorder?.resume() }
                ActiveSessionTracker.resumeSession()
                updateNotification("Recording screen & mouse…")
            }
            ACTION_STOP -> {
                stopAndFinalizeRecording()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Preparing screen capture…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Preparing screen capture…"))
        }

        val app = application as RecordlyApp
        val (projectId, projectDir) = app.projectRepository.allocateProjectDir()
        currentProjectId = projectId
        val outFile = File(projectDir, "raw.mp4")
        currentOutputFile = outFile

        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val densityDpi = metrics.densityDpi

        // Scale down nicely for fast hardware encoding while preserving aspect ratio & /16 alignment
        val maxSide = 1920
        val scale = minOf(1f, maxSide.toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels).toFloat())
        recordWidth = ((metrics.widthPixels * scale).toInt() / 16) * 16
        recordHeight = ((metrics.heightPixels * scale).toInt() / 16) * 16
        if (recordWidth < 320) recordWidth = 720
        if (recordHeight < 320) recordHeight = 1280

        val projMgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projMgr.getMediaProjection(resultCode, data)
        mediaProjection = projection
        projection.registerCallback(projectionCallback, null)

        serviceScope.launch {
            val settings = app.settingsRepository.getGroqSettings()
            hasAudioTrack = settings.recordMicrophone

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this@ScreenRecordService)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder

            try {
                if (hasAudioTrack) {
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setOutputFile(outFile.absolutePath)
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                recorder.setVideoSize(recordWidth, recordHeight)
                recorder.setVideoFrameRate(30)
                recorder.setVideoEncodingBitRate(10_000_000)
                if (hasAudioTrack) {
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setAudioEncodingBitRate(128_000)
                    recorder.setAudioSamplingRate(44_100)
                }
                recorder.prepare()

                virtualDisplay = projection.createVirtualDisplay(
                    "RecordlyVirtualDisplay",
                    recordWidth,
                    recordHeight,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    recorder.surface,
                    null,
                    null
                )

                ActiveSessionTracker.beginSession()
                recorder.start()

                startTicker()
                updateNotification("Recording active • Cursor & Keyboard ready")
            } catch (e: Exception) {
                e.printStackTrace()
                cleanupMedia()
                stopSelf()
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive && ActiveSessionTracker.isRecording.value) {
                ActiveSessionTracker.updateElapsedTicker()
                delay(200L)
            }
        }
    }

    private fun stopAndFinalizeRecording() {
        tickerJob?.cancel()
        stopService(Intent(this, FloatingControlService::class.java))

        val (elapsedDuration, mouseSamples, keyboardIntervals) = ActiveSessionTracker.endSession()
        cleanupMedia()

        val file = currentOutputFile
        val projectId = currentProjectId
        if (file == null || !file.exists() || file.length() < 1024L || projectId.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val app = application as RecordlyApp
        serviceScope.launch(Dispatchers.IO) {
            val meta = app.projectRepository.readVideoMetadata(file.absolutePath)
            val actualDuration = if (meta.durationMs > 0L) meta.durationMs else elapsedDuration
            val settings = app.settingsRepository.getGroqSettings()
            val style = app.settingsRepository.getCanvasStyle().copy(defaultZoomScale = settings.defaultZoomScale)

            val autoZooms = if (settings.autoZoomEnabled && mouseSamples.isNotEmpty()) {
                AutoZoomGenerator.generateFromMouse(
                    samples = mouseSamples,
                    totalDurationMs = actualDuration,
                    defaultZoom = style.defaultZoomScale
                )
            } else emptyList()

            val initialSegments = AutoZoomGenerator.buildInitialSegmentsWithKeyboard(
                durationMs = actualDuration,
                keyboardIntervals = keyboardIntervals,
                mode = settings.keyboardTrimMode
            )

            val stamp = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date())
            val project = RecordingProject(
                id = projectId,
                title = "Recording $stamp",
                createdAtMs = System.currentTimeMillis(),
                durationMs = actualDuration,
                width = meta.width,
                height = meta.height,
                videoPath = file.absolutePath,
                mouseSamples = mouseSamples,
                keyboardIntervals = keyboardIntervals,
                editPlan = AiEditPlan(
                    summary = "Recorded ${actualDuration / 1000}s • ${mouseSamples.count { it.isDown }} clicks • ${keyboardIntervals.size} keyboard spans",
                    segments = initialSegments.ifEmpty {
                        listOf(EditSegment(0L, actualDuration, 1.0f, SegmentAction.KEEP, "Full recording"))
                    },
                    zoomKeyframes = autoZooms,
                    keyboardIntervals = keyboardIntervals
                ),
                styleConfig = style
            )
            app.projectRepository.saveProject(project)
            ActiveSessionTracker.markSavedProject(projectId)

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cleanupMedia() {
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.reset() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, RecordlyApp.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Recordly Mobile Studio")
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop & Edit", stopIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.recordly.mobile.ACTION_START_RECORD"
        const val ACTION_PAUSE = "com.recordly.mobile.ACTION_PAUSE_RECORD"
        const val ACTION_RESUME = "com.recordly.mobile.ACTION_RESUME_RECORD"
        const val ACTION_STOP = "com.recordly.mobile.ACTION_STOP_RECORD"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
