package com.recordly.mobile.editor

import com.recordly.mobile.model.EditSegment
import com.recordly.mobile.model.KeyboardTrimMode
import com.recordly.mobile.model.MouseSample
import com.recordly.mobile.model.SegmentAction
import com.recordly.mobile.model.TimeInterval
import com.recordly.mobile.model.ZoomKeyframe
import com.recordly.mobile.model.ZoomSource
import java.util.UUID
import kotlin.math.abs

data class CameraState(
    val scale: Float,
    val focusX: Float,
    val focusY: Float,
    val cursorX: Float?,
    val cursorY: Float?,
    val clickRippleProgress: Float // 0..1 when recently clicked, -1f otherwise
)

object AutoZoomGenerator {

    /**
     * Groups mouse clicks and sustained cursor activity into smooth Recordly-style zoom keyframes.
     */
    fun generateFromMouse(
        samples: List<MouseSample>,
        totalDurationMs: Long,
        defaultZoom: Float = 2.0f
    ): List<ZoomKeyframe> {
        if (samples.isEmpty() || totalDurationMs <= 0L) return emptyList()
        val clicks = samples.filter { it.isDown }.sortedBy { it.timestampMs }
        if (clicks.isEmpty()) return emptyList()

        val keyframes = mutableListOf<ZoomKeyframe>()
        var clusterStart = clicks.first().timestampMs
        var clusterEnd = clicks.first().timestampMs
        var sumX = clicks.first().xNorm
        var sumY = clicks.first().yNorm
        var count = 1

        fun commitCluster() {
            val zStart = (clusterStart - 350L).coerceAtLeast(0L)
            val zEnd = (clusterEnd + 1100L).coerceAtMost(totalDurationMs)
            if (zEnd - zStart >= 600L) {
                keyframes += ZoomKeyframe(
                    id = UUID.randomUUID().toString(),
                    startMs = zStart,
                    endMs = zEnd,
                    focusX = (sumX / count).coerceIn(0.08f, 0.92f),
                    focusY = (sumY / count).coerceIn(0.08f, 0.92f),
                    zoomScale = defaultZoom.coerceIn(1.3f, 2.8f),
                    source = ZoomSource.MOUSE,
                    note = "$count mouse click(s)"
                )
            }
        }

        for (click in clicks.drop(1)) {
            val avgX = sumX / count
            val avgY = sumY / count
            val closeInTime = (click.timestampMs - clusterEnd) <= 1800L
            val closeInSpace = abs(click.xNorm - avgX) <= 0.25f && abs(click.yNorm - avgY) <= 0.25f

            if (closeInTime && closeInSpace) {
                clusterEnd = click.timestampMs
                sumX += click.xNorm
                sumY += click.yNorm
                count++
            } else {
                commitCluster()
                clusterStart = click.timestampMs
                clusterEnd = click.timestampMs
                sumX = click.xNorm
                sumY = click.yNorm
                count = 1
            }
        }
        commitCluster()
        return keyframes
    }

    /**
     * Splits [0..durationMs] around detected keyboard intervals so keyboard segments are
     * immediately trimmed or sped up according to user preference.
     */
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

    /**
     * Evaluates smooth eased zoom/pan camera and cursor state at any timestamp in the original recording.
     */
    fun evaluateCameraAt(
        originalTimeMs: Long,
        zoomKeyframes: List<ZoomKeyframe>,
        mouseSamples: List<MouseSample>
    ): CameraState {
        val cursorSample = interpolateCursor(originalTimeMs, mouseSamples)
        val ripple = computeRecentClickRipple(originalTimeMs, mouseSamples)

        val activeZoom = zoomKeyframes.firstOrNull { originalTimeMs in it.startMs..it.endMs }
            ?: return CameraState(
                scale = 1.0f,
                focusX = 0.5f,
                focusY = 0.5f,
                cursorX = cursorSample?.first,
                cursorY = cursorSample?.second,
                clickRippleProgress = ripple
            )

        val tIn = ((originalTimeMs - activeZoom.startMs).toFloat() / activeZoom.easeInMs.coerceAtLeast(100L)).coerceIn(0f, 1f)
        val tOut = ((activeZoom.endMs - originalTimeMs).toFloat() / activeZoom.easeOutMs.coerceAtLeast(100L)).coerceIn(0f, 1f)
        val envelope = minOf(easeInOutCubic(tIn), easeInOutCubic(tOut))

        // Blend keyframe focal point with live cursor tracking when a mouse is present
        val rawFocusX = if (cursorSample != null && activeZoom.source == ZoomSource.MOUSE) {
            lerp(activeZoom.focusX, cursorSample.first, 0.55f)
        } else activeZoom.focusX
        val rawFocusY = if (cursorSample != null && activeZoom.source == ZoomSource.MOUSE) {
            lerp(activeZoom.focusY, cursorSample.second, 0.55f)
        } else activeZoom.focusY

        val scale = lerp(1.0f, activeZoom.zoomScale, envelope)
        val focusX = lerp(0.5f, rawFocusX, envelope)
        val focusY = lerp(0.5f, rawFocusY, envelope)

        return CameraState(
            scale = scale,
            focusX = focusX,
            focusY = focusY,
            cursorX = cursorSample?.first,
            cursorY = cursorSample?.second,
            clickRippleProgress = ripple
        )
    }

    private fun interpolateCursor(timeMs: Long, samples: List<MouseSample>): Pair<Float, Float>? {
        if (samples.isEmpty()) return null
        val idx = samples.binarySearch { it.timestampMs.compareTo(timeMs) }
        if (idx >= 0) return samples[idx].xNorm to samples[idx].yNorm
        val insertion = -idx - 1
        if (insertion == 0) return samples.first().xNorm to samples.first().yNorm
        if (insertion >= samples.size) return samples.last().xNorm to samples.last().yNorm
        val a = samples[insertion - 1]
        val b = samples[insertion]
        val span = (b.timestampMs - a.timestampMs).coerceAtLeast(1L).toFloat()
        val t = ((timeMs - a.timestampMs).toFloat() / span).coerceIn(0f, 1f)
        return lerp(a.xNorm, b.xNorm, t) to lerp(a.yNorm, b.yNorm, t)
    }

    private fun computeRecentClickRipple(timeMs: Long, samples: List<MouseSample>): Float {
        val recentClick = samples.lastOrNull { it.isDown && it.timestampMs <= timeMs && (timeMs - it.timestampMs) <= 450L }
            ?: return -1f
        return ((timeMs - recentClick.timestampMs).toFloat() / 450f).coerceIn(0f, 1f)
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) 4f * t * t * t else 1f - ((-2f * t + 2f).let { it * it * it }) / 2f
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
