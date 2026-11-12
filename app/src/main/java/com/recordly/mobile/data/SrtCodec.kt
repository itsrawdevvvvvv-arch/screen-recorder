package com.recordly.mobile.data

import com.recordly.mobile.model.EditSegment
import com.recordly.mobile.model.SegmentAction
import com.recordly.mobile.model.SrtCue
import com.recordly.mobile.model.WordTimestamp
import java.util.Locale

object SrtCodec {

    private val timeRegex = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )

    fun parse(srtContent: String): List<SrtCue> {
        val normalized = srtContent.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isEmpty()) return emptyList()

        val blocks = normalized.split(Regex("\n\\s*\n"))
        val cues = mutableListOf<SrtCue>()
        var fallbackIndex = 1

        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue

            val timeLineIndex = lines.indexOfFirst { timeRegex.containsMatchIn(it) }
            if (timeLineIndex < 0) continue

            val match = timeRegex.find(lines[timeLineIndex]) ?: continue
            val startMs = parseTimestampMs(
                match.groupValues[1].toLong(),
                match.groupValues[2].toLong(),
                match.groupValues[3].toLong(),
                match.groupValues[4]
            )
            val endMs = parseTimestampMs(
                match.groupValues[5].toLong(),
                match.groupValues[6].toLong(),
                match.groupValues[7].toLong(),
                match.groupValues[8]
            )

            val explicitIndex = if (timeLineIndex > 0) lines[0].trim().toIntOrNull() else null
            val text = lines.drop(timeLineIndex + 1).joinToString("\n").trim()
            if (text.isEmpty()) continue

            cues += SrtCue(
                index = explicitIndex ?: fallbackIndex,
                startMs = startMs,
                endMs = maxOf(endMs, startMs + 200L),
                text = text
            )
            fallbackIndex++
        }
        return cues
    }

    fun serialize(cues: List<SrtCue>): String {
        val sb = StringBuilder()
        cues.sortedBy { it.startMs }.forEachIndexed { idx, cue ->
            if (idx > 0) sb.append('\n')
            sb.append(idx + 1).append('\n')
            sb.append(formatTimestamp(cue.startMs))
                .append(" --> ")
                .append(formatTimestamp(maxOf(cue.endMs, cue.startMs + 150L)))
                .append('\n')
            sb.append(wrapSubtitleText(cue.text)).append('\n')
        }
        return sb.toString()
    }

    /**
     * Converts Groq Whisper word timestamps into crisp short SRT cues (<= 8 words or <= 3.2s per cue)
     * ideal for mobile screen-recording captions.
     */
    fun buildCuesFromWords(
        words: List<WordTimestamp>,
        fallbackSegments: List<SrtCue>
    ): List<SrtCue> {
        if (words.isEmpty()) return fallbackSegments

        val result = mutableListOf<SrtCue>()
        val currentWords = mutableListOf<WordTimestamp>()
        var cueIndex = 1

        fun flush() {
            if (currentWords.isEmpty()) return
            val text = currentWords.joinToString(" ") { it.word.trim() }.replace(Regex("\\s+"), " ").trim()
            if (text.isNotEmpty()) {
                result += SrtCue(
                    index = cueIndex++,
                    startMs = currentWords.first().startMs,
                    endMs = maxOf(currentWords.last().endMs, currentWords.first().startMs + 250L),
                    text = text,
                    words = currentWords.toList()
                )
            }
            currentWords.clear()
        }

        for (w in words) {
            val prev = currentWords.lastOrNull()
            val gapMs = if (prev != null) w.startMs - prev.endMs else 0L
            val spanMs = if (currentWords.isNotEmpty()) w.endMs - currentWords.first().startMs else 0L
            val charCount = currentWords.sumOf { it.word.length + 1 } + w.word.length

            val endsSentence = prev?.word?.trim()?.let {
                it.endsWith(".") || it.endsWith("?") || it.endsWith("!")
            } == true

            if (currentWords.isNotEmpty() && (gapMs > 550L || spanMs > 3000L || charCount > 44 || currentWords.size >= 8 || endsSentence)) {
                flush()
            }
            currentWords += w
        }
        flush()
        return result
    }

    /**
     * Remaps original timeline SRT cues onto the new edited timeline after TRIM and SPEED segments.
     */
    fun remapCuesToEditedTimeline(
        originalCues: List<SrtCue>,
        segments: List<EditSegment>
    ): List<SrtCue> {
        if (segments.isEmpty()) return originalCues
        val keepSegments = segments.filter { it.action == SegmentAction.KEEP && it.endMs > it.startMs }
            .sortedBy { it.startMs }
        if (keepSegments.isEmpty()) return emptyList()

        data class MappedSpan(
            val origStart: Long,
            val origEnd: Long,
            val outStart: Long,
            val outEnd: Long,
            val speed: Float
        )

        val mappedSpans = mutableListOf<MappedSpan>()
        var outCursor = 0L
        for (seg in keepSegments) {
            val spd = seg.speed.coerceIn(0.25f, 8.0f)
            val outDur = ((seg.endMs - seg.startMs) / spd).toLong().coerceAtLeast(1L)
            mappedSpans += MappedSpan(seg.startMs, seg.endMs, outCursor, outCursor + outDur, spd)
            outCursor += outDur
        }

        fun mapOrigTime(origMs: Long): Long? {
            for (s in mappedSpans) {
                if (origMs in s.origStart..s.origEnd) {
                    val delta = ((origMs - s.origStart) / s.speed).toLong()
                    return (s.outStart + delta).coerceIn(s.outStart, s.outEnd)
                }
            }
            return null
        }

        val remapped = mutableListOf<SrtCue>()
        var idx = 1
        for (cue in originalCues) {
            val overlappingSpans = mappedSpans.filter { cue.endMs > it.origStart && cue.startMs < it.origEnd }
            if (overlappingSpans.isEmpty()) continue
            val firstSpan = overlappingSpans.first()
            val lastSpan = overlappingSpans.last()
            val clampedStart = cue.startMs.coerceIn(firstSpan.origStart, firstSpan.origEnd)
            val clampedEnd = cue.endMs.coerceIn(lastSpan.origStart, lastSpan.origEnd)
            val newStart = mapOrigTime(clampedStart) ?: continue
            val newEnd = mapOrigTime(clampedEnd) ?: continue
            if (newEnd > newStart + 80L) {
                remapped += cue.copy(
                    index = idx++,
                    startMs = newStart,
                    endMs = newEnd
                )
            }
        }
        return remapped
    }

    /**
     * Maps a timestamp on the edited output timeline back to the original source video timestamp.
     */
    fun outputTimeToOriginalTimeMs(outputMs: Long, segments: List<EditSegment>): Long {
        val keepSegments = segments.filter { it.action == SegmentAction.KEEP && it.endMs > it.startMs }
            .sortedBy { it.startMs }
        if (keepSegments.isEmpty()) return outputMs

        var outCursor = 0L
        for (seg in keepSegments) {
            val spd = seg.speed.coerceIn(0.25f, 8.0f)
            val outDur = ((seg.endMs - seg.startMs) / spd).toLong().coerceAtLeast(1L)
            if (outputMs <= outCursor + outDur) {
                val localOut = (outputMs - outCursor).coerceAtLeast(0L)
                return (seg.startMs + (localOut * spd).toLong()).coerceIn(seg.startMs, seg.endMs)
            }
            outCursor += outDur
        }
        return keepSegments.last().endMs
    }

    private fun parseTimestampMs(h: Long, m: Long, s: Long, msRaw: String): Long {
        val ms = msRaw.padEnd(3, '0').take(3).toLong()
        return ((h * 3600L) + (m * 60L) + s) * 1000L + ms
    }

    fun formatTimestamp(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val hours = safe / 3_600_000L
        val minutes = (safe % 3_600_000L) / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val millis = safe % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun wrapSubtitleText(text: String, maxLineLength: Int = 40): String {
        val clean = text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        if (clean.length <= maxLineLength) return clean
        val words = clean.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= maxLineLength) {
                current.append(' ').append(word)
            } else {
                lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.take(2).joinToString("\n")
    }
}
