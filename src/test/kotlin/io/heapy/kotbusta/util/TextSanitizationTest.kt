package io.heapy.kotbusta.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class TextSanitizationTest {
    @Test
    fun `decodeFb2Content decodes windows-1251 bytes to correct text`() {
        val text = "Привет, мир — тест кодировки"
        val bytes = text.toByteArray(Charset.forName("windows-1251"))

        assertEquals(text, decodeFb2Content(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `decodeFb2Content decodes valid utf-8 bytes as utf-8`() {
        val text = "Unicode текст café"
        val bytes = text.toByteArray(Charsets.UTF_8)

        assertEquals(text, decodeFb2Content(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `decodeFb2Content decodes UTF-16LE with BOM`() {
        val text = "Аннотация в UTF-16LE"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)

        assertEquals(text, decodeFb2Content(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `decodeFb2Content decodes UTF-16BE with BOM`() {
        val text = "Аннотация в UTF-16BE"
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)

        assertEquals(text, decodeFb2Content(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `decodeFb2Content returns null for empty input`() {
        assertNull(decodeFb2Content(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun `decodeFb2Content strips BOM and xml-illegal control chars`() {
        // UTF-8 BOM (EF BB BF) + 'a' + NUL + "bc"; both the BOM and NUL must go.
        val bytes = byteArrayOf(
            0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(),
            'a'.code.toByte(), 0x00, 'b'.code.toByte(), 'c'.code.toByte(),
        )

        assertEquals("abc", decodeFb2Content(ByteArrayInputStream(bytes)))
    }
}
