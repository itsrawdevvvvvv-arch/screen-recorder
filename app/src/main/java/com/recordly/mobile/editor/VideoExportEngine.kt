package com.recordly.mobile.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import com.recordly.mobile.data.SrtCodec
import com.recordly.mobile.model.EditSegment
import com.recordly.mobile.model.RecordingProject
import com.recordly.mobile.model.SegmentAction
import com.recordly.mobile.model.SrtCue
import com.recordly.mobile.model.ZoomKeyframe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
class VideoExportEngine(private val context: Context) {

    /** Range of the ORIGINAL recording that is kept (everything else is cut). */
    data class KeepSegment(val startMs: Long, val endMs: Long)

    /** Output (post-cut) time → original recording time. */
    class SegmentTimeMap(private val segments: List<KeepSegment>) {
        fun outputToOriginalMs(outputMs: Long): Long {
            var remaining = outputMs.coerceAtLeast(0L)
            for (s in segments) {
                val len = s.endMs - s.startMs
                if (remaining < len) return s.startMs + remaining
                remaining -= len
            }
            return segments.lastOrNull()?.endMs ?: outputMs
        }
    }

    companion object {
        /**
         * Exact camera matrix for Media3's MatrixTransformation (NDC, [-1,1], y up, frame = [-1,1]²):
         *
         *   g  = (2·fx − 1, 1 − 2·fy)         focus in NDC
         *   p' = S · (p − g)                  focus → (0,0), uniform scale S
         *      = S·p + (−S·gx, −S·gy)
         *
         * Focus is clamped to [0.5/S, 1 − 0.5/S] ⇒ |g| ≤ 1 − 1/S ⇒ the frame edge never enters the viewport.
         */
        fun buildZoomMatrix(view: ZoomView): Matrix {
            val m = Matrix()
            if (view.scale <= 1.001f) return m
            val t = CameraMath.ndcTransform(view.scale, view.focusX, view.focusY)
            m.setScale(t.scale, t.scale)
            m.postTranslate(t.tx, t.ty)
            return m
        }

        fun createZoomEffect(
            zoomKeyframes: List<ZoomKeyframe>,
            outputToOriginalMs: (Long) -> Long
        ): MatrixTransformation = MatrixTransformation { presentationTimeUs ->
            val originalMs = outputToOriginalMs(presentationTimeUs / 1000L)
            buildZoomMatrix(AutoZoomGenerator.evaluateView(originalMs, zoomKeyframes))
        }
    }

    suspend fun exportProject(
        project: RecordingProject,
        outputMp4: File,
        onProgress: (Float) -> Unit
    ): File = suspendCancellableCoroutine { cont ->
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                if (outputMp4.exists()) outputMp4.delete()

                val allSegments = project.editPlan?.segments?.takeIf { it.isNotEmpty() }
                    ?: listOf(EditSegment(0L, project.durationMs, 1.0f, SegmentAction.KEEP, "Full video"))

                val keepSegments = allSegments.filter { it.action == SegmentAction.KEEP && it.endMs > it.startMs }
                    .ifEmpty { listOf(EditSegment(0L, project.durationMs, 1.0f, SegmentAction.KEEP, "Full video")) }

                val hasExternalAudio = !project.externalAudioPath.isNullOrBlank() &&
                    File(project.externalAudioPath).exists()

                val videoItems = keepSegments.map { seg ->
                    val clipping = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(seg.startMs)
                        .setEndPositionMs(seg.endMs)
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.fromFile(File(project.videoPath)))
                        .setClippingConfiguration(clipping)
                        .build()

                    val spd = seg.speed.coerceIn(0.25f, 8.0f)
                    val speedProvider = object : SpeedProvider {
                        override fun getSpeed(timeUs: Long): Float = spd
                        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
                    }

                    EditedMediaItem.Builder(mediaItem)
                        .setSpeed(speedProvider)
                        .setRemoveAudio(hasExternalAudio)
                        .build()
                }

                val videoSequence = EditedMediaItemSequence.Builder(videoItems).build()

                val remappedCues: List<SrtCue> = project.editPlan?.remappedSrtCues?.takeIf { it.isNotEmpty() }
                    ?: SrtCodec.remapCuesToEditedTimeline(project.srtCues, keepSegments)

                val zoomKeyframes = project.editPlan?.zoomKeyframes ?: emptyList()

                // Global mathematically-exact camera centering MatrixTransformation
                val zoomMatrixEffect = createZoomEffect(zoomKeyframes) { outMs ->
                    SrtCodec.outputTimeToOriginalTimeMs(outMs, keepSegments)
                }

                // Dynamic BitmapOverlay for cursor halo, click ripples, and .srt captions
                val overlayW = project.width.coerceIn(480, 1080)
                val overlayH = project.height.coerceIn(480, 1920)
                val overlayBitmap = Bitmap.createBitmap(overlayW, overlayH, Bitmap.Config.ARGB_8888)
                val overlayCanvas = Canvas(overlayBitmap)

                val cursorOverlay = createStudioOverlay(
                    bitmap = overlayBitmap,
                    canvas = overlayCanvas,
                    width = overlayW,
                    height = overlayH,
                    project = project,
                    keepSegments = keepSegments,
                    remappedCues = remappedCues
                )

                val compositionEffects = Effects(
                    emptyList(),
                    listOf(zoomMatrixEffect, OverlayEffect(ImmutableList.of<TextureOverlay>(cursorOverlay)))
                )

                val sequences = mutableListOf(videoSequence)
                if (hasExternalAudio) {
                    val audioItem = EditedMediaItem.Builder(
                        MediaItem.fromUri(Uri.fromFile(File(project.externalAudioPath!!)))
                    ).setRemoveVideo(true).build()
                    sequences += EditedMediaItemSequence.Builder(listOf(audioItem)).build()
                }

                val composition = Composition.Builder(sequences)
                    .setEffects(compositionEffects)
                    .build()

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            onProgress(1f)
                            if (cont.isActive) cont.resume(outputMp4)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()

                transformer.start(composition, outputMp4.absolutePath)

                val progressHolder = ProgressHolder()
                val poller = object : Runnable {
                    override fun run() {
                        if (!cont.isActive) return
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                        }
                        if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                            mainHandler.postDelayed(this, 180L)
                        }
                    }
                }
                mainHandler.postDelayed(poller, 180L)

                cont.invokeOnCancellation {
                    mainHandler.post { runCatching { transformer.cancel() } }
                }
            } catch (t: Throwable) {
                if (cont.isActive) cont.resumeWithException(t)
            }
        }
    }

    suspend fun export(
        inputUri: Uri,
        outputFile: File,
        videoDurationMs: Long,
        keepSegments: List<KeepSegment> = emptyList(),
        zoomKeyframes: List<ZoomKeyframe>,
        videoBitrate: Int = 12_000_000,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.Main) {
        val segments = keepSegments.ifEmpty { listOf(KeepSegment(0L, videoDurationMs)) }
        val timeMap = SegmentTimeMap(segments)
        val zoomEffect = createZoomEffect(zoomKeyframes, timeMap::outputToOriginalMs)

        val items = segments.map { seg ->
            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(seg.startMs)
                        .setEndPositionMs(seg.endMs)
                        .build()
                )
                .build()
            EditedMediaItem.Builder(mediaItem).build()
        }
        val sequence = EditedMediaItemSequence.Builder(items).build()

        val composition = Composition.Builder(sequence)
            .setEffects(Effects(emptyList<AudioProcessor>(), listOf<Effect>(zoomEffect)))
            .build()

        if (outputFile.exists()) outputFile.delete()
        val done = CompletableDeferred<File>()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBitrate).build())
            .setEnableFallback(true)
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    done.complete(outputFile)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    done.completeExceptionally(exportException)
                }
            })
            .build()

        val progressJob = launch {
            val holder = ProgressHolder()
            while (isActive) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
                delay(200L)
            }
        }
        try {
            transformer.start(composition, outputFile.absolutePath)
            done.await()
        } catch (e: CancellationException) {
            transformer.cancel()
            throw e
        } finally {
            progressJob.cancel()
        }
    }

    private fun createStudioOverlay(
        bitmap: Bitmap,
        canvas: Canvas,
        width: Int,
        height: Int,
        project: RecordingProject,
        keepSegments: List<EditSegment>,
        remappedCues: List<SrtCue>
    ): BitmapOverlay {
        val cursorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 3f, Color.argb(170, 0, 0, 0))
        }
        val cursorStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#11131C")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6C5CE7")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        val captionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 14, 17, 26)
            style = Paint.Style.FILL
        }
        val captionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (width * 0.036f).coerceIn(26f, 44f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        return object : BitmapOverlay() {
            override fun getBitmap(presentationTimeUs: Long): Bitmap {
                bitmap.eraseColor(Color.TRANSPARENT)
                val outMs = presentationTimeUs / 1000L
                val origMs = SrtCodec.outputTimeToOriginalTimeMs(outMs, keepSegments)

                if (project.styleConfig.showCursorRing) {
                    val cam = AutoZoomGenerator.evaluateCameraAt(
                        originalTimeMs = origMs,
                        zoomKeyframes = project.editPlan?.zoomKeyframes ?: emptyList(),
                        mouseSamples = project.mouseSamples
                    )
                    val cx = cam.cursorX
                    val cy = cam.cursorY
                    if (cx != null && cy != null) {
                        val zoomedX = (0.5f + (cx - cam.focusX) * cam.scale).coerceIn(0f, 1f) * width
                        val zoomedY = (0.5f + (cy - cam.focusY) * cam.scale).coerceIn(0f, 1f) * height

                        if (cam.clickRippleProgress in 0f..1f) {
                            ripplePaint.alpha = ((1f - cam.clickRippleProgress) * 230).toInt().coerceIn(0, 255)
                            val radius = 18f + cam.clickRippleProgress * 56f
                            canvas.drawCircle(zoomedX, zoomedY, radius, ripplePaint)
                        }

                        // Soft focus halo around recorded mouse cursor
                        cursorFill.color = Color.argb(45, 108, 92, 231)
                        canvas.drawCircle(zoomedX, zoomedY, 22f, cursorFill)
                        cursorStroke.color = Color.argb(190, 0, 210, 160)
                        cursorStroke.strokeWidth = 3.5f
                        canvas.drawCircle(zoomedX, zoomedY, 22f, cursorStroke)
                    }
                }

                if (project.styleConfig.burnSubtitles) {
                    val activeCue = remappedCues.firstOrNull { outMs in it.startMs..it.endMs }
                    if (activeCue != null) {
                        val lines = activeCue.text.lines().take(2)
                        val lineHeight = captionTextPaint.fontSpacing
                        val maxTextW = lines.maxOfOrNull { captionTextPaint.measureText(it) } ?: 0f
                        val padX = 28f
                        val padY = 18f
                        val boxW = (maxTextW + padX * 2).coerceAtMost(width * 0.9f)
                        val boxH = lines.size * lineHeight + padY * 2
                        val centerX = width / 2f
                        val bottomY = height * 0.89f
                        val rect = RectF(
                            centerX - boxW / 2f,
                            bottomY - boxH,
                            centerX + boxW / 2f,
                            bottomY
                        )
                        canvas.drawRoundRect(rect, 22f, 22f, captionBgPaint)

                        var y = rect.top + padY - captionTextPaint.ascent()
                        for (line in lines) {
                            canvas.drawText(line, centerX, y, captionTextPaint)
                            y += lineHeight
                        }
                    }
                }

                return bitmap
            }
        }
    }
}
