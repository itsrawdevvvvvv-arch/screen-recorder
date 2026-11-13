package com.recordly.mobile.model

import kotlinx.serialization.Serializable

@Serializable
enum class KeyboardTrimMode {
    REMOVE,
    SPEED_UP_5X,
    KEEP
}

@Serializable
enum class SegmentAction {
    KEEP,
    TRIM
}

@Serializable
enum class ZoomSource {
    MOUSE,
    AI,
    ACCESSIBILITY,
    MANUAL
}

@Serializable
enum class BackgroundPreset {
    MIDNIGHT_GRADIENT,
    ELECTRIC_VIOLET,
    EMERALD_SLATE,
    PURE_DARK
}

@Serializable
enum class AspectRatioPreset {
    ORIGINAL,
    PORTRAIT_9_16,
    LANDSCAPE_16_9,
    SQUARE_1_1
}

@Serializable
data class FocusWaypoint(
    val timeMs: Long,
    val x: Float,
    val y: Float
)

/** All coordinates are normalized [0..1], origin top-left, y down. */
@Serializable
data class MouseSample(
    val timeMs: Long,
    val x: Float,
    val y: Float,
    val isClick: Boolean = false,
    val confidence: Float = 1f,
    val source: Source = Source.VISUAL
) {
    @Serializable
    enum class Source { VISUAL, ACCESSIBILITY, FUSED, INFERRED }

    constructor(
        timestampMs: Long,
        xNorm: Float,
        yNorm: Float,
        isDown: Boolean = false,
        isUp: Boolean = false,
        button: Int = 0
    ) : this(
        timeMs = timestampMs,
        x = xNorm,
        y = yNorm,
        isClick = isDown,
        confidence = 1f,
        source = Source.VISUAL
    )

    // Compatibility accessors for UI and older callers
    val timestampMs: Long get() = timeMs
    val xNorm: Float get() = x
    val yNorm: Float get() = y
    val isDown: Boolean get() = isClick
    val isUp: Boolean get() = false
    val button: Int get() = if (isClick) 1 else 0
}

data class CameraState(
    val scale: Float,
    val focusX: Float,   // already clamped for [scale]
    val focusY: Float,   // already clamped for [scale]
    val cursorX: Float?,
    val cursorY: Float?,
    /** 0 = no ripple active, 0.01..1.0 = progress of the most recent click ripple. */
    val clickRippleProgress: Float
)

data class ActivityHotspot(
    val timeMs: Long,
    val x: Float,
    val y: Float,
    val strength: Float
)

data class AccessibilityClick(
    val timeMs: Long,
    val x: Float?,
    val y: Float?
)

data class TimestampedImage(
    val timestampMs: Long,
    val jpegBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TimestampedImage
        if (timestampMs != other.timestampMs) return false
        return jpegBytes.contentEquals(other.jpegBytes)
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + jpegBytes.contentHashCode()
        return result
    }
}

@Serializable
data class TimeInterval(
    val startMs: Long,
    val endMs: Long,
    val label: String = "Keyboard",
    val trim: Boolean = true
)

@Serializable
data class ZoomKeyframe(
    val startMs: Long,
    val endMs: Long,
    val zoomScale: Float = 2.0f,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val easeInMs: Long = 350L,
    val easeOutMs: Long = 400L,
    val waypoints: List<FocusWaypoint> = emptyList(),
    val id: String = java.util.UUID.randomUUID().toString(),
    val source: ZoomSource = ZoomSource.MOUSE,
    val note: String = ""
)

@Serializable
data class EditSegment(
    val startMs: Long,
    val endMs: Long,
    val speed: Float = 1.0f,
    val action: SegmentAction = SegmentAction.KEEP,
    val reason: String = ""
)

@Serializable
data class WordTimestamp(
    val word: String,
    val startMs: Long,
    val endMs: Long
)

@Serializable
data class SrtCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<WordTimestamp> = emptyList()
)

@Serializable
data class FrameInsight(
    val timestampMs: Long,
    val summary: String,
    val keyboardVisible: Boolean = false,
    val isIdleOrLoading: Boolean = false,
    val cursorHintX: Float? = null,
    val cursorHintY: Float? = null
)

@Serializable
data class AiEditPlan(
    val summary: String = "",
    val suggestedTitle: String = "",
    val segments: List<EditSegment> = emptyList(),
    val zoomKeyframes: List<ZoomKeyframe> = emptyList(),
    val keyboardIntervals: List<TimeInterval> = emptyList(),
    val remappedSrtCues: List<SrtCue> = emptyList(),
    val aiNotes: List<String> = emptyList()
)

@Serializable
data class CanvasStyleConfig(
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.ORIGINAL,
    val backgroundPreset: BackgroundPreset = BackgroundPreset.MIDNIGHT_GRADIENT,
    val paddingFraction: Float = 0.05f,
    val cornerRadiusPx: Float = 36f,
    val showCursorRing: Boolean = true,
    val burnSubtitles: Boolean = true,
    val defaultZoomScale: Float = 2.0f
)

@Serializable
data class GroqSettings(
    val apiKey: String = "",
    val whisperModel: String = "whisper-large-v3-turbo",
    val chatModel: String = "llama-3.3-70b-versatile",
    val visionModel: String = "qwen/qwen3.8-27b",
    val keyboardTrimMode: KeyboardTrimMode = KeyboardTrimMode.REMOVE,
    val autoZoomEnabled: Boolean = true,
    val autoAiSyncOnStop: Boolean = false,
    val defaultZoomScale: Float = 2.0f,
    val recordInternalAudio: Boolean = true,
    val recordMicrophone: Boolean = true
)

@Serializable
data class RecordingProject(
    val id: String,
    val title: String,
    val createdAtMs: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoPath: String,
    val externalAudioPath: String? = null,
    val srtPath: String? = null,
    val exportedVideoPath: String? = null,
    val mouseSamples: List<MouseSample> = emptyList(),
    val keyboardIntervals: List<TimeInterval> = emptyList(),
    val srtCues: List<SrtCue> = emptyList(),
    val frameInsights: List<FrameInsight> = emptyList(),
    val editPlan: AiEditPlan? = null,
    val styleConfig: CanvasStyleConfig = CanvasStyleConfig()
)
