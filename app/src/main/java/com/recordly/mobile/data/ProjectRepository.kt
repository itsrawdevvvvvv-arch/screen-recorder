package com.recordly.mobile.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.recordly.mobile.model.RecordingProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ProjectRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val projectsRoot = File(context.filesDir, "projects").apply { mkdirs() }

    private val _projects = MutableStateFlow<List<RecordingProject>>(emptyList())
    val projects: StateFlow<List<RecordingProject>> = _projects.asStateFlow()

    suspend fun reloadProjects() = withContext(Dispatchers.IO) {
        val loaded = projectsRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val meta = File(dir, "project.json")
                if (!meta.exists()) return@mapNotNull null
                runCatching { json.decodeFromString<RecordingProject>(meta.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.createdAtMs }
            ?: emptyList()
        _projects.value = loaded
    }

    fun allocateProjectDir(projectId: String = UUID.randomUUID().toString()): Pair<String, File> {
        val dir = File(projectsRoot, projectId).apply { mkdirs() }
        return projectId to dir
    }

    fun getProjectDir(projectId: String): File = File(projectsRoot, projectId).apply { mkdirs() }

    suspend fun saveProject(project: RecordingProject) = withContext(Dispatchers.IO) {
        val dir = getProjectDir(project.id)
        File(dir, "project.json").writeText(json.encodeToString(project))
        reloadProjects()
    }

    suspend fun getProject(projectId: String): RecordingProject? = withContext(Dispatchers.IO) {
        val meta = File(getProjectDir(projectId), "project.json")
        if (!meta.exists()) return@withContext null
        runCatching { json.decodeFromString<RecordingProject>(meta.readText()) }.getOrNull()
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        getProjectDir(projectId).deleteRecursively()
        reloadProjects()
    }

    suspend fun importVideoFromUri(uri: Uri): RecordingProject = withContext(Dispatchers.IO) {
        val (id, dir) = allocateProjectDir()
        val destFile = File(dir, "raw.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open selected video")

        val meta = readVideoMetadata(destFile.absolutePath)
        val stamp = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date())
        val project = RecordingProject(
            id = id,
            title = "Recording $stamp",
            createdAtMs = System.currentTimeMillis(),
            durationMs = meta.durationMs,
            width = meta.width,
            height = meta.height,
            videoPath = destFile.absolutePath
        )
        saveProject(project)
        project
    }

    suspend fun exportVideoToGallery(sourceMp4: File, displayName: String): Uri? = withContext(Dispatchers.IO) {
        if (!sourceMp4.exists()) return@withContext null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Recordly")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        resolver.openOutputStream(uri)?.use { out ->
            sourceMp4.inputStream().use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }

    suspend fun exportFileToDownloads(file: File, displayName: String, mimeType: String): Uri? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Recordly")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val uri = resolver.insert(collection, values) ?: return@withContext null
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }

    data class VideoMeta(val durationMs: Long, val width: Int, val height: Int)

    fun readVideoMetadata(path: String): VideoMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) VideoMeta(dur, h, w) else VideoMeta(dur, w, h)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
