package com.recordly.mobile.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recordly.mobile.RecordlyApp
import com.recordly.mobile.ai.AiPipelineOrchestrator
import com.recordly.mobile.ai.GroqApiClient
import com.recordly.mobile.capture.ActiveSessionTracker
import com.recordly.mobile.capture.RecordlyAccessibilityService
import com.recordly.mobile.data.SrtCodec
import com.recordly.mobile.editor.VideoExportEngine
import com.recordly.mobile.model.AiEditPlan
import com.recordly.mobile.model.EditSegment
import com.recordly.mobile.model.GroqSettings
import com.recordly.mobile.model.RecordingProject
import com.recordly.mobile.model.SegmentAction
import com.recordly.mobile.model.ZoomKeyframe
import com.recordly.mobile.model.ZoomSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ExportUiState(
    val exporting: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    val exportedUri: Uri? = null,
    val error: String? = null
)

class RecordlyViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RecordlyApp
    private val projectRepo = app.projectRepository
    private val settingsRepo = app.settingsRepository
    private val groqClient = GroqApiClient()
    private val orchestrator = AiPipelineOrchestrator(application, projectRepo, settingsRepo, groqClient)
    private val exportEngine = VideoExportEngine(application)

    val projects = projectRepo.projects
    val groqSettings = settingsRepo.groqSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GroqSettings())

    val connectedMice = app.mouseDeviceMonitor.connectedMice
    val isRecording = ActiveSessionTracker.isRecording
    val recordingElapsedMs = ActiveSessionTracker.elapsedMs
    val lastSavedProjectId = ActiveSessionTracker.lastSavedProjectId

    val aiProgress = orchestrator.progress

    private val _accessibilityEnabled = MutableStateFlow(false)
    val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

    private val _activeProject = MutableStateFlow<RecordingProject?>(null)
    val activeProject: StateFlow<RecordingProject?> = _activeProject.asStateFlow()

    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _groqTestResult = MutableStateFlow<String?>(null)
    val groqTestResult: StateFlow<String?> = _groqTestResult.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepo.reloadProjects()
        }
        refreshHardwareAndPermissions()
    }

    fun refreshHardwareAndPermissions() {
        app.mouseDeviceMonitor.refresh()
        _accessibilityEnabled.value = RecordlyAccessibilityService.isEnabled(app)
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _activeProject.value = projectRepo.getProject(projectId)
        }
    }

    fun importVideo(uri: Uri, onLoaded: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { projectRepo.importVideoFromUri(uri) }
                .onSuccess { proj ->
                    _activeProject.value = proj
                    onLoaded(proj.id)
                }
                .onFailure { _toastMessage.value = "Import failed: ${it.message}" }
        }
    }

    fun importExternalAudio(projectId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching { projectRepo.importExternalAudio(projectId, uri) }
                .onSuccess { updated ->
                    _activeProject.value = updated
                    _toastMessage.value = "Voiceover audio linked. Run Groq AI Sync to align!"
                }
                .onFailure { _toastMessage.value = "Audio import failed: ${it.message}" }
        }
    }

    fun importSrt(projectId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching { projectRepo.importSrtFile(projectId, uri) }
                .onSuccess { updated ->
                    _activeProject.value = updated
                    _toastMessage.value = "Loaded ${updated?.srtCues?.size ?: 0} .srt cues"
                }
                .onFailure { _toastMessage.value = "SRT import failed: ${it.message}" }
        }
    }

    fun runGroqMagicSync(projectId: String) {
        viewModelScope.launch {
            orchestrator.runFullGroqSync(projectId)
                .onSuccess { updated ->
                    _activeProject.value = updated
                    _toastMessage.value = "Groq AI synced trims, speeds, zooms & .srt!"
                }
                .onFailure { err ->
                    _toastMessage.value = err.message ?: "Groq AI sync failed"
                }
        }
    }

    fun refineWithPrompt(projectId: String, prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            orchestrator.refineWithPrompt(projectId, prompt.trim())
                .onSuccess { updated ->
                    _activeProject.value = updated
                    _toastMessage.value = "AI updated the timeline!"
                }
                .onFailure { err ->
                    _toastMessage.value = err.message ?: "Prompt failed"
                }
        }
    }

    fun toggleSegmentTrim(segmentIndex: Int) {
        val proj = _activeProject.value ?: return
        val plan = ensurePlan(proj)
        val updatedSegments = plan.segments.mapIndexed { idx, seg ->
            if (idx == segmentIndex) {
                val nextAction = if (seg.action == SegmentAction.KEEP) SegmentAction.TRIM else SegmentAction.KEEP
                seg.copy(action = nextAction)
            } else seg
        }
        saveUpdatedPlan(proj, plan.copy(
            segments = updatedSegments,
            remappedSrtCues = SrtCodec.remapCuesToEditedTimeline(proj.srtCues, updatedSegments)
        ))
    }

    fun cycleSegmentSpeed(segmentIndex: Int) {
        val proj = _activeProject.value ?: return
        val plan = ensurePlan(proj)
        val speeds = listOf(0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 5.0f)
        val updatedSegments = plan.segments.mapIndexed { idx, seg ->
            if (idx == segmentIndex) {
                val curIdx = speeds.indexOfFirst { kotlin.math.abs(it - seg.speed) < 0.05f }
                val nextSpeed = speeds[(curIdx + 1) % speeds.size]
                seg.copy(speed = nextSpeed, action = SegmentAction.KEEP)
            } else seg
        }
        saveUpdatedPlan(proj, plan.copy(
            segments = updatedSegments,
            remappedSrtCues = SrtCodec.remapCuesToEditedTimeline(proj.srtCues, updatedSegments)
        ))
    }

    fun addManualZoomAt(timestampMs: Long, focusX: Float = 0.5f, focusY: Float = 0.5f) {
        val proj = _activeProject.value ?: return
        val plan = ensurePlan(proj)
        val start = (timestampMs - 300L).coerceAtLeast(0L)
        val end = (timestampMs + 1500L).coerceAtMost(proj.durationMs)
        val newKf = ZoomKeyframe(
            id = UUID.randomUUID().toString(),
            startMs = start,
            endMs = end,
            focusX = focusX.coerceIn(0.1f, 0.9f),
            focusY = focusY.coerceIn(0.1f, 0.9f),
            zoomScale = proj.styleConfig.defaultZoomScale,
            source = ZoomSource.MANUAL,
            note = "Manual zoom"
        )
        saveUpdatedPlan(
            proj,
            plan.copy(zoomKeyframes = (plan.zoomKeyframes + newKf).sortedBy { it.startMs })
        )
    }

    fun removeZoomKeyframe(id: String) {
        val proj = _activeProject.value ?: return
        val plan = ensurePlan(proj)
        saveUpdatedPlan(proj, plan.copy(zoomKeyframes = plan.zoomKeyframes.filterNot { it.id == id }))
    }

    fun toggleBurnSubtitles() {
        val proj = _activeProject.value ?: return
        val updated = proj.copy(styleConfig = proj.styleConfig.copy(burnSubtitles = !proj.styleConfig.burnSubtitles))
        viewModelScope.launch {
            projectRepo.saveProject(updated)
            _activeProject.value = updated
        }
    }

    fun toggleShowCursor() {
        val proj = _activeProject.value ?: return
        val updated = proj.copy(styleConfig = proj.styleConfig.copy(showCursorRing = !proj.styleConfig.showCursorRing))
        viewModelScope.launch {
            projectRepo.saveProject(updated)
            _activeProject.value = updated
        }
    }

    fun exportProjectMp4() {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            _exportState.value = ExportUiState(exporting = true, progress = 0.02f, message = "Rendering with Media3…")
            val outFile = File(projectRepo.getProjectDir(proj.id), "export.mp4")
            runCatching {
                exportEngine.exportProject(proj, outFile) { p ->
                    _exportState.value = _exportState.value.copy(progress = p)
                }
                val safeTitle = proj.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "Recordly" }
                val galleryUri = projectRepo.exportVideoToGallery(outFile, safeTitle)
                val updated = proj.copy(exportedVideoPath = outFile.absolutePath)
                projectRepo.saveProject(updated)
                _activeProject.value = updated
                galleryUri
            }.onSuccess { uri ->
                _exportState.value = ExportUiState(
                    exporting = false,
                    progress = 1f,
                    message = "Saved to Movies/Recordly!",
                    exportedUri = uri
                )
            }.onFailure { err ->
                _exportState.value = ExportUiState(
                    exporting = false,
                    error = err.message ?: "Export failed"
                )
            }
        }
    }

    fun exportSrtFile() {
        val proj = _activeProject.value ?: return
        val cues = proj.editPlan?.remappedSrtCues?.takeIf { it.isNotEmpty() } ?: proj.srtCues
        if (cues.isEmpty()) {
            _toastMessage.value = "No subtitles yet. Run Groq AI Sync first."
            return
        }
        viewModelScope.launch {
            val safeTitle = proj.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "Recordly" }
            val uri = projectRepo.exportSrtToDownloads(cues, safeTitle)
            _toastMessage.value = if (uri != null) "Saved .srt to Downloads/Recordly" else "Failed to save .srt"
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepo.deleteProject(projectId)
            if (_activeProject.value?.id == projectId) _activeProject.value = null
        }
    }

    fun saveGroqSettings(updated: GroqSettings) {
        viewModelScope.launch {
            settingsRepo.updateGroqSettings(updated)
            _toastMessage.value = "Settings saved"
        }
    }

    fun testGroqConnection(apiKey: String) {
        viewModelScope.launch {
            _groqTestResult.value = "Testing Groq API…"
            groqClient.testConnection(apiKey)
                .onSuccess { count -> _groqTestResult.value = "Connected! $count Groq models available." }
                .onFailure { err -> _groqTestResult.value = "Failed: ${err.message}" }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun consumeLastSavedProject() {
        ActiveSessionTracker.consumeLastSavedProject()
    }

    fun clearExportStatus() {
        _exportState.value = ExportUiState()
    }

    private fun ensurePlan(project: RecordingProject): AiEditPlan {
        return project.editPlan ?: AiEditPlan(
            segments = listOf(EditSegment(0L, project.durationMs, 1.0f, SegmentAction.KEEP, "Full video"))
        )
    }

    private fun saveUpdatedPlan(project: RecordingProject, plan: AiEditPlan) {
        val updated = project.copy(editPlan = plan)
        _activeProject.value = updated
        viewModelScope.launch {
            projectRepo.saveProject(updated)
        }
    }
}
