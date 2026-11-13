package com.recordly.mobile.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class SegmentAction {
    KEEP,
    TRIM
}

@Serializable
data class FocusWaypoint(
    val timeMs: Long,
    val x: Float,
    val y: Float
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
    val id: String = UUID.randomUUID().toString(),
    val note: String = ""
)

data class CameraState(
    val scale: Float,
    val focusX: Float,
    val focusY: Float
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
data class ContactSheetFrame(
    val index: Int,
    val timestampMs: Long,
    val formattedTime: String,
    val imagePath: String
)

@Serializable
data class AiEditPlan(
    val suggestedTitle: String = "",
    val summary: String = "",
    val segments: List<EditSegment> = emptyList(),
    val zoomKeyframes: List<ZoomKeyframe> = emptyList()
)

@Serializable
data class AppSettings(
    val showFloatingMenu: Boolean = true,
    val contactSheetIntervalSec: Float = 1.5f,
    val maxKeyframes: Int = 24,
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
    val contactSheetPdfPath: String? = null,
    val contactSheetZipPath: String? = null,
    val exportedVideoPath: String? = null,
    val keyframes: List<ContactSheetFrame> = emptyList(),
    val editPlan: AiEditPlan? = null
)
