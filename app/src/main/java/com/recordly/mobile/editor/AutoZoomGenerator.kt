package com.recordly.mobile.editor

import com.recordly.mobile.model.AccessibilityClick
import com.recordly.mobile.model.ActivityHotspot
import com.recordly.mobile.model.CameraState
import com.recordly.mobile.model.EditSegment
import com.recordly.mobile.model.FocusWaypoint
import com.recordly.mobile.model.KeyboardTrimMode
import com.recordly.mobile.model.MouseSample
import com.recordly.mobile.model.SegmentAction
import com.recordly.mobile.model.TimeInterval
import com.recordly.mobile.model.ZoomKeyframe
import com.recordly.mobile.model.ZoomSource
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Single source of truth for viewport math, shared by Compose preview and Media3 export. */
object CameraMath {

    /** Keeps the viewport inside the frame: focus ∈ [0.5/S, 1 − 0.5/S]. */
    fun clampFocus(focus: Float, scale: Float): Float {
        if (scale <= 1f) return 0.5f
        val half = 0.5f / scale
        return focus.coerceIn(half, 1f - half)
    }

    /**
     * Transform applied to NDC vertices: p' = S·(p − g) = S·p + (tx, ty), with g = focus in NDC.
     * [tx],[ty] are NDC translations (y up).
     */
    data class NdcTransform(val scale: Float, val tx: Float, val ty: Float) {
        /** Compose graphicsLayer translation (pivot = center, y down). */
        fun pixelTranslationX(widthPx: Float): Float = tx * widthPx / 2f
        fun pixelTranslationY(heightPx: Float): Float = -ty * heightPx / 2f
    }

    fun ndcTransform(scale: Float, focusX: Float, focusY: Float): NdcTransform {
        val s = max(1f, scale)
        val fx = clampFocus(focusX, s)
        val fy = clampFocus(focusY, s)
        val gx = fx * 2f - 1f
        val gy = 1f - fy * 2f
        return NdcTransform(s, -s * gx, -s * gy)
    }
}

/** Zoom only (no cursor); cheap enough to call per exported frame. Focus is already clamped. */
data class ZoomView(val scale: Float, val focusX: Float, val focusY: Float)

object AutoZoomGenerator {

    const val PAN_DURATION_MS = 700L
    private const val MIN_SEGMENT_MS = 900L
    private const val RIPPLE_MS = 600L

    data class Config(
        val leadInMs: Long = 350L,
        val leadOutMs: Long = 400L,
        val minHoldMs: Long = 1500L,
        val maxHoldMs: Long = 2500L,
        val minScale: Float = 1.8f,
        val baseScale: Float = 2.0f,
        val maxScale: Float = 2.2f,
        /** A new event within this window pans instead of zooming out. */
        val panMergeWindowMs: Long = 2000L,
        val minPanDistance: Float = 0.05f,
        val dwellRadius: Float = 0.035f,
        val dwellMinMs: Long = 700L,
        val dwellMaxMs: Long = 6000L,
        val minConfidence: Float = 0.6f,
        val minEventSpacingMs: Long = 400L,
        val hotspotMinStrength: Float = 0.25f,
        val hotspotSpacingMs: Long = 1500L
    )

    private data class ZoomEvent(val timeMs: Long, val x: Float, val y: Float, val weight: Float)

    // ------------------------------------------------------------------------------------------
    // Keyframe generation
    // ------------------------------------------------------------------------------------------

    fun generate(
        samples: List<MouseSample>,
        hotspots: List<ActivityHotspot>,
        durationMs: Long,
        cursorReliable: Boolean = true,
        config: Config = Config()
    ): List<ZoomKeyframe> {
        if (durationMs <= 0L) return emptyList()
        val sorted = samples.sortedBy { it.timeMs }
        var events = collectEvents(sorted, cursorReliable, config)
        if (events.isEmpty()) events = eventsFromHotspots(hotspots, config)
        if (events.isEmpty()) return emptyList()

        val keyframes = ArrayList<ZoomKeyframe>()
        var prevEnd = 0L
        for (cluster in clusterEvents(events, config)) {
            val kf = buildKeyframe(cluster, prevEnd, durationMs, config) ?: continue
            keyframes += kf
            prevEnd = kf.endMs
        }
        return keyframes
    }

    private fun collectEvents(sorted: List<MouseSample>, cursorReliable: Boolean, cfg: Config): List<ZoomEvent> {
        val clicks = sorted.filter { it.isClick }
            .map { ZoomEvent(it.timeMs, it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f), 1f) }
        val dwells = if (cursorReliable) detectDwells(sorted, cfg) else emptyList()
        val keptDwells = dwells.filter { d -> clicks.none { abs(it.timeMs - d.timeMs) < 800L } }

        val all = (clicks + keptDwells).sortedBy { it.timeMs }
        val out = ArrayList<ZoomEvent>()
        for (e in all) {
            val prev = out.lastOrNull()
            if (prev != null && e.timeMs - prev.timeMs < cfg.minEventSpacingMs) {
                if (e.weight > prev.weight) out[out.lastIndex] = e
                continue
            }
            out += e
        }
        return out
    }

    private fun detectDwells(sorted: List<MouseSample>, cfg: Config): List<ZoomEvent> {
        val cursor = sorted.filter { !it.isClick && it.confidence >= cfg.minConfidence }
        val out = ArrayList<ZoomEvent>()
        var i = 0
        while (i < cursor.size) {
            val a = cursor[i]
            var j = i
            var sx = a.x
            var sy = a.y
            while (j + 1 < cursor.size &&
                cursor[j + 1].timeMs - cursor[j].timeMs <= 600L &&
                hypot(cursor[j + 1].x - a.x, cursor[j + 1].y - a.y) <= cfg.dwellRadius
            ) {
                j++
                sx += cursor[j].x
                sy += cursor[j].y
            }
            val dur = cursor[j].timeMs - a.timeMs
            if (dur in cfg.dwellMinMs..cfg.dwellMaxMs) {
                val n = j - i + 1
                out += ZoomEvent(a.timeMs, sx / n, sy / n, 0.6f)
            }
            i = j + 1
        }
        return out
    }

    private fun eventsFromHotspots(hotspots: List<ActivityHotspot>, cfg: Config): List<ZoomEvent> {
        val out = ArrayList<ActivityHotspot>()
        for (h in hotspots.filter { it.strength >= cfg.hotspotMinStrength }.sortedBy { it.timeMs }) {
            val prev = out.lastOrNull()
            if (prev != null && h.timeMs - prev.timeMs < cfg.hotspotSpacingMs) {
                if (h.strength > prev.strength) out[out.lastIndex] = h
                continue
            }
            out += h
        }
        return out.map { ZoomEvent(it.timeMs, it.x, it.y, (it.strength * 0.5f).coerceIn(0.1f, 0.5f)) }
    }

    private fun clusterEvents(events: List<ZoomEvent>, cfg: Config): List<List<ZoomEvent>> {
        val clusters = ArrayList<List<ZoomEvent>>()
        var cur = ArrayList<ZoomEvent>()
        for (e in events) {
            if (cur.isEmpty()) { cur.add(e); continue }
            val gap = e.timeMs - cur.last().timeMs
            val startsBeforePreviousOutDone = e.timeMs - cfg.leadInMs < segmentEnd(cur, cfg)
            if (gap <= cfg.panMergeWindowMs || startsBeforePreviousOutDone) {
                cur.add(e)
            } else {
                clusters.add(cur)
                cur = arrayListOf(e)
            }
        }
        if (cur.isNotEmpty()) clusters.add(cur)
        return clusters
    }

    private fun holdMs(cluster: List<ZoomEvent>, cfg: Config): Long {
        val f = ((cluster.size - 1) / 3f).coerceIn(0f, 1f)
        return cfg.minHoldMs + ((cfg.maxHoldMs - cfg.minHoldMs) * f).toLong()
    }

    private fun segmentEnd(cluster: List<ZoomEvent>, cfg: Config): Long =
        cluster.last().timeMs + holdMs(cluster, cfg) + cfg.leadOutMs

    private fun buildKeyframe(
        cluster: List<ZoomEvent>, prevEnd: Long, durationMs: Long, cfg: Config
    ): ZoomKeyframe? {
        val start = max(prevEnd, cluster.first().timeMs - cfg.leadInMs).coerceAtLeast(0L)
        val end = min(durationMs, segmentEnd(cluster, cfg))
        if (end - start < MIN_SEGMENT_MS) return null

        val waypoints = ArrayList<FocusWaypoint>()
        for (e in cluster) {
            val lastWp = waypoints.lastOrNull()
            if (lastWp == null || hypot(e.x - lastWp.x, e.y - lastWp.y) >= cfg.minPanDistance) {
                waypoints += FocusWaypoint(e.timeMs, e.x, e.y)
            }
        }
        val avgWeight = cluster.map { it.weight }.average().toFloat().coerceIn(0f, 1f)
        val bonus = min(0.2f, 0.05f * (cluster.size - 1))
        val scale = (cfg.minScale + (cfg.baseScale - cfg.minScale) * avgWeight + bonus)
            .coerceIn(cfg.minScale, cfg.maxScale)
        val third = (end - start) / 3

        return ZoomKeyframe(
            startMs = start,
            endMs = end,
            zoomScale = scale,
            focusX = waypoints.first().x,
            focusY = waypoints.first().y,
            easeInMs = min(cfg.leadInMs, third),
            easeOutMs = min(cfg.leadOutMs, third),
            waypoints = waypoints,
            id = UUID.randomUUID().toString(),
            source = ZoomSource.MOUSE,
            note = "${cluster.size} action(s)"
        )
    }

    fun sanitize(input: List<ZoomKeyframe>, durationMs: Long, maxScale: Float = 3f): List<ZoomKeyframe> {
        val out = ArrayList<ZoomKeyframe>()
        var prevEnd = 0L
        for (k in input.sortedBy { it.startMs }) {
            if (!k.zoomScale.isFinite() || !k.focusX.isFinite() || !k.focusY.isFinite()) continue
            val start = max(k.startMs, prevEnd).coerceAtLeast(0L)
            val end = min(k.endMs, durationMs)
            if (end - start < MIN_SEGMENT_MS) continue
            val half = (end - start) / 2
            out += k.copy(
                startMs = start,
                endMs = end,
                zoomScale = k.zoomScale.coerceIn(1.2f, maxScale),
                focusX = k.focusX.coerceIn(0f, 1f),
                focusY = k.focusY.coerceIn(0f, 1f),
                easeInMs = k.easeInMs.coerceIn(100L, half),
                easeOutMs = k.easeOutMs.coerceIn(100L, half),
                waypoints = k.waypoints
                    .filter { it.timeMs in start..end }
                    .map { it.copy(x = it.x.coerceIn(0f, 1f), y = it.y.coerceIn(0f, 1f)) }
                    .sortedBy { it.timeMs }
            )
            prevEnd = end
        }
        return out
    }

    // ------------------------------------------------------------------------------------------
    // Evaluation (used per frame by preview and export)
    // ------------------------------------------------------------------------------------------

    fun evaluateView(originalTimeMs: Long, zoomKeyframes: List<ZoomKeyframe>): ZoomView {
        var kf: ZoomKeyframe? = null
        for (k in zoomKeyframes) {
            if (originalTimeMs >= k.startMs && originalTimeMs <= k.endMs) { kf = k; break }
        }
        val active = kf ?: return ZoomView(1f, 0.5f, 0.5f)

        val easeIn = active.easeInMs.coerceAtLeast(100L)
        val easeOut = active.easeOutMs.coerceAtLeast(100L)
        val tIn = ((originalTimeMs - active.startMs).toFloat() / easeIn).coerceIn(0f, 1f)
        val tOut = ((active.endMs - originalTimeMs).toFloat() / easeOut).coerceIn(0f, 1f)
        val envelope = min(easeInOutCubic(tIn), easeInOutCubic(tOut))

        val (panX, panY) = panFocusAt(active, originalTimeMs)
        val scale = lerp(1f, active.zoomScale, envelope)
        val fx = CameraMath.clampFocus(lerp(0.5f, panX, envelope), scale)
        val fy = CameraMath.clampFocus(lerp(0.5f, panY, envelope), scale)
        return ZoomView(scale, fx, fy)
    }

    fun evaluateCameraAt(
        originalTimeMs: Long,
        zoomKeyframes: List<ZoomKeyframe>,
        mouseSamples: List<MouseSample>
    ): CameraState {
        val view = evaluateView(originalTimeMs, zoomKeyframes)
        val cursor = interpolateCursor(originalTimeMs, mouseSamples)
        val ripple = computeRecentClickRipple(originalTimeMs, mouseSamples)
        return CameraState(view.scale, view.focusX, view.focusY, cursor?.first, cursor?.second, ripple)
    }

    private fun panFocusAt(kf: ZoomKeyframe, t: Long): Pair<Float, Float> {
        val w = kf.waypoints
        if (w.isEmpty()) return kf.focusX to kf.focusY
        for (i in w.lastIndex downTo 1) {
            val dur = min(PAN_DURATION_MS, w[i].timeMs - w[i - 1].timeMs).coerceAtLeast(1L)
            val start = w[i].timeMs - dur
            if (t >= start) {
                val p = easeInOutCubic(((t - start).toFloat() / dur).coerceIn(0f, 1f))
                return lerp(w[i - 1].x, w[i].x, p) to lerp(w[i - 1].y, w[i].y, p)
            }
        }
        return w[0].x to w[0].y
    }

    internal fun upperBound(samples: List<MouseSample>, t: Long): Int {
        var lo = 0
        var hi = samples.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (samples[mid].timeMs <= t) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun interpolateCursor(timeMs: Long, samples: List<MouseSample>): Pair<Float, Float>? {
        if (samples.isEmpty()) return null
        val idx = upperBound(samples, timeMs)
        val a = if (idx > 0) samples[idx - 1] else null
        val b = if (idx < samples.size) samples[idx] else null
        return when {
            a != null && b != null -> {
                val gap = b.timeMs - a.timeMs
                when {
                    gap <= 0L -> a.x to a.y
                    gap > 1500L -> null
                    else -> {
                        val f = (timeMs - a.timeMs).toFloat() / gap
                        lerp(a.x, b.x, f) to lerp(a.y, b.y, f)
                    }
                }
            }
            a != null -> if (timeMs - a.timeMs <= 1000L) a.x to a.y else null
            b != null -> if (b.timeMs - timeMs <= 300L) b.x to b.y else null
            else -> null
        }
    }

    fun computeRecentClickRipple(timeMs: Long, samples: List<MouseSample>): Float {
        var i = upperBound(samples, timeMs) - 1
        while (i >= 0 && timeMs - samples[i].timeMs <= RIPPLE_MS) {
            if (samples[i].isClick) {
                return ((timeMs - samples[i].timeMs).toFloat() / RIPPLE_MS).coerceIn(0.001f, 1f)
            }
            i--
        }
        return 0f
    }

    fun buildInitialSegmentsWithKeyboard(
        durationMs: Long,
        keyboardIntervals: List<TimeInterval>,
        mode: KeyboardTrimMode
    ): List<EditSegment> {
        if (durationMs <= 0L) return emptyList()
        if (keyboardIntervals.isEmpty() || mode == KeyboardTrimMode.KEEP) {
            return listOf(EditSegment(0L, durationMs, 1.0f, SegmentAction.KEEP, "Full recording"))
        }

        val sorted = keyboardIntervals
            .filter { it.endMs > it.startMs }
            .sortedBy { it.startMs }

        val result = mutableListOf<EditSegment>()
        var cursor = 0L
        for (kb in sorted) {
            val s = kb.startMs.coerceIn(0L, durationMs)
            val e = kb.endMs.coerceIn(0L, durationMs)
            if (e <= cursor) continue
            val clampedS = maxOf(s, cursor)
            if (clampedS > cursor) {
                result += EditSegment(cursor, clampedS, 1.0f, SegmentAction.KEEP, "Main content")
            }
            if (mode == KeyboardTrimMode.REMOVE) {
                result += EditSegment(clampedS, e, 1.0f, SegmentAction.TRIM, "Phone keyboard hidden")
            } else if (mode == KeyboardTrimMode.SPEED_UP_5X) {
                result += EditSegment(clampedS, e, 5.0f, SegmentAction.KEEP, "Phone keyboard 5x")
            }
            cursor = e
        }
        if (cursor < durationMs) {
            result += EditSegment(cursor, durationMs, 1.0f, SegmentAction.KEEP, "Main content")
        }
        return result
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else {
            val u = -2f * t + 2f
            1f - u * u * u / 2f
        }
}
