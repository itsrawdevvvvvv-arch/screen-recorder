package com.recordly.mobile.capture

import android.os.SystemClock
import com.recordly.mobile.shizuku.IMouseEventCallback
import com.recordly.mobile.shizuku.IMouseEventService
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class MouseEventUserService : IMouseEventService.Stub() {

    @Volatile
    private var running = false
    private var process: Process? = null
    private var workerThread: Thread? = null

    override fun destroy() {
        stopTracking()
        exitProcess(0)
    }

    override fun isTracking(): Boolean = running

    override fun stopTracking() {
        running = false
        runCatching { process?.destroy() }
        process = null
        workerThread?.interrupt()
        workerThread = null
    }

    override fun startTracking(
        screenWidth: Int,
        screenHeight: Int,
        sensitivity: Float,
        callback: IMouseEventCallback?
    ) {
        stopTracking()
        running = true
        val width = screenWidth.coerceAtLeast(1).toFloat()
        val height = screenHeight.coerceAtLeast(1).toFloat()
        val sens = sensitivity.coerceIn(0.3f, 3.0f)

        workerThread = thread(start = true, name = "RecordlyMouseGetEvent") {
            var cursorX = width / 2f
            var cursorY = height / 2f
            val startTime = SystemClock.elapsedRealtime()
            var pendingMove = false
            var pendingDown = false
            var pendingUp = false
            var pendingButton = 0

            try {
                val proc = Runtime.getRuntime().exec(arrayOf("getevent", "-lt"))
                process = proc
                callback?.onStatus("Shizuku getevent mouse tracker active")

                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    while (running) {
                        val line = reader.readLine() ?: break
                        // Typical line: [   1234.567890] /dev/input/event4: EV_REL       REL_X                fffffffc
                        val colonIdx = line.indexOf(": ")
                        if (colonIdx < 0) continue
                        val payload = line.substring(colonIdx + 2).trim()
                        val tokens = payload.split(Regex("\\s+"))
                        if (tokens.size < 3) continue

                        val evType = tokens[0]
                        val evCode = tokens[1]
                        val evValHex = tokens[2]

                        when (evType) {
                            "EV_REL" -> {
                                val delta = parseSignedHex32(evValHex) * sens
                                when (evCode) {
                                    "REL_X" -> {
                                        cursorX = (cursorX + delta).coerceIn(0f, width)
                                        pendingMove = true
                                    }
                                    "REL_Y" -> {
                                        cursorY = (cursorY + delta).coerceIn(0f, height)
                                        pendingMove = true
                                    }
                                }
                            }
                            "EV_KEY" -> {
                                if (evCode == "BTN_LEFT" || evCode == "BTN_MOUSE" || evCode == "BTN_RIGHT") {
                                    val pressed = evValHex.equals("DOWN", ignoreCase = true) ||
                                        evValHex.toLongOrNull(16) == 1L
                                    if (pressed) pendingDown = true else pendingUp = true
                                    pendingButton = if (evCode == "BTN_RIGHT") 2 else 1
                                }
                            }
                            "EV_SYN" -> {
                                if (evCode == "SYN_REPORT" && (pendingMove || pendingDown || pendingUp)) {
                                    val elapsed = SystemClock.elapsedRealtime() - startTime
                                    callback?.onMouseSample(
                                        elapsed,
                                        (cursorX / width).coerceIn(0f, 1f),
                                        (cursorY / height).coerceIn(0f, 1f),
                                        pendingDown,
                                        pendingUp,
                                        pendingButton
                                    )
                                    pendingMove = false
                                    pendingDown = false
                                    pendingUp = false
                                    pendingButton = 0
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                callback?.onStatus("Mouse tracker stopped: ${t.message ?: "unknown"}")
            } finally {
                running = false
            }
        }
    }

    private fun parseSignedHex32(hex: String): Int {
        return runCatching {
            val unsigned = hex.toLong(16)
            unsigned.toInt()
        }.getOrDefault(0)
    }
}
