package com.recordly.mobile.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recordly.mobile.RecordlyApp
import com.recordly.mobile.capture.ActiveSessionTracker
import com.recordly.mobile.editor.ContactSheetEngine
import com.recordly.mobile.editor.VideoExportEngine
import com.recordly.mobile.model.AppSettings
import com.recordly.mobile.model.EditSegment
import com.recordly.mobile.model.RecordingProject
import com.recordly.mobile.model.SegmentAction
import com.recordly.mobile.model.ZoomKeyframe
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
    private val exportEngine = VideoExportEngine(application)

    val projects = projectRepo.projects
    val appSettings = settingsRepo.appSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val isRecording = ActiveSessionTracker.isRecording
    val recordingElapsedMs = ActiveSessionTracker.elapsedMs
    val lastSavedProjectId = ActiveSessionTracker.lastSavedProjectId

    private val _activeProject = MutableStateFlow<RecordingProject?>(null)
    val activeProject: StateFlow<RecordingProject?> = _activeProject.asStateFlow()

    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _isGeneratingSheet = MutableStateFlow(false)
    val isGeneratingSheet: StateFlow<Boolean> = _isGeneratingSheet.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepo.reloadProjects()
        }
    }

    fun toggleFloatingMenu() {
        viewModelScope.launch {
            settingsRepo.toggleFloatingMenu()
        }
    }

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsRepo.updateAppSettings(settings)
            _toastMessage.value = "Settings updated"
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val proj = projectRepo.getProject(projectId)
            _activeProject.value = proj
            if (proj != null && proj.keyframes.isEmpty()) {
                regenerateContactSheet(projectId)
            }
        }
    }

    fun importVideo(uri: Uri, onLoaded: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { projectRepo.importVideoFromUri(uri) }
                .onSuccess { proj ->
                    _activeProject.value = proj
                    onLoaded(proj.id)
                    regenerateContactSheet(proj.id)
                }
                .onFailure { _toastMessage.value = "Import failed: ${it.message}" }
        }
    }

    fun regenerateContactSheet(projectId: String) {
        viewModelScope.launch {
            val proj = projectRepo.getProject(projectId) ?: return@launch
            _isGeneratingSheet.value = true
            val settings = settingsRepo.getAppSettings()
            val dir = projectRepo.getProjectDir(projectId)

            runCatching {
                ContactSheetEngine.generateContactSheet(
                    context = app,
                    project = proj,
                    projectDir = dir,
                    intervalSec = settings.contactSheetIntervalSec,
                    maxFramesLimit = settings.maxKeyframes
                )
            }.onSuccess { result ->
                val updated = proj.copy(
                    contactSheetPdfPath = result.pdfPath,
                    contactSheetZipPath = result.zipPath,
                    keyframes = result.frames
                )
                projectRepo.saveProject(updated)
                _activeProject.value = updated
                _toastMessage.value = "Contact sheet created (${result.frames.size} frames)"
            }.onFailure { err ->
                _toastMessage.value = "Failed to create contact sheet: ${err.message}"
            }
            _isGeneratingSheet.value = false
        }
    }

    fun applyClaudeAiResponse(projectId: String, pastedResponse: String) {
        if (pastedResponse.isBlank()) return
        viewModelScope.launch {
            val proj = projectRepo.getProject(projectId) ?: return@launch
            ContactSheetEngine.parseAiResponse(pastedResponse, proj.durationMs)
                .onSuccess { plan ->
                    val updated = proj.copy(
                        title = plan.suggestedTitle.ifBlank { proj.title },
                        editPlan = plan
                    )
                    projectRepo.saveProject(updated)
                    _activeProject.value = updated
                    _toastMessage.value = "Applied AI plan: ${plan.segments.size} cuts, ${plan.zoomKeyframes.size} zooms!"
                }
                .onFailure { err ->
                    _toastMessage.value = "Could not parse AI response: ${err.message}"
                }
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

    fun exportPdfToDownloads() {
        val proj = _activeProject.value ?: return
        val path = proj.contactSheetPdfPath ?: return
        val file = File(path)
        if (!file.exists()) return

        viewModelScope.launch {
            val safeTitle = "${proj.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)}_contact_sheet.pdf"
            val uri = projectRepo.exportFileToDownloads(file, safeTitle, "application/pdf")
            _toastMessage.value = if (uri != null) "Saved PDF to Downloads/Recordly" else "Export failed"
        }
    }

    fun exportZipToDownloads() {
        val proj = _activeProject.value ?: return
        val path = proj.contactSheetZipPath ?: return
        val file = File(path)
        if (!file.exists()) return

        viewModelScope.launch {
            val safeTitle = "${proj.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)}_bundle.zip"
            val uri = projectRepo.exportFileToDownloads(file, safeTitle, "application/zip")
            _toastMessage.value = if (uri != null) "Saved ZIP to Downloads/Recordly" else "Export failed"
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepo.deleteProject(projectId)
            if (_activeProject.value?.id == projectId) _activeProject.value = null
            _toastMessage.value = "Project deleted"
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
}
