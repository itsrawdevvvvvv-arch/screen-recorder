package com.recordly.mobile.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.recordly.mobile.model.AiEditPlan
import com.recordly.mobile.model.ContactSheetFrame
import com.recordly.mobile.model.EditSegment
import com.recordly.mobile.model.RecordingProject
import com.recordly.mobile.model.SegmentAction
import com.recordly.mobile.model.ZoomKeyframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ContactSheetEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    data class GenerationResult(
        val frames: List<ContactSheetFrame>,
        val pdfPath: String?,
        val zipPath: String?
    )

    suspend fun generateContactSheet(
        context: Context,
        project: RecordingProject,
        projectDir: File,
        intervalSec: Float = 1.5f,
        maxFramesLimit: Int = 24,
        onProgress: (Float) -> Unit = {}
    ): GenerationResult = withContext(Dispatchers.IO) {
        val sheetDir = File(projectDir, "contact_sheet").apply { mkdirs() }
        val framesDir = File(sheetDir, "frames").apply { mkdirs() }

        val retriever = MediaMetadataRetriever()
        val frameResults = mutableListOf<ContactSheetFrame>()
        val bitmapsForPdf = mutableListOf<Pair<Bitmap, String>>()

        try {
            retriever.setDataSource(project.videoPath)
            val durationMs = project.durationMs.coerceAtLeast(1000L)

            val intervalMs = (intervalSec * 1000L).toLong().coerceIn(500L, 5000L)
            val calculatedSteps = durationMs / intervalMs
            val effectiveStepMs = if (calculatedSteps > maxFramesLimit) {
                durationMs / maxFramesLimit
            } else {
                intervalMs
            }.coerceAtLeast(300L)

            val targetTimes = mutableListOf<Long>()
            var curTime = 250L
            while (curTime < durationMs && targetTimes.size < maxFramesLimit) {
                targetTimes.add(curTime)
                curTime += effectiveStepMs
            }
            if (targetTimes.isEmpty()) targetTimes.add(0L)

            val total = targetTimes.size
            for ((idx, tMs) in targetTimes.withIndex()) {
                val rawBmp = grabFrame(retriever, tMs * 1000L, 480, 854)
                if (rawBmp != null) {
                    val formatted = formatTimestamp(tMs)
                    val stampedBmp = stampFrame(rawBmp, idx + 1, formatted)
                    val frameFile = File(framesDir, String.format(Locale.US, "frame_%02d_%05dms.jpg", idx + 1, tMs))
                    FileOutputStream(frameFile).use { out ->
                        stampedBmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }

                    frameResults += ContactSheetFrame(
                        index = idx + 1,
                        timestampMs = tMs,
                        formattedTime = formatted,
                        imagePath = frameFile.absolutePath
                    )
                    bitmapsForPdf.add(stampedBmp to formatted)
                }
                onProgress(0.1f + ((idx + 1).toFloat() / total) * 0.45f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { retriever.release() }
        }

        // 1. Generate PDF Storyboard
        val pdfFile = File(sheetDir, "contact_sheet.pdf")
        val pdfGenerated = runCatching {
            generatePdf(
                pdfFile = pdfFile,
                project = project,
                frames = bitmapsForPdf
            )
        }.isSuccess
        onProgress(0.75f)

        // 2. Generate AI Prompt text
        val promptText = buildAiPrompt(project, frameResults)
        val promptFile = File(sheetDir, "prompt.txt").apply {
            writeText(promptText)
        }

        // 3. Generate Metadata JSON
        val metaFile = File(sheetDir, "metadata.json").apply {
            writeText(
                json.encodeToString(
                    ContactSheetMetadataDto.serializer(),
                    ContactSheetMetadataDto(
                        projectId = project.id,
                        title = project.title,
                        durationMs = project.durationMs,
                        width = project.width,
                        height = project.height,
                        frameCount = frameResults.size,
                        frames = frameResults
                    )
                )
            )
        }

        // 4. Generate ZIP Bundle
        val zipFile = File(sheetDir, "contact_sheet_bundle.zip")
        val zipGenerated = runCatching {
            createZipBundle(
                zipFile = zipFile,
                frameFiles = frameResults.map { File(it.imagePath) },
                pdfFile = if (pdfGenerated) pdfFile else null,
                promptFile = promptFile,
                metadataFile = metaFile
            )
        }.isSuccess
        onProgress(1.0f)

        // Clean up in-memory bitmaps
        bitmapsForPdf.forEach { (bmp, _) ->
            if (!bmp.isRecycled) bmp.recycle()
        }

        GenerationResult(
            frames = frameResults,
            pdfPath = if (pdfGenerated) pdfFile.absolutePath else null,
            zipPath = if (zipGenerated) zipFile.absolutePath else null
        )
    }

    private fun grabFrame(r: MediaMetadataRetriever, timeUs: Long, targetW: Int, targetH: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 27) {
                r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH)
                    ?: r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun stampFrame(src: Bitmap, frameNum: Int, formattedTime: String): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E60E1012")
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40DCC9A9")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCC9A9") // Warm Sand
            textSize = max(16f, h * 0.045f)
            isFakeBoldText = true
        }

        val badgeText = "#$frameNum • $formattedTime"
        val textW = textPaint.measureText(badgeText)
        val padX = 14f
        val padY = 8f
        val badgeW = textW + padX * 2
        val badgeH = textPaint.textSize + padY * 2
        val rect = RectF(w - badgeW - 14f, h - badgeH - 14f, w - 14f, h - 14f)

        canvas.drawRoundRect(rect, 10f, 10f, bgPaint)
        canvas.drawRoundRect(rect, 10f, 10f, strokePaint)
        canvas.drawText(badgeText, rect.left + padX, rect.bottom - padY - 2f, textPaint)
        return out
    }

    private fun generatePdf(
        pdfFile: File,
        project: RecordingProject,
        frames: List<Pair<Bitmap, String>>
    ) {
        val pageWidth = 842 // A4 landscape points (72 dpi approx)
        val pageHeight = 595
        val document = PdfDocument()

        val cols = 4
        val rows = 3
        val framesPerPage = cols * rows
        val totalPages = max(1, ceil(frames.size.toFloat() / framesPerPage).toInt())

        val bgPaint = Paint().apply { color = Color.parseColor("#0E1012") }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCC9A9") // Warm Sand
            textSize = 20f
            isFakeBoldText = true
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9DA3AE")
            textSize = 10f
        }
        val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22262E")
            style = Paint.Style.FILL
        }
        val frameBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33DCC9A9")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        for (pageIdx in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIdx + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Background
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

            // Header Banner
            val headerTop = 26f
            canvas.drawText("RECORDLY CONTACT SHEET", 36f, headerTop + 16f, titlePaint)
            val subtitle = "${project.title}  •  ${formatTimestamp(project.durationMs)}  •  ${project.width}x${project.height}  •  Page ${pageIdx + 1}/$totalPages"
            canvas.drawText(subtitle, 36f, headerTop + 32f, metaPaint)

            // Right header badge
            val badgeRect = RectF(pageWidth - 210f, headerTop + 6f, pageWidth - 36f, headerTop + 32f)
            canvas.drawRoundRect(badgeRect, 6f, 6f, tagBgPaint)
            val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B83A2D") // Terracotta
                textSize = 9.5f
                isFakeBoldText = true
            }
            canvas.drawText("Claude AI Storyboard Exporter", badgeRect.left + 12f, badgeRect.centerY() + 3.5f, tagTextPaint)

            // Thumbnails Grid
            val gridLeft = 36f
            val gridTop = headerTop + 48f
            val gridWidth = pageWidth - 72f
            val gridHeight = pageHeight - gridTop - 30f

            val slotW = gridWidth / cols
            val slotH = gridHeight / rows

            val pageFrames = frames.drop(pageIdx * framesPerPage).take(framesPerPage)
            for ((localIdx, item) in pageFrames.withIndex()) {
                val (bmp, timeStr) = item
                val c = localIdx % cols
                val r = localIdx / cols

                val cellX = gridLeft + c * slotW
                val cellY = gridTop + r * slotH

                // Maintain aspect ratio inside slot
                val maxImgW = slotW - 12f
                val maxImgH = slotH - 18f
                val scale = min(maxImgW / bmp.width, maxImgH / bmp.height)
                val imgW = bmp.width * scale
                val imgH = bmp.height * scale
                val imgLeft = cellX + (slotW - imgW) / 2f
                val imgTop = cellY + (slotH - imgH - 14f) / 2f

                val destRect = RectF(imgLeft, imgTop, imgLeft + imgW, imgTop + imgH)
                canvas.drawBitmap(bmp, null, destRect, null)
                canvas.drawRoundRect(destRect, 4f, 4f, frameBorderPaint)
            }

            // Footer
            val footerText = "Generated with Recordly • Paste prompt.txt into Claude AI along with this PDF"
            canvas.drawText(footerText, 36f, pageHeight - 12f, metaPaint)

            document.finishPage(page)
        }

        if (pdfFile.exists()) pdfFile.delete()
        FileOutputStream(pdfFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    private fun createZipBundle(
        zipFile: File,
        frameFiles: List<File>,
        pdfFile: File?,
        promptFile: File?,
        metadataFile: File?
    ) {
        if (zipFile.exists()) zipFile.delete()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            pdfFile?.let { if (it.exists()) addFileToZip(zos, it, it.name) }
            promptFile?.let { if (it.exists()) addFileToZip(zos, it, it.name) }
            metadataFile?.let { if (it.exists()) addFileToZip(zos, it, it.name) }

            for (frame in frameFiles) {
                if (frame.exists()) {
                    addFileToZip(zos, frame, "frames/${frame.name}")
                }
            }
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input ->
            input.copyTo(zos, bufferSize = 8192)
        }
        zos.closeEntry()
    }

    fun buildAiPrompt(project: RecordingProject, frames: List<ContactSheetFrame>): String {
        val durationSec = String.format(Locale.US, "%.1f", project.durationMs / 1000f)
        val frameSummary = frames.joinToString("\n") {
            "  - Frame #${it.index}: ${it.formattedTime} (${it.timestampMs}ms)"
        }

        return """
# TASK: Generate Edit Decision List (EDL) & Camera Zooms for Screen Recording

I have attached the visual contact sheet (PDF) and keyframe images for my mobile screen recording:
- **Title**: ${project.title}
- **Duration**: ${durationSec}s (${project.durationMs}ms)
- **Resolution**: ${project.width} x ${project.height}
- **Extracted Frames**: ${frames.size} keyframes sampled

### Keyframe Timestamps:
$frameSummary

---

### INSTRUCTIONS:
Please inspect each frame's visual content, UI switches, active screen elements, and interactions:
1. **Trim Dead Time / Typing / Stalls**:
   - Mark sections where keyboard appears, loading spinners spin, or dead pauses occur with `action="TRIM"`.
2. **Speed Pacing**:
   - Speed up navigation, page scrolling, or passive reading segments (`speed=2.0` to `4.0`).
   - Normal content remains at `speed=1.0`.
3. **Dynamic Zoom-In / Zoom-Out Coordinates**:
   - Add zoom keyframes (`zoomScale` between 1.6 and 2.4) centered on focal touch points, buttons, cards, or inputs (`focusX` and `focusY` normalized between 0.05 and 0.95).
   - Ensure lead-in is smooth (350ms) and camera centers on the active UI action.

Respond ONLY with valid, clean JSON matching this exact schema:

```json
{
  "suggestedTitle": "Short descriptive title",
  "summary": "1-2 sentence explanation of edits, cuts, and zoom camera motions",
  "segments": [
    {
      "startMs": 0,
      "endMs": 2800,
      "speed": 1.0,
      "action": "KEEP",
      "reason": "Intro walkthrough"
    },
    {
      "startMs": 2800,
      "endMs": 4500,
      "speed": 3.0,
      "action": "KEEP",
      "reason": "Fast forward scrolling"
    }
  ],
  "zoomKeyframes": [
    {
      "startMs": 1000,
      "endMs": 2800,
      "zoomScale": 2.0,
      "focusX": 0.50,
      "focusY": 0.42,
      "note": "Zoom into primary button"
    }
  ]
}
```
        """.trimIndent()
    }

    /**
     * Parses the user's pasted Claude AI / LLM response into an AiEditPlan.
     * Supports markdown fences or raw JSON.
     */
    fun parseAiResponse(rawInput: String, durationMs: Long): Result<AiEditPlan> = runCatching {
        var cleanJson = rawInput.trim()
        if (cleanJson.contains("```json")) {
            cleanJson = cleanJson.substringAfter("```json").substringBefore("```").trim()
        } else if (cleanJson.contains("```")) {
            cleanJson = cleanJson.substringAfter("```").substringBefore("```").trim()
        }

        val dto = json.decodeFromString<AiResponseDto>(cleanJson)

        val normalizedSegments = if (dto.segments.isNotEmpty()) {
            dto.segments.map { s ->
                EditSegment(
                    startMs = s.startMs.coerceIn(0L, durationMs),
                    endMs = s.endMs.coerceIn(0L, durationMs),
                    speed = s.speed.coerceIn(0.25f, 6.0f),
                    action = if (s.action.equals("TRIM", ignoreCase = true)) SegmentAction.TRIM else SegmentAction.KEEP,
                    reason = s.reason
                )
            }.sortedBy { it.startMs }
        } else {
            listOf(EditSegment(0L, durationMs, 1.0f, SegmentAction.KEEP, "Full video"))
        }

        val normalizedZooms = dto.zoomKeyframes.map { z ->
            ZoomKeyframe(
                startMs = z.startMs.coerceIn(0L, durationMs),
                endMs = z.endMs.coerceIn(0L, durationMs),
                zoomScale = z.zoomScale.coerceIn(1.2f, 3.5f),
                focusX = z.focusX.coerceIn(0.05f, 0.95f),
                focusY = z.focusY.coerceIn(0.05f, 0.95f),
                id = UUID.randomUUID().toString(),
                note = z.note
            )
        }.sortedBy { it.startMs }

        AiEditPlan(
            suggestedTitle = dto.suggestedTitle,
            summary = dto.summary,
            segments = normalizedSegments,
            zoomKeyframes = normalizedZooms
        )
    }

    private fun formatTimestamp(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val m = (safe / 60_000L)
        val s = (safe % 60_000L) / 1000L
        val cs = (safe % 1000L) / 10L
        return String.format(Locale.US, "%02d:%02d.%02d", m, s, cs)
    }

    @Serializable
    private data class ContactSheetMetadataDto(
        val projectId: String,
        val title: String,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val frameCount: Int,
        val frames: List<ContactSheetFrame>
    )

    @Serializable
    private data class AiResponseDto(
        val suggestedTitle: String = "",
        val summary: String = "",
        val segments: List<LlmSegmentDto> = emptyList(),
        val zoomKeyframes: List<LlmZoomDto> = emptyList()
    )

    @Serializable
    private data class LlmSegmentDto(
        val startMs: Long = 0L,
        val endMs: Long = 0L,
        val speed: Float = 1.0f,
        val action: String = "KEEP",
        val reason: String = ""
    )

    @Serializable
    private data class LlmZoomDto(
        val startMs: Long = 0L,
        val endMs: Long = 0L,
        val focusX: Float = 0.5f,
        val focusY: Float = 0.5f,
        val zoomScale: Float = 2.0f,
        val note: String = ""
    )
}
