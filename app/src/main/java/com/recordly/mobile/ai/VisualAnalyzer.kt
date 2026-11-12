package com.recordly.mobile.ai

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.recordly.mobile.model.FrameInsight
import com.recordly.mobile.model.MouseSample
import com.recordly.mobile.model.TimeInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object VisualAnalyzer {

    /**
     * Extracts compact JPEG keyframes at click timestamps and regular intervals so Groq Vision
     * can understand UI context, detect on-screen keyboards (if Accessibility wasn't enabled),
     * and spot idle/loading screens without exceeding payload limits.
     */
    suspend fun extractSmartKeyframes(
        videoPath: String,
        durationMs: Long,
        mouseSamples: List<MouseSample>,
        maxFrames: Int = 12
    ): List<TimestampedImage> = withContext(Dispatchers.IO) {
        if (durationMs <= 0L) return@withContext emptyList()

        val targetTimes = linkedSetOf<Long>()
        // Prioritize mouse clicks first
        mouseSamples.filter { it.isDown }
            .map { it.timestampMs.coerceIn(0L, durationMs) }
            .take(maxFrames / 2)
            .forEach { targetTimes.add(it) }

        // Fill remaining budget evenly across the recording
        val step = (durationMs / maxOf(1, maxFrames - targetTimes.size)).coerceAtLeast(1500L)
        var t = 500L
        while (t < durationMs && targetTimes.size < maxFrames) {
            val tooClose = targetTimes.any { kotlin.math.abs(it - t) < 1200L }
            if (!tooClose) targetTimes.add(t)
            t += step
        }

        val sortedTimes = targetTimes.sorted().take(maxFrames)
        val retriever = MediaMetadataRetriever()
        val images = mutableListOf<TimestampedImage>()

        try {
            retriever.setDataSource(videoPath)
            for (ms in sortedTimes) {
                val bmp = retriever.getFrameAtTime(ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: continue
                val scaled = scaleDown(bmp, 640)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
                if (scaled !== bmp) scaled.recycle()
                bmp.recycle()
                images += TimestampedImage(ms, out.toByteArray())
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { retriever.release() }
        }
        images
    }

    /**
     * Merges real-time Accessibility keyboard intervals with any keyboard spans detected by
     * Groq Vision (used when AccessibilityService was disabled during recording or for imported videos).
     */
    fun mergeKeyboardIntervals(
        accessibilityIntervals: List<TimeInterval>,
        visionInsights: List<FrameInsight>,
        totalDurationMs: Long
    ): List<TimeInterval> {
        if (accessibilityIntervals.isNotEmpty()) return accessibilityIntervals

        val kbFrames = visionInsights.filter { it.keyboardVisible }.sortedBy { it.timestampMs }
        if (kbFrames.isEmpty()) return emptyList()

        val inferred = mutableListOf<TimeInterval>()
        var spanStart = (kbFrames.first().timestampMs - 700L).coerceAtLeast(0L)
        var spanEnd = (kbFrames.first().timestampMs + 900L).coerceAtMost(totalDurationMs)

        for (frame in kbFrames.drop(1)) {
            if (frame.timestampMs - spanEnd <= 2500L) {
                spanEnd = (frame.timestampMs + 900L).coerceAtMost(totalDurationMs)
            } else {
                inferred += TimeInterval(spanStart, spanEnd, "Phone Keyboard (AI Vision)", trim = true)
                spanStart = (frame.timestampMs - 700L).coerceAtLeast(0L)
                spanEnd = (frame.timestampMs + 900L).coerceAtMost(totalDurationMs)
            }
        }
        inferred += TimeInterval(spanStart, spanEnd, "Phone Keyboard (AI Vision)", trim = true)
        return inferred
    }

    /**
     * Converts Groq Vision cursor hints into synthetic MouseSamples if Shizuku wasn't running.
     */
    fun fallbackMouseSamplesFromVision(visionInsights: List<FrameInsight>): List<MouseSample> {
        return visionInsights.mapNotNull { insight ->
            val x = insight.cursorHintX ?: return@mapNotNull null
            val y = insight.cursorHintY ?: return@mapNotNull null
            MouseSample(
                timestampMs = insight.timestampMs,
                xNorm = x.coerceIn(0f, 1f),
                yNorm = y.coerceIn(0f, 1f),
                isDown = true,
                isUp = true,
                button = 1
            )
        }
    }

    private fun scaleDown(src: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(src.width, src.height)
        if (maxSide <= maxDimension) return src
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
