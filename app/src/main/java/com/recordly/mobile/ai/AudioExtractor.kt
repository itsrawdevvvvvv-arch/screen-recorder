package com.recordly.mobile.ai

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.recordly.mobile.model.TimeInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.sqrt

object AudioExtractor {

    /**
     * Extracts the audio track from an MP4 (or copies an external audio file) into a compact .m4a
     * file suitable for Groq Whisper upload. Returns null if the source has no audio track.
     */
    suspend fun extractAudioToM4a(sourceMediaPath: String, outputM4aFile: File): File? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(sourceMediaPath)
            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { idx ->
                extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null

            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            // If already AAC/MP4-compatible, fast remux with zero re-encoding
            if (mime.contains("mp4a") || mime.contains("aac")) {
                if (outputM4aFile.exists()) outputM4aFile.delete()
                extractor.selectTrack(audioTrackIndex)
                val muxer = MediaMuxer(outputM4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxTrack = muxer.addTrack(format)
                muxer.start()

                val buffer = ByteBuffer.allocate(256 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxTrack, buffer, info)
                    extractor.advance()
                }
                muxer.stop()
                muxer.release()
                return@withContext outputM4aFile
            } else {
                // Fallback: copy raw external file directly if it's mp3/wav/ogg/m4a
                val src = File(sourceMediaPath)
                val copyOut = File(outputM4aFile.parentFile, "audio_upload.${src.extension.ifEmpty { "m4a" }}")
                src.copyTo(copyOut, overwrite = true)
                return@withContext copyOut
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /**
     * Computes silence spans (>= minSilenceMs) locally using decoded PCM RMS energy so Groq LLM
     * knows exact speech/pause boundaries.
     */
    suspend fun detectSilenceIntervals(
        mediaPath: String,
        minSilenceMs: Long = 900L,
        rmsThreshold: Double = 380.0
    ): List<TimeInterval> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val silences = mutableListOf<TimeInterval>()

        try {
            extractor.setDataSource(mediaPath)
            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { idx ->
                extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext emptyList()

            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
            extractor.selectTrack(audioTrackIndex)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var currentSilenceStartMs: Long? = null
            var lastPtsMs = 0L

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5_000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    val ptsMs = bufferInfo.presentationTimeUs / 1000L
                    lastPtsMs = maxOf(lastPtsMs, ptsMs)

                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuf = outBuf.asShortBuffer()
                        var sumSq = 0.0
                        val count = shortBuf.remaining()
                        while (shortBuf.hasRemaining()) {
                            val s = shortBuf.get().toDouble()
                            sumSq += s * s
                        }
                        val rms = if (count > 0) sqrt(sumSq / count) else 0.0
                        if (rms < rmsThreshold) {
                            if (currentSilenceStartMs == null) currentSilenceStartMs = ptsMs
                        } else {
                            currentSilenceStartMs?.let { start ->
                                if (ptsMs - start >= minSilenceMs) {
                                    silences += TimeInterval(start, ptsMs, label = "Silence", trim = true)
                                }
                                currentSilenceStartMs = null
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
            currentSilenceStartMs?.let { start ->
                if (lastPtsMs - start >= minSilenceMs) {
                    silences += TimeInterval(start, lastPtsMs, label = "Trailing Silence", trim = true)
                }
            }
        } catch (_: Throwable) {
            // Return whatever silences were parsed safely
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
        silences
    }
}
