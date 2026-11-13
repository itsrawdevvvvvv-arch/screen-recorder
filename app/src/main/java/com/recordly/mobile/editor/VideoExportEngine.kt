package com.recordly.mobile.editor

import android.content.Context
import android.graphics.Matrix
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
import androidx.media3.effect.MatrixTransformation
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
import com.recordly.mobile.model.EditSegment
import com.recordly.mobile.model.RecordingProject
import com.recordly.mobile.model.SegmentAction
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

    data class KeepSegment(val startMs: Long, val endMs: Long, val speed: Float = 1.0f)

    class SegmentTimeMap(private val segments: List<KeepSegment>) {
        fun outputToOriginalMs(outputMs: Long): Long {
            var remaining = outputMs.coerceAtLeast(0L)
            for (s in segments) {
                val durationOut = ((s.endMs - s.startMs) / s.speed).toLong().coerceAtLeast(1L)
                if (remaining < durationOut) {
                    return s.startMs + (remaining * s.speed).toLong()
                }
                remaining -= durationOut
            }
            return segments.lastOrNull()?.endMs ?: outputMs
        }
    }

    companion object {
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

                val keepSegments = allSegments
                    .filter { it.action == SegmentAction.KEEP && it.endMs > it.startMs }
                    .map { KeepSegment(it.startMs, it.endMs, it.speed) }
                    .ifEmpty { listOf(KeepSegment(0L, project.durationMs, 1.0f)) }

                val videoItems = keepSegments.map { seg ->
                    val clipping = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(seg.startMs)
                        .setEndPositionMs(seg.endMs)
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.fromFile(File(project.videoPath)))
                        .setClippingConfiguration(clipping)
                        .build()

                    val spd = seg.speed.coerceIn(0.25f, 6.0f)
                    val speedProvider = object : SpeedProvider {
                        override fun getSpeed(timeUs: Long): Float = spd
                        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
                    }

                    EditedMediaItem.Builder(mediaItem)
                        .setSpeed(speedProvider)
                        .build()
                }

                val videoSequence = EditedMediaItemSequence.Builder(videoItems).build()
                val zoomKeyframes = project.editPlan?.zoomKeyframes ?: emptyList()
                val timeMap = SegmentTimeMap(keepSegments)

                val zoomMatrixEffect = createZoomEffect(zoomKeyframes, timeMap::outputToOriginalMs)
                val compositionEffects = Effects(
                    emptyList(),
                    listOf(zoomMatrixEffect)
                )

                val composition = Composition.Builder(listOf(videoSequence))
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
}
