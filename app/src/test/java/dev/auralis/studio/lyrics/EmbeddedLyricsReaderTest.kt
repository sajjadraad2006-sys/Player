package dev.auralis.studio.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedLyricsReaderTest {
    @Test fun parsesUtf8UsltWithoutInventingTimestamps() {
        val data = byteArrayOf(3, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0) +
            "First line\nSecond line".toByteArray()
        assertEquals("First line\nSecond line", EmbeddedLyricsReader.parseUslt(data))
    }

    @Test fun parsesMillisecondSyltAsLrc() {
        val header = byteArrayOf(3, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 2, 1, 0)
        val line = "Hello".toByteArray() + byteArrayOf(0, 0, 0, 0x04, 0xD2.toByte())
        val parsed = EmbeddedLyricsReader.parseSylt(header + line).orEmpty()
        assertTrue(parsed.startsWith("[00:01.23]Hello"))
    }
}
