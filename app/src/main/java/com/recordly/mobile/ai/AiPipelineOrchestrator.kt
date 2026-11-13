package com.recordly.mobile.ai

import android.content.Context
import android.net.Uri
import com.recordly.mobile.data.ProjectRepository
import com.recordly.mobile.data.SettingsRepository
import com.recordly.mobile.data.SrtCodec
import com.recordly.mobile.editor.AutoZoomGenerator
import com.recordly.mobile.model.RecordingProject
import com.recordly.mobile.model.SrtCue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class AiPipelineProgress(
    val running: Boolean = false,
    val stepTitle: String = "",
    val progress: Float = 0f,
    val error: String? = null
)

class AiPipelineOrchestrator(
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val groqClient: GroqApiClient = GroqApiClient()
) {
    private val _progress = MutableStateFlow(AiPipelineProgress())
    val progress: StateFlow<AiPipelineProgress> = _progress.asStateFlow()

    suspend fun runFullGroqSync(projectId: String): Result<RecordingProject> = runCatching {
        val project = projectRepository.getProject(projectId) ?: error("Project not found")
        val settings = settingsRepository.getGroqSettings()
        require(settings.apiKey.isNotBlank()) { "Please enter your Groq API key in Settings first." }

        val dir = projectRepository.getProjectDir(projectId)
        val audioSourcePath = project.externalAudioPath ?: project.videoPath

        // Step 1: Audio extraction & silence detection
        _progress.value = AiPipelineProgress(true, "Extracting audio & scanning pauses…", 0.15f)
        val extractedM4a = AudioExtractor.extractAudioToM4a(audioSourcePath, File(dir, "audio_extract.m4a"))
        val silenceIntervals = AudioExtractor.detectSilenceIntervals(audioSourcePath)

        // Step 2: Groq Whisper -> .srt (skip if user already imported a custom .srt)
        var srtCues: List<SrtCue> = project.srtCues
        var srtPath = project.srtPath
        if (srtCues.isEmpty() && extractedM4a != null) {
            _progress.value = AiPipelineProgress(true, "Groq Whisper generating .srt & word timings…", 0.35f)
            val transcription = groqClient.transcribeAudio(
                apiKey = settings.apiKey,
                model = settings.whisperModel,
                audioFile = extractedM4a
            )
            srtCues = transcription.cues
            val srtFile = File(dir, "subtitles.srt").apply {
                writeText(SrtCodec.serialize(srtCues))
            }
            srtPath = srtFile.absolutePath
        }

        // Step 3: Post-recording visible cursor tracking + Groq Vision keyframe inspection
        _progress.value = AiPipelineProgress(true, "Tracking visible cursor & scanning keyframes…", 0.55f)
        val analyzer = VisualAnalyzer()
        val videoUri = Uri.fromFile(File(project.videoPath))
        val analysis = analyzer.analyze(
            context = context,
            videoUri = videoUri,
            onProgress = { p ->
                _progress.value = AiPipelineProgress(true, "Analyzing video frames for cursor…", 0.55f + p * 0.12f)
            }
        )
        val localCursorSamples = analyzer.fuse(analysis, emptyList())
        val keyframes = analyzer.extractSmartKeyframes(
            videoPath = project.videoPath,
            durationMs = project.durationMs,
            mouseSamples = localCursorSamples.ifEmpty { project.mouseSamples }
        )
        _progress.value = AiPipelineProgress(true, "Groq Vision locating cursor, clicks & keyboard…", 0.68f)
        val visionInsights = groqClient.analyzeKeyframesWithVision(
            apiKey = settings.apiKey,
            visionModel = settings.visionModel,
            frames = keyframes
        )

        val mergedKeyboardIntervals = analyzer.mergeKeyboardIntervals(
            accessibilityIntervals = project.keyboardIntervals,
            visionInsights = visionInsights,
            totalDurationMs = project.durationMs
        )

        val effectiveMouseSamples = localCursorSamples.ifEmpty { project.mouseSamples }

        // Step 4: Groq LLM AI Director builds audio-synced trim, speed & zoom plan
        _progress.value = AiPipelineProgress(true, "Groq AI Director syncing speed, trims & zooms…", 0.85f)
        val aiPlan = groqClient.generateAiEditPlan(
            settings = settings,
            durationMs = project.durationMs,
            srtCues = srtCues,
            silenceIntervals = silenceIntervals,
            keyboardIntervals = mergedKeyboardIntervals,
            mouseSamples = effectiveMouseSamples,
            frameInsights = visionInsights
        )

        // Blend auto zoom clusters if AI returned sparse zooms
        val combinedZooms = if (aiPlan.zoomKeyframes.isEmpty() && (effectiveMouseSamples.isNotEmpty() || analysis.hotspots.isNotEmpty())) {
            AutoZoomGenerator.generate(
                samples = effectiveMouseSamples,
                hotspots = analysis.hotspots,
                durationMs = project.durationMs,
                cursorReliable = analysis.cursorReliable
            )
        } else {
            AutoZoomGenerator.sanitize(aiPlan.zoomKeyframes, project.durationMs)
        }

        val finalPlan = aiPlan.copy(zoomKeyframes = combinedZooms)
        val updatedProject = project.copy(
            title = aiPlan.suggestedTitle.ifBlank { project.title },
            srtPath = srtPath,
            srtCues = srtCues,
            mouseSamples = effectiveMouseSamples,
            keyboardIntervals = mergedKeyboardIntervals,
            frameInsights = visionInsights,
            editPlan = finalPlan
        )
        projectRepository.saveProject(updatedProject)
        _progress.value = AiPipelineProgress(false, "AI Sync Complete!", 1.0f)
        updatedProject
    }.onFailure { err ->
        _progress.value = AiPipelineProgress(false, "AI Sync failed", 0f, err.message ?: "Unknown error")
    }

    suspend fun refineWithPrompt(projectId: String, prompt: String): Result<RecordingProject> = runCatching {
        val project = projectRepository.getProject(projectId) ?: error("Project not found")
        val settings = settingsRepository.getGroqSettings()
        val currentPlan = project.editPlan ?: error("Run AI Sync first or create a plan.")

        _progress.value = AiPipelineProgress(true, "Groq AI updating timeline…", 0.5f)
        val updatedPlan = groqClient.refineEditPlanWithPrompt(
            settings = settings,
            durationMs = project.durationMs,
            currentPlan = currentPlan,
            srtCues = project.srtCues,
            userInstruction = prompt
        )
        val updated = project.copy(
            title = updatedPlan.suggestedTitle.ifBlank { project.title },
            editPlan = updatedPlan
        )
        projectRepository.saveProject(updated)
        _progress.value = AiPipelineProgress(false, "Updated!", 1.0f)
        updated
    }.onFailure { err ->
        _progress.value = AiPipelineProgress(false, "Update failed", 0f, err.message ?: "Unknown error")
    }

    fun clearError() {
        _progress.value = _progress.value.copy(error = null)
    }
}
