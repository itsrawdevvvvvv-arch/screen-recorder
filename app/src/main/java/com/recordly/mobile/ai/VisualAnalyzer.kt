package com.recordly.mobile.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.recordly.mobile.editor.AutoZoomGenerator
import com.recordly.mobile.model.AccessibilityClick
import com.recordly.mobile.model.ActivityHotspot
import com.recordly.mobile.model.FrameInsight
import com.recordly.mobile.model.MouseSample
import com.recordly.mobile.model.TimeInterval
import com.recordly.mobile.model.TimestampedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong


/**
 * Post-recording cursor detector and visual activity analyzer.
 * The Android pointer is baked into the MediaProjection frames:
 *  1. Sample frames at 4–8 fps.
 *  2. Find the arrow (black outline, white fill, top-left hotspot) by multi-scale masked template matching
 *     with a fast two-probe pre-filter.
 *  3. Frame-difference (cursor area masked out) to find localized UI activity hotspots (touch / click reactions).
 *  4. Fuse with accessibility clicks.
 */
class VisualAnalyzer(private val config: Config = Config()) {

    data class Config(
        val sampleFps: Float = 6f,
        val analysisShortSidePx: Int = 720,
        /** Candidate cursor heights as a fraction of the frame's short side. */
        val cursorHeightFractions: List<Float> = listOf(0.016f, 0.022f, 0.030f, 0.040f, 0.052f, 0.066f),
        val minCursorScore: Float = 0.78f,
        val darkMax: Int = 110,
        val brightMin: Int = 180,
        val diffBlockPx: Int = 24,
        val blockDiffThreshold: Float = 6f,
        val maxChangedFraction: Float = 0.35f,
        val ignoreTopFraction: Float = 0.045f,   // status bar clock
        val reliableCoverage: Float = 0.15f
    )

    data class AnalysisResult(
        val cursorSamples: List<MouseSample>,
        val hotspots: List<ActivityHotspot>,
        val cursorCoverage: Float,
        val cursorReliable: Boolean,
        val durationMs: Long
    )

    // ------------------------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------------------------

    suspend fun analyze(
        context: Context,
        videoUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): AnalysisResult = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            var vw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            var vh = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) { val t = vw; vw = vh; vh = t }
            require(durationMs > 0 && vw > 0 && vh > 0) { "Unreadable video: $videoUri" }

            val scale = min(1f, config.analysisShortSidePx.toFloat() / min(vw, vh))
            val tw = max(64, (vw * scale).roundToInt())
            val th = max(64, (vh * scale).roundToInt())
            runAnalysis(retriever, durationMs, tw, th, onProgress)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Merge visual cursor track + accessibility clicks (+ visually inferred clicks) into the final
     * sorted sample list. [clockOffsetMs] shifts accessibility timestamps onto the video timeline.
     */
    fun fuse(
        analysis: AnalysisResult,
        accessibilityClicks: List<AccessibilityClick>,
        clockOffsetMs: Long = 0L
    ): List<MouseSample> {
        val cursor = analysis.cursorSamples
        val out = ArrayList<MouseSample>(cursor.size + accessibilityClicks.size + 16)
        out.addAll(cursor)
        val a11yTimes = ArrayList<Long>()

        for (c in accessibilityClicks) {
            val t = c.timeMs + clockOffsetMs
            if (t < 0 || t > analysis.durationMs) continue
            val visual = if (hasSampleWithin(cursor, t, 500L)) AutoZoomGenerator.interpolateCursor(t, cursor) else null
            val cx = c.x
            val cy = c.y
            val sample: MouseSample? = when {
                visual != null ->
                    MouseSample(t, visual.first, visual.second, isClick = true, confidence = 0.9f, source = MouseSample.Source.FUSED)
                cx != null && cy != null ->
                    MouseSample(t, cx, cy, isClick = true, confidence = 0.7f, source = MouseSample.Source.ACCESSIBILITY)
                else -> analysis.hotspots
                    .filter { abs(it.timeMs - t) <= 600L }
                    .maxByOrNull { it.strength }
                    ?.let { MouseSample(t, it.x, it.y, isClick = true, confidence = 0.4f, source = MouseSample.Source.INFERRED) }
            }
            if (sample != null) {
                out += sample
                a11yTimes += t
            }
        }

        // Clicks the accessibility service cannot see (games, web canvases, mouse on custom views)
        inferVisualClicks(cursor, analysis.hotspots)
            .filter { v -> a11yTimes.none { abs(it - v.timeMs) < 700L } }
            .forEach { out += it }

        return out.sortedBy { it.timeMs }
    }

    // ------------------------------------------------------------------------------------------
    // Frame loop
    // ------------------------------------------------------------------------------------------

    private suspend fun runAnalysis(
        retriever: MediaMetadataRetriever,
        durationMs: Long,
        tw: Int,
        th: Int,
        onProgress: (Float) -> Unit
    ): AnalysisResult {
        val templates = buildTemplates(min(tw, th))
        val pixelCount = tw * th
        val pixels = IntArray(pixelCount)
        var luma = ByteArray(pixelCount)
        var prevLuma = ByteArray(pixelCount)
        var havePrev = false

        val B = config.diffBlockPx
        val diffSums = IntArray(((tw + B - 1) / B) * ((th + B - 1) / B))

        val stepMs = (1000f / config.sampleFps.coerceIn(1f, 12f)).roundToLong().coerceAtLeast(60L)
        val cursorSamples = ArrayList<MouseSample>()
        val hotspots = ArrayList<ActivityHotspot>()
        var frames = 0
        var detected = 0
        var lastScaleIdx = -1
        var misses = 0
        var prevRect: IntArray? = null

        var tMs = 0L
        while (tMs <= durationMs) {
            currentCoroutineContext().ensureActive()
            val frameTime = min(tMs, max(0L, durationMs - 50L))
            val bmp = grabFrame(retriever, frameTime * 1000L, tw, th)
            frames++
            if (bmp != null) {
                bmp.getPixels(pixels, 0, tw, 0, 0, tw, th)
                bmp.recycle()
                for (i in 0 until pixelCount) {
                    val p = pixels[i]
                    luma[i] = ((77 * ((p shr 16) and 255) + 150 * ((p shr 8) and 255) + 29 * (p and 255)) shr 8).toByte()
                }

                val order: List<Int> =
                    if (lastScaleIdx >= 0 && misses < 3)
                        (max(0, lastScaleIdx - 1)..min(templates.lastIndex, lastScaleIdx + 1)).toList()
                    else templates.indices.toList()

                val hit = detectCursor(luma, tw, th, templates, order)
                var curRect: IntArray? = null
                if (hit != null) {
                    detected++
                    misses = 0
                    lastScaleIdx = hit.templateIndex
                    cursorSamples += MouseSample(
                        timeMs = tMs,
                        x = ((hit.hotX + 0.5f) / tw).coerceIn(0f, 1f),
                        y = ((hit.hotY + 0.5f) / th).coerceIn(0f, 1f),
                        isClick = false,
                        confidence = hit.score,
                        source = MouseSample.Source.VISUAL
                    )
                    curRect = intArrayOf(hit.left, hit.top, hit.left + hit.w, hit.top + hit.h)
                } else {
                    misses++
                    if (misses >= 3) lastScaleIdx = -1
                }

                if (havePrev) {
                    frameActivity(luma, prevLuma, tw, th, curRect, prevRect, diffSums, tMs)?.let { hotspots += it }
                }
                val tmp = prevLuma; prevLuma = luma; luma = tmp
                havePrev = true
                prevRect = curRect
            }
            tMs += stepMs
            onProgress((tMs.toFloat() / durationMs).coerceIn(0f, 1f))
        }

        val coverage = if (frames > 0) detected.toFloat() / frames else 0f
        return AnalysisResult(
            cursorSamples = removeOutliers(cursorSamples),
            hotspots = hotspots,
            cursorCoverage = coverage,
            cursorReliable = coverage >= config.reliableCoverage,
            durationMs = durationMs
        )
    }

    private fun grabFrame(r: MediaMetadataRetriever, timeUs: Long, w: Int, h: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 27) {
                val b = r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, w, h) ?: return null
                if (b.width == w && b.height == h) b else scaleTo(b, w, h)
            } else {
                val b = r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
                if (b.width == w && b.height == h) b else scaleTo(b, w, h)
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun scaleTo(src: Bitmap, w: Int, h: Int): Bitmap {
        val s = Bitmap.createScaledBitmap(src, w, h, true)
        if (s !== src) src.recycle()
        return s
    }

    // ------------------------------------------------------------------------------------------
    // Cursor template (procedural Android arrow)
    // ------------------------------------------------------------------------------------------

    private class CursorTemplate(
        val w: Int,
        val h: Int,
        val hotX: Int,
        val hotY: Int,
        val border: IntArray,   // flattened (dx, dy)
        val fill: IntArray,     // flattened (dx, dy)
        val probeBx: Int, val probeBy: Int,
        val probeFx: Int, val probeFy: Int
    )

    private class CursorHit(
        val left: Int, val top: Int,
        val hotX: Int, val hotY: Int,
        val w: Int, val h: Int,
        val score: Float,
        val templateIndex: Int
    )

    private fun buildTemplates(shortSide: Int): List<CursorTemplate> =
        config.cursorHeightFractions
            .map { max(10, (shortSide * it).roundToInt()) }
            .distinct()
            .mapNotNull { buildTemplate(it) }

    private fun buildTemplate(heightPx: Int): CursorTemplate? {
        val hf = heightPx.toFloat()
        val stroke = max(1.3f, hf * 0.09f)
        val pad = ceil(stroke).toInt() + 1
        val bw = ceil(hf * 0.6f).toInt() + pad * 2
        val bh = heightPx + pad * 2

        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val path = Path()
        ARROW.forEachIndexed { i, (ux, uy) ->
            val x = pad + ux * hf
            val y = pad + uy * hf
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt()
        })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeJoin = Paint.Join.ROUND
            color = 0xFF000000.toInt()
        })
        val px = IntArray(bw * bh)
        bmp.getPixels(px, 0, bw, 0, 0, bw, bh)
        bmp.recycle()

        val cls = IntArray(bw * bh)           // 1 = border, 2 = fill
        val border = ArrayList<Int>()
        val fill = ArrayList<Int>()
        for (y in 0 until bh) for (x in 0 until bw) {
            val p = px[y * bw + x]
            if ((p ushr 24) < 230) continue
            val l = (77 * ((p shr 16) and 255) + 150 * ((p shr 8) and 255) + 29 * (p and 255)) shr 8
            if (l < 80) { border += x; border += y; cls[y * bw + x] = 1 }
            else if (l > 215) { fill += x; fill += y; cls[y * bw + x] = 2 }
        }
        if (border.size < 40 || fill.size < 16) return null

        val row = (pad + (0.4f * hf).toInt()) * bw
        val bStart = (0 until bw).firstOrNull { cls[row + it] == 1 } ?: return null
        var bEnd = bStart
        while (bEnd + 1 < bw && cls[row + bEnd + 1] == 1) bEnd++
        val fStart = (bEnd + 1 until bw).firstOrNull { cls[row + it] == 2 } ?: return null
        var fEnd = fStart
        while (fEnd + 1 < bw && cls[row + fEnd + 1] == 2) fEnd++
        val rowY = pad + (0.4f * hf).toInt()

        return CursorTemplate(
            w = bw, h = bh, hotX = pad, hotY = pad,
            border = border.toIntArray(), fill = fill.toIntArray(),
            probeBx = (bStart + bEnd) / 2, probeBy = rowY,
            probeFx = (fStart + fEnd) / 2, probeFy = rowY
        )
    }

    private fun detectCursor(
        luma: ByteArray, w: Int, h: Int,
        templates: List<CursorTemplate>, order: List<Int>
    ): CursorHit? {
        var best: CursorHit? = null
        var bestScore = config.minCursorScore
        val dark = config.darkMax
        val bright = config.brightMin

        for (ti in order) {
            val t = templates[ti]
            val maxX = w - t.w
            val maxY = h - t.h
            if (maxX < 0 || maxY < 0) continue
            for (y in 0..maxY) {
                val rowB = (y + t.probeBy) * w + t.probeBx
                val rowF = (y + t.probeFy) * w + t.probeFx
                for (x in 0..maxX) {
                    if ((luma[rowB + x].toInt() and 255) > dark) continue
                    if ((luma[rowF + x].toInt() and 255) < bright) continue
                    val s = score(luma, w, x, y, t)
                    if (s > bestScore) {
                        bestScore = s
                        best = CursorHit(x, y, x + t.hotX, y + t.hotY, t.w, t.h, s, ti)
                    }
                }
            }
        }
        return best
    }

    private fun score(luma: ByteArray, w: Int, x: Int, y: Int, t: CursorTemplate): Float {
        var okB = 0
        var i = 0
        val b = t.border
        while (i < b.size) {
            if ((luma[(y + b[i + 1]) * w + x + b[i]].toInt() and 255) <= config.darkMax) okB++
            i += 2
        }
        var okF = 0
        i = 0
        val f = t.fill
        while (i < f.size) {
            if ((luma[(y + f[i + 1]) * w + x + f[i]].toInt() and 255) >= config.brightMin) okF++
            i += 2
        }
        return 0.5f * okB / (b.size / 2) + 0.5f * okF / (f.size / 2)
    }

    // ------------------------------------------------------------------------------------------
    // Frame differencing (activity hotspots)
    // ------------------------------------------------------------------------------------------

    private fun frameActivity(
        cur: ByteArray, prev: ByteArray, w: Int, h: Int,
        exA: IntArray?, exB: IntArray?,
        sums: IntArray, tMs: Long
    ): ActivityHotspot? {
        val B = config.diffBlockPx
        val gw = (w + B - 1) / B
        val gh = (h + B - 1) / B
        java.util.Arrays.fill(sums, 0)
        val yStart = (h * config.ignoreTopFraction).toInt()

        for (y in yStart until h) {
            val rowBase = (y / B) * gw
            val o = y * w
            for (x in 0 until w) {
                val d = abs((cur[o + x].toInt() and 255) - (prev[o + x].toInt() and 255))
                if (d > 8) sums[rowBase + x / B] += d
            }
        }

        var changed = 0
        var wsum = 0f
        var cx = 0f
        var cy = 0f
        var maxMean = 0f
        for (by in (yStart / B) until gh) for (bx in 0 until gw) {
            val bw = min(B, w - bx * B)
            val bh = min(B, h - by * B)
            val mean = sums[by * gw + bx].toFloat() / (bw * bh)
            if (mean < config.blockDiffThreshold) continue
            val l = bx * B
            val t = by * B
            if (intersects(l, t, l + bw, t + bh, exA, B / 2) || intersects(l, t, l + bw, t + bh, exB, B / 2)) continue
            changed++
            wsum += mean
            cx += mean * (l + bw / 2f)
            cy += mean * (t + bh / 2f)
            if (mean > maxMean) maxMean = mean
        }
        if (changed == 0) return null
        if (changed.toFloat() / (gw * gh) > config.maxChangedFraction) return null
        return ActivityHotspot(
            timeMs = tMs,
            x = (cx / wsum / w).coerceIn(0f, 1f),
            y = (cy / wsum / h).coerceIn(0f, 1f),
            strength = (maxMean / 80f).coerceIn(0.1f, 1f)
        )
    }

    private fun intersects(l: Int, t: Int, r: Int, b: Int, ex: IntArray?, margin: Int): Boolean {
        if (ex == null) return false
        return l < ex[2] + margin && r > ex[0] - margin && t < ex[3] + margin && b > ex[1] - margin
    }

    private fun removeOutliers(samples: List<MouseSample>): List<MouseSample> {
        if (samples.size < 3) return samples
        val out = ArrayList<MouseSample>(samples.size)
        out += samples.first()
        for (i in 1 until samples.lastIndex) {
            val p = out.last()
            val c = samples[i]
            val n = samples[i + 1]
            val jumpIn = hypot(c.x - p.x, c.y - p.y)
            val jumpOut = hypot(c.x - n.x, c.y - n.y)
            val neighbours = hypot(p.x - n.x, p.y - n.y)
            if (jumpIn > 0.35f && jumpOut > 0.35f && neighbours < 0.15f) continue
            out += c
        }
        out += samples.last()
        return out
    }

    private fun hasSampleWithin(samples: List<MouseSample>, t: Long, windowMs: Long): Boolean {
        if (samples.isEmpty()) return false
        val idx = AutoZoomGenerator.upperBound(samples, t)
        val before = if (idx > 0) t - samples[idx - 1].timeMs else Long.MAX_VALUE
        val after = if (idx < samples.size) samples[idx].timeMs - t else Long.MAX_VALUE
        return min(before, after) <= windowMs
    }

    private fun inferVisualClicks(cursor: List<MouseSample>, hotspots: List<ActivityHotspot>): List<MouseSample> {
        if (cursor.size < 2 || hotspots.isEmpty()) return emptyList()
        val stepMs = max(60L, (1000f / config.sampleFps.coerceIn(1f, 12f)).roundToLong())
        val out = ArrayList<MouseSample>()
        for (hs in hotspots) {
            val c = AutoZoomGenerator.interpolateCursor(hs.timeMs, cursor) ?: continue
            if (!hasSampleWithin(cursor, hs.timeMs, stepMs * 2)) continue
            if (hypot(hs.x - c.first, hs.y - c.second) > 0.15f) continue
            val near = cursor.filter { abs(it.timeMs - hs.timeMs) <= stepMs * 5 / 2 }
            if (near.any { hypot(it.x - c.first, it.y - c.second) > 0.03f }) continue
            val t = max(0L, hs.timeMs - stepMs / 2)
            if (out.isNotEmpty() && t - out.last().timeMs < 600L) continue
            out += MouseSample(t, c.first, c.second, isClick = true, confidence = 0.6f, source = MouseSample.Source.INFERRED)
        }
        return out
    }

    // ------------------------------------------------------------------------------------------
    // Groq Vision & AI Keyframe Helpers
    // ------------------------------------------------------------------------------------------

    suspend fun extractSmartKeyframes(
        videoPath: String,
        durationMs: Long,
        mouseSamples: List<MouseSample>,
        maxFrames: Int = 12
    ): List<TimestampedImage> = withContext(Dispatchers.IO) {
        if (durationMs <= 0L) return@withContext emptyList()
        val targetTimes = linkedSetOf<Long>()
        mouseSamples.filter { it.isClick }
            .map { it.timeMs.coerceIn(0L, durationMs) }
            .take(maxFrames / 2)
            .forEach { targetTimes.add(it) }

        val step = (durationMs / maxOf(1, maxFrames - targetTimes.size)).coerceAtLeast(1200L)
        var t = 500L
        while (t < durationMs && targetTimes.size < maxFrames) {
            if (targetTimes.none { abs(it - t) < 1000L }) targetTimes.add(t)
            t += step
        }

        val sortedTimes = targetTimes.sorted().take(maxFrames)
        val retriever = MediaMetadataRetriever()
        val images = mutableListOf<TimestampedImage>()
        try {
            retriever.setDataSource(videoPath)
            for (ms in sortedTimes) {
                val bmp = retriever.getFrameAtTime(ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val scaled = if (maxOf(bmp.width, bmp.height) > 640) {
                    val r = 640f / maxOf(bmp.width, bmp.height)
                    Bitmap.createScaledBitmap(bmp, (bmp.width * r).toInt(), (bmp.height * r).toInt(), true)
                } else bmp
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 74, out)
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

    private companion object {
        val ARROW = listOf(
            0.00f to 0.00f, 0.00f to 0.83f, 0.18f to 0.65f, 0.30f to 0.93f,
            0.40f to 0.88f, 0.28f to 0.60f, 0.55f to 0.60f
        )
    }
}
