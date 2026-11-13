package com.recordly.mobile.editor

import android.content.Context
import android.net.Uri
import com.recordly.mobile.ai.VisualAnalyzer
import com.recordly.mobile.capture.ClickLog
import com.recordly.mobile.model.AccessibilityClick
import com.recordly.mobile.model.MouseSample
import com.recordly.mobile.model.ZoomKeyframe
import kotlinx.coroutines.CancellationException

object ZoomPipeline {

    data class Result(
        val mouseSamples: List<MouseSample>,
        val zoomKeyframes: List<ZoomKeyframe>,
        val cursorReliable: Boolean,
        val durationMs: Long
    )

    /**
     * @param refine optional Groq step: send the compact keyframe JSON (+ maybe event summary), parse the
     *               reply into keyframes. Output is always re-validated by [AutoZoomGenerator.sanitize];
     *               on any failure the deterministic keyframes are kept.
     */
    suspend fun run(
        context: Context,
        videoUri: Uri,
        accessibilityClicks: List<AccessibilityClick> = ClickLog.end(),
        clockOffsetMs: Long = 0L,
        refine: (suspend (List<ZoomKeyframe>, Long) -> List<ZoomKeyframe>)? = null,
        onProgress: (Float) -> Unit = {}
    ): Result {
        val analyzer = VisualAnalyzer()
        val analysis = analyzer.analyze(context, videoUri, onProgress)
        val samples = analyzer.fuse(analysis, accessibilityClicks, clockOffsetMs)
        val base = AutoZoomGenerator.generate(
            samples = samples,
            hotspots = analysis.hotspots,
            durationMs = analysis.durationMs,
            cursorReliable = analysis.cursorReliable
        )
        val keyframes = if (refine != null && base.isNotEmpty()) {
            try {
                AutoZoomGenerator.sanitize(refine(base, analysis.durationMs), analysis.durationMs).ifEmpty { base }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                base
            }
        } else base
        return Result(samples, keyframes, analysis.cursorReliable, analysis.durationMs)
    }
}
