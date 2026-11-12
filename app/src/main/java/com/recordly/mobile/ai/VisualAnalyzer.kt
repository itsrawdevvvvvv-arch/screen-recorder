package com.recordly.mobile.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import com.recordly.mobile.model.FrameInsight
import com.recordly.mobile.model.MouseSample
import com.recordly.mobile.model.TimeInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

object VisualAnalyzer {

    /**
     * Extracts compact JPEG keyframes across the recorded video so Groq Vision can locate the
     * visible Android mouse cursor, detect on-screen keyboards, and spot idle/loading screens.
     */
    suspend fun extractSmartKeyframes(
        videoPath: String,
        durationMs: Long,
        mouseSamples: List<MouseSample>,
        maxFrames: Int = 15
    ): List<TimestampedImage> = withContext(Dispatchers.IO) {
        if (durationMs <= 0L) return@withContext emptyList()

        val targetTimes = linkedSetOf<Long>()
        mouseSamples.filter { it.isDown }
            .map { it.timestampMs.coerceIn(0L, durationMs) }
            .take(maxFrames / 3)
            .forEach { targetTimes.add(it) }

        val step = (durationMs / maxOf(1, maxFrames - targetTimes.size)).coerceAtLeast(1000L)
        var t = 400L
        while (t < durationMs && targetTimes.size < maxFrames) {
            val tooClose = targetTimes.any { abs(it - t) < 900L }
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
                val scaled = scaleDown(bmp, 720)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 76, out)
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
     * Post-recording local computer-vision pass that tracks the visible mouse arrow / localized
     * pointer motion between consecutive frames (~500ms spacing), without needing Shizuku.
     */
    suspend fun trackVisibleCursorMotion(
        videoPath: String,
        durationMs: Long,
        maxSamples: Int = 40
    ): List<MouseSample> = withContext(Dispatchers.IO) {
        if (durationMs <= 0L) return@withContext emptyList()
        val retriever = MediaMetadataRetriever()
        val detected = mutableListOf<MouseSample>()

        try {
            retriever.setDataSource(videoPath)
            val stepMs = (durationMs / maxSamples.coerceAtLeast(1)).coerceAtLeast(350L)
            var prevSmall: Bitmap? = null
            var t = 250L

            while (t < durationMs) {
                val raw = retriever.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                if (raw != null) {
                    val small = Bitmap.createScaledBitmap(raw, 160, 284, true)
                    raw.recycle()

                    val prev = prevSmall
                    if (prev != null) {
                        val hotspot = findLocalizedMotionCentroid(prev, small)
                        if (hotspot != null) {
                            detected += MouseSample(
                                timestampMs = t,
                                xNorm = hotspot.first,
                                yNorm = hotspot.second,
                                isDown = hotspot.third,
                                isUp = hotspot.third,
                                button = if (hotspot.third) 1 else 0
                            )
                        }
                        prev.recycle()
                    }
                    prevSmall = small
                }
                t += stepMs
            }
            prevSmall?.recycle()
        } catch (_: Throwable) {
        } finally {
            runCatching { retriever.release() }
        }
        detected
    }

    /**
     * Merges Groq Vision cursor detections (exact visible arrow coordinates) with local
     * motion hotspots and any Accessibility click events into one smooth cursor track.
     */
    fun fusePostRecordingCursorSamples(
        visionInsights: List<FrameInsight>,
        localMotionSamples: List<MouseSample>,
        existingSamples: List<MouseSample>
    ): List<MouseSample> {
        val visionSamples = visionInsights.mapNotNull { insight ->
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

        val combined = (existingSamples + visionSamples + localMotionSamples)
            .sortedBy { it.timestampMs }

        // Deduplicate samples within 120ms, preferring Vision/Accessibility over raw motion diff
        val cleaned = mutableListOf<MouseSample>()
        for (sample in combined) {
            val prev = cleaned.lastOrNull()
            if (prev != null && abs(sample.timestampMs - prev.timestampMs) < 120L) {
                if (sample.isDown && !prev.isDown) {
                    cleaned[cleaned.lastIndex] = sample
                }
            } else {
                cleaned += sample
            }
        }
        return cleaned
    }

    /**
     * Finds small localized pixel-change hotspots typical of a moving mouse pointer over a steady UI.
     * Returns Triple(xNorm, yNorm, isLikelyClickOrTransition) or null if full-screen scroll/blank.
     */
    private fun findLocalizedMotionCentroid(a: Bitmap, b: Bitmap): Triple<Float, Float, Boolean>? {
        val w = minOf(a.width, b.width)
        val h = minOf(a.height, b.height)
        val totalPixels = w * h
        if (totalPixels <= 0) return null

        var changedCount = 0
        var sumX = 0L
        var sumY = 0L

        val pxA = IntArray(totalPixels)
        val pxB = IntArray(totalPixels)
        a.getPixels(pxA, 0, w, 0, 0, w, h)
        b.getPixels(pxB, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val c1 = pxA[rowOffset + x]
                val c2 = pxB[rowOffset + x]
                val diff = abs(Color.red(c1) - Color.red(c2)) +
                    abs(Color.green(c1) - Color.green(c2)) +
                    abs(Color.blue(c1) - Color.blue(c2))
                if (diff > 65) {
                    changedCount++
                    sumX += x
                    sumY += y
                }
            }
        }

        val ratio = changedCount.toFloat() / totalPixels.toFloat()
        // Ignore unchanged frames (<0.15%) or full-screen scrolls/transitions (>35%)
        if (ratio < 0.0015f || ratio > 0.35f || changedCount == 0) return null

        val cx = (sumX.toFloat() / changedCount / w.toFloat()).coerceIn(0.02f, 0.98f)
        val cy = (sumY.toFloat() / changedCount / h.toFloat()).coerceIn(0.02f, 0.98f)
        // Small localized change + slight UI response (2%..15%) often indicates a button hover/click
        val likelyClick = ratio in 0.015f..0.18f
        return Triple(cx, cy, likelyClick)
    }

    /**
     * Merges real-time Accessibility keyboard intervals with keyboard spans detected by Groq Vision.
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

    private fun scaleDown(src: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(src.width, src.height)
        if (maxSide <= maxDimension) return src
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
