package com.recordly.mobile.ai

import android.util.Base64
import com.recordly.mobile.data.SrtCodec
import com.recordly.mobile.model.AiEditPlan
import com.recordly.mobile.model.EditSegment
import com.recordly.mobile.model.FrameInsight
import com.recordly.mobile.model.GroqSettings
import com.recordly.mobile.model.KeyboardTrimMode
import com.recordly.mobile.model.MouseSample
import com.recordly.mobile.model.SegmentAction
import com.recordly.mobile.model.SrtCue
import com.recordly.mobile.model.TimeInterval
import com.recordly.mobile.model.TimestampedImage
import com.recordly.mobile.model.WordTimestamp
import com.recordly.mobile.model.ZoomKeyframe
import com.recordly.mobile.model.ZoomSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit


data class TranscriptionResult(
    val fullText: String,
    val cues: List<SrtCue>,
    val words: List<WordTimestamp>
)

class GroqApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun testConnection(apiKey: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Groq HTTP ${resp.code}: $body")
                val obj = json.decodeFromString<JsonObject>(body)
                (obj["data"] as? JsonArray)?.size ?: 0
            }
        }
    }

    suspend fun transcribeAudio(
        apiKey: String,
        model: String,
        audioFile: File
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Groq API key is missing. Add it in Settings." }
        require(audioFile.exists() && audioFile.length() > 0L) { "Audio file is empty or missing." }

        val mediaType = when (audioFile.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> "audio/mp4"
        }.toMediaType()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities[]", "word")
            .addFormDataPart("timestamp_granularities[]", "segment")
            .build()

        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Groq Whisper error ${resp.code}: $raw")

            val parsed = json.decodeFromString<WhisperVerboseResponse>(raw)
            val words = parsed.words.map {
                WordTimestamp(
                    word = it.word.trim(),
                    startMs = (it.start * 1000.0).toLong(),
                    endMs = (it.end * 1000.0).toLong()
                )
            }
            val segments = parsed.segments.mapIndexed { idx, seg ->
                SrtCue(
                    index = idx + 1,
                    startMs = (seg.start * 1000.0).toLong(),
                    endMs = (seg.end * 1000.0).toLong(),
                    text = seg.text.trim()
                )
            }
            val crispCues = SrtCodec.buildCuesFromWords(words, segments)
            TranscriptionResult(
                fullText = parsed.text.trim(),
                cues = crispCues,
                words = words
            )
        }
    }

    suspend fun analyzeKeyframesWithVision(
        apiKey: String,
        visionModel: String,
        frames: List<TimestampedImage>
    ): List<FrameInsight> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || frames.isEmpty()) return@withContext emptyList()
        val insights = mutableListOf<FrameInsight>()

        // Groq vision models accept up to 3 images per request
        for (chunk in frames.chunked(3)) {
            val contentArray = buildJsonArray {
                val timestampsDesc = chunk.mapIndexed { idx, img -> "Image ${idx + 1} = ${img.timestampMs}ms" }
                    .joinToString(", ")
                add(buildJsonObject {
                    put("type", "text")
                    put(
                        "text",
                        """
                        Analyze these mobile screen recording frames ($timestampsDesc).
                        For EACH image, detect:
                        1. Short description of UI action ("summary")
                        2. Whether an on-screen mobile keyboard is visible ("keyboardVisible": true/false)
                        3. Whether the screen appears idle, blank, or showing a loading spinner ("isIdleOrLoading": true/false)
                        4. Normalized mouse pointer arrow coordinates if visible ("cursorHintX": 0..1 or null, "cursorHintY": 0..1 or null)
                        Respond strictly with JSON object:
                        {"frames":[{"timestampMs":123,"summary":"...","keyboardVisible":false,"isIdleOrLoading":false,"cursorHintX":null,"cursorHintY":null}]}
                        """.trimIndent()
                    )
                })
                for (img in chunk) {
                    val b64 = Base64.encodeToString(img.jpegBytes, Base64.NO_WRAP)
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject {
                            put("url", "data:image/jpeg;base64,$b64")
                        })
                    })
                }
            }

            val payload = buildJsonObject {
                put("model", visionModel)
                put("temperature", 0.1)
                put("response_format", buildJsonObject { put("type", "json_object") })
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", contentArray)
                    })
                })
            }

            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            runCatching {
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Vision error ${resp.code}: $raw")
                    val content = extractAssistantContent(raw)
                    val parsed = json.decodeFromString<VisionBatchResponse>(content)
                    insights += parsed.frames
                }
            }
        }
        insights
    }

    suspend fun generateAiEditPlan(
        settings: GroqSettings,
        durationMs: Long,
        srtCues: List<SrtCue>,
        silenceIntervals: List<TimeInterval>,
        keyboardIntervals: List<TimeInterval>,
        mouseSamples: List<MouseSample>,
        frameInsights: List<FrameInsight>
    ): AiEditPlan = withContext(Dispatchers.IO) {
        require(settings.apiKey.isNotBlank()) { "Groq API key is missing." }

        val clicksSummary = mouseSamples.filter { it.isDown }
            .take(80)
            .joinToString("\n") {
                "- ${it.timestampMs}ms click at (${"%.2f".format(it.xNorm)}, ${"%.2f".format(it.yNorm)})"
            }

        val srtSummary = srtCues.take(120).joinToString("\n") {
            "- [${it.startMs}ms..${it.endMs}ms] \"${it.text.replace("\n", " ")}\""
        }

        val kbSummary = keyboardIntervals.joinToString("\n") {
            "- [${it.startMs}ms..${it.endMs}ms] ${it.label}"
        }

        val silenceSummary = silenceIntervals.joinToString("\n") {
            "- [${it.startMs}ms..${it.endMs}ms] silence"
        }

        val visionSummary = frameInsights.joinToString("\n") {
            "- ${it.timestampMs}ms: ${it.summary} (kb=${it.keyboardVisible}, idle=${it.isIdleOrLoading})"
        }

        val kbInstruction = when (settings.keyboardTrimMode) {
            KeyboardTrimMode.REMOVE -> "All keyboard intervals MUST have action=\"TRIM\" so the phone keyboard never stays in the final video."
            KeyboardTrimMode.SPEED_UP_5X -> "All keyboard intervals MUST have action=\"KEEP\" with speed=5.0 so typing passes rapidly."
            KeyboardTrimMode.KEEP -> "Keyboard intervals can remain at speed 1.0 unless silent/idle."
        }

        val prompt = """
            You are Recordly Mobile's AI Video Director.
            Build a frame-accurate, contiguous Edit Decision List (EDL) covering 0ms to ${durationMs}ms.
            Goals:
            1. $kbInstruction
            2. Match screen recording pacing to the spoken .srt cues & audio:
               - Trim dead silence gaps longer than 1100ms when no important click happens (action="TRIM").
               - Speed up slow loading, scrolling, or waiting segments (speed between 1.5 and 4.0) so visual actions stay tightly aligned with the .srt narration.
               - Slightly slow down (0.75 to 0.9) ultra-quick clicks if narration explains them over a longer span.
            3. Create smooth zoomKeyframes (zoomScale 1.6 to 2.4) centered on mouse click coordinates or visual focal points when key steps happen or match .srt emphasis.
            4. Segments MUST be sorted, non-overlapping, and cover [0, $durationMs] without gaps.

            INPUT DATA:
            Duration: ${durationMs}ms
            SRT Cues:
            ${srtSummary.ifEmpty { "(none)" }}

            Audio Silences:
            ${silenceSummary.ifEmpty { "(none)" }}

            Keyboard Visible Intervals:
            ${kbSummary.ifEmpty { "(none)" }}

            Mouse Clicks:
            ${clicksSummary.ifEmpty { "(none)" }}

            Vision Frame Notes:
            ${visionSummary.ifEmpty { "(none)" }}

            Respond ONLY with valid JSON matching this schema:
            {
              "suggestedTitle": "Short video title",
              "summary": "1-2 sentence explanation of cuts, speedups, and zooms",
              "segments": [
                {"startMs": 0, "endMs": 2500, "speed": 1.0, "action": "KEEP", "reason": "Intro"},
                {"startMs": 2500, "endMs": 4100, "speed": 1.0, "action": "TRIM", "reason": "Phone keyboard appeared"}
              ],
              "zoomKeyframes": [
                {"startMs": 900, "endMs": 2300, "focusX": 0.48, "focusY": 0.62, "zoomScale": 2.0, "note": "Click Save button"}
              ],
              "aiNotes": ["Trimmed 1.6s keyboard segment", "Sped up loading 2.5x to match narration"]
            }
        """.trimIndent()

        val rawJson = callChatJson(settings.apiKey, settings.chatModel, prompt)
        parsePlanFromLlm(rawJson, durationMs, srtCues, keyboardIntervals)
    }

    suspend fun refineEditPlanWithPrompt(
        settings: GroqSettings,
        durationMs: Long,
        currentPlan: AiEditPlan,
        srtCues: List<SrtCue>,
        userInstruction: String
    ): AiEditPlan = withContext(Dispatchers.IO) {
        require(settings.apiKey.isNotBlank()) { "Groq API key is missing." }

        val currentJson = json.encodeToString(
            LlmEditPlanDto.serializer(),
            LlmEditPlanDto(
                suggestedTitle = currentPlan.suggestedTitle,
                summary = currentPlan.summary,
                segments = currentPlan.segments.map {
                    LlmSegmentDto(it.startMs, it.endMs, it.speed, it.action.name, it.reason)
                },
                zoomKeyframes = currentPlan.zoomKeyframes.map {
                    LlmZoomDto(it.startMs, it.endMs, it.focusX, it.focusY, it.zoomScale, it.note)
                },
                aiNotes = currentPlan.aiNotes
            )
        )

        val prompt = """
            You are Recordly Mobile's AI Video Director.
            Modify the existing Edit Plan JSON strictly according to the user's request.
            Keep segments contiguous covering [0, $durationMs].
            User request: "$userInstruction"

            Current Plan JSON:
            $currentJson

            SRT count: ${srtCues.size} cues.
            Return ONLY the updated JSON with the exact same schema.
        """.trimIndent()

        val updatedRaw = callChatJson(settings.apiKey, settings.chatModel, prompt)
        parsePlanFromLlm(updatedRaw, durationMs, srtCues, currentPlan.keyboardIntervals)
    }

    private fun callChatJson(apiKey: String, model: String, prompt: String): String {
        val payload = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", JsonPrimitive("You output strict, valid JSON only. Never include markdown fences."))
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", JsonPrimitive(prompt))
                })
            })
        }

        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Groq Chat error ${resp.code}: $raw")
            return extractAssistantContent(raw)
        }
    }

    private fun extractAssistantContent(rawResponse: String): String {
        val root = json.decodeFromString<ChatCompletionResponse>(rawResponse)
        val content = root.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        return content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun parsePlanFromLlm(
        rawJson: String,
        durationMs: Long,
        originalSrt: List<SrtCue>,
        keyboardIntervals: List<TimeInterval>
    ): AiEditPlan {
        val dto = json.decodeFromString<LlmEditPlanDto>(rawJson)
        val segments = normalizeSegments(
            dto.segments.map {
                EditSegment(
                    startMs = it.startMs.coerceIn(0L, durationMs),
                    endMs = it.endMs.coerceIn(0L, durationMs),
                    speed = it.speed.coerceIn(0.25f, 8.0f),
                    action = if (it.action.equals("TRIM", ignoreCase = true)) SegmentAction.TRIM else SegmentAction.KEEP,
                    reason = it.reason
                )
            },
            durationMs
        )

        val zooms = dto.zoomKeyframes.mapNotNull { z ->
            val s = z.startMs.coerceIn(0L, durationMs)
            val e = z.endMs.coerceIn(0L, durationMs)
            if (e <= s + 250L) return@mapNotNull null
            ZoomKeyframe(
                id = UUID.randomUUID().toString(),
                startMs = s,
                endMs = e,
                focusX = z.focusX.coerceIn(0.05f, 0.95f),
                focusY = z.focusY.coerceIn(0.05f, 0.95f),
                zoomScale = z.zoomScale.coerceIn(1.2f, 3.0f),
                source = ZoomSource.AI,
                note = z.note
            )
        }.sortedBy { it.startMs }

        val remappedSrt = SrtCodec.remapCuesToEditedTimeline(originalSrt, segments)

        return AiEditPlan(
            summary = dto.summary,
            suggestedTitle = dto.suggestedTitle,
            segments = segments,
            zoomKeyframes = zooms,
            keyboardIntervals = keyboardIntervals,
            remappedSrtCues = remappedSrt,
            aiNotes = dto.aiNotes
        )
    }

    private fun normalizeSegments(raw: List<EditSegment>, durationMs: Long): List<EditSegment> {
        if (durationMs <= 0L) return emptyList()
        if (raw.isEmpty()) return listOf(EditSegment(0L, durationMs, 1.0f, SegmentAction.KEEP, "Full video"))

        val sorted = raw.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        if (sorted.isEmpty()) return listOf(EditSegment(0L, durationMs, 1.0f, SegmentAction.KEEP, "Full video"))

        val out = mutableListOf<EditSegment>()
        var cursor = 0L
        for (seg in sorted) {
            val start = maxOf(cursor, seg.startMs)
            if (start > cursor) {
                out += EditSegment(cursor, start, 1.0f, SegmentAction.KEEP, "Auto-filled span")
            }
            if (seg.endMs > start) {
                out += seg.copy(startMs = start, endMs = minOf(seg.endMs, durationMs))
                cursor = minOf(seg.endMs, durationMs)
            }
        }
        if (cursor < durationMs) {
            out += EditSegment(cursor, durationMs, 1.0f, SegmentAction.KEEP, "Trailing span")
        }
        return out
    }

    @Serializable
    private data class WhisperVerboseResponse(
        val text: String = "",
        val words: List<WhisperWord> = emptyList(),
        val segments: List<WhisperSegment> = emptyList()
    )

    @Serializable
    private data class WhisperWord(
        val word: String = "",
        val start: Double = 0.0,
        val end: Double = 0.0
    )

    @Serializable
    private data class WhisperSegment(
        val start: Double = 0.0,
        val end: Double = 0.0,
        val text: String = ""
    )

    @Serializable
    private data class VisionBatchResponse(
        val frames: List<FrameInsight> = emptyList()
    )

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<ChatChoice> = emptyList()
    )

    @Serializable
    private data class ChatChoice(
        val message: ChatMessage = ChatMessage()
    )

    @Serializable
    private data class ChatMessage(
        val content: String = ""
    )

    @Serializable
    private data class LlmEditPlanDto(
        val suggestedTitle: String = "",
        val summary: String = "",
        val segments: List<LlmSegmentDto> = emptyList(),
        val zoomKeyframes: List<LlmZoomDto> = emptyList(),
        val aiNotes: List<String> = emptyList()
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
