package com.recordly.mobile.editor

import com.recordly.mobile.model.CameraState
import com.recordly.mobile.model.EditSegment
import com.recordly.mobile.model.FocusWaypoint
import com.recordly.mobile.model.SegmentAction
import com.recordly.mobile.model.ZoomKeyframe
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
    private const val MIN_SEGMENT_MS = 500L

    fun evaluateView(originalTimeMs: Long, zoomKeyframes: List<ZoomKeyframe>): ZoomView {
        var kf: ZoomKeyframe? = null
        for (k in zoomKeyframes) {
            if (originalTimeMs >= k.startMs && originalTimeMs <= k.endMs) { kf = k; break }
        }
        val active = kf ?: return ZoomView(1f, 0.5f, 0.5f)

        val easeIn = active.easeInMs.coerceAtLeast(80L)
        val easeOut = active.easeOutMs.coerceAtLeast(80L)
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
        zoomKeyframes: List<ZoomKeyframe>
    ): CameraState {
        val view = evaluateView(originalTimeMs, zoomKeyframes)
        return CameraState(view.scale, view.focusX, view.focusY)
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

    fun sanitize(input: List<ZoomKeyframe>, durationMs: Long, maxScale: Float = 3.5f): List<ZoomKeyframe> {
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
                focusX = k.focusX.coerceIn(0.05f, 0.95f),
                focusY = k.focusY.coerceIn(0.05f, 0.95f),
                easeInMs = k.easeInMs.coerceIn(80L, half),
                easeOutMs = k.easeOutMs.coerceIn(80L, half)
            )
            prevEnd = end
        }
        return out
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else {
            val u = -2f * t + 2f
            1f - u * u * u / 2f
        }
}
