package br.com.helpdev.velocimetroalerta.sensors

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VamFrameReaderTest {
    @Test
    fun closedSocketReturnsDisconnected() {
        assertNull(VamFrameReader(ByteArrayInputStream(byteArrayOf())).readFrame())
    }

    @Test
    fun partialReadsProduceOneCrlfFrame() {
        val input = ChunkedInputStream(
            "{\"cad\":\"82\"}\r\n{\"hr\":\"144\"}\r\n".toByteArray(),
            maxChunkSize = 3
        )
        val reader = VamFrameReader(input)

        assertEquals("{\"cad\":\"82\"}\r\n", reader.readFrame())
        assertEquals("{\"hr\":\"144\"}\r\n", reader.readFrame())
        assertNull(reader.readFrame())
    }

    private class ChunkedInputStream(
        private val bytes: ByteArray,
        private val maxChunkSize: Int
    ) : InputStream() {
        private var position = 0

        override fun read(): Int = if (position >= bytes.size) -1 else bytes[position++].toInt() and 0xff

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(length, maxChunkSize, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
    }
}
