package br.com.helpdev.velocimetroalerta.sensors

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/** Reads CRLF-terminated VAM frames and keeps bytes following a frame for the next call. */
internal class VamFrameReader(
    private val input: InputStream,
    private val maxFrameBytes: Int = DEFAULT_MAX_FRAME_BYTES
) {
    private var pending = ByteArray(0)

    @Throws(IOException::class)
    fun readFrame(): String? {
        val chunk = ByteArray(READ_BUFFER_BYTES)
        while (true) {
            val delimiter = findCrlf(pending)
            if (delimiter >= 0) {
                val frame = pending.copyOfRange(0, delimiter + CRLF_BYTES)
                pending = pending.copyOfRange(delimiter + CRLF_BYTES, pending.size)
                return String(frame, StandardCharsets.UTF_8)
            }
            if (pending.size >= maxFrameBytes) {
                throw IOException("VAM frame exceeds $maxFrameBytes bytes")
            }

            val read = input.read(chunk, 0, minOf(chunk.size, maxFrameBytes - pending.size))
            when {
                read < 0 -> {
                    if (pending.isEmpty()) return null
                    throw EOFException("Socket closed in the middle of a VAM frame")
                }
                read == 0 -> continue
                else -> pending += chunk.copyOfRange(0, read)
            }
        }
    }

    private fun findCrlf(bytes: ByteArray): Int {
        for (index in 0 until bytes.size - 1) {
            if (bytes[index] == CR && bytes[index + 1] == LF) return index
        }
        return -1
    }

    private companion object {
        const val DEFAULT_MAX_FRAME_BYTES = 8 * 1024
        const val READ_BUFFER_BYTES = 512
        const val CRLF_BYTES = 2
        const val CR: Byte = 13
        const val LF: Byte = 10
    }
}
