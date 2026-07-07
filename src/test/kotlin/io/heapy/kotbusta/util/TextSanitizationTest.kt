package io.heapy.kotbusta.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `decodeFb2Content decodes UTF-16LE without BOM`() {
        val text = "<FictionBook><body><section><p>Привет</p></section></body></FictionBook>"
        val decoded = decodeFb2Content(ByteArrayInputStream(text.toByteArray(Charsets.UTF_16LE)))!!

        assertTrue(decoded.contains("FictionBook"), decoded)
        assertTrue(decoded.contains("Привет"), decoded)
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

    @Test
    fun `decodeFb2Content decodes known HTML entities and escapes the rest`() {
        val fb2 = "<p>a&nbsp;b &mdash; c &amp; d &unknown; e &copy;</p>"

        val decoded = decodeFb2Content(ByteArrayInputStream(fb2.toByteArray(Charsets.UTF_8)))!!

        assertTrue(decoded.contains("a b"), decoded)        // &nbsp; -> no-break space
        assertTrue(decoded.contains("—"), decoded)          // &mdash; -> em dash
        assertTrue(decoded.contains("&amp; d"), decoded)         // XML-predefined passes through
        assertTrue(decoded.contains("&amp;unknown; e"), decoded) // unknown name rendered literally
        assertTrue(decoded.contains("©"), decoded)          // &copy; -> ©
    }

    @Test
    fun `decodeFb2Content escapes a bare ampersand so parsing cannot abort`() {
        val decoded = decodeFb2Content(ByteArrayInputStream("Tom & Jerry".toByteArray(Charsets.UTF_8)))

        assertEquals("Tom &amp; Jerry", decoded)
    }

    @Test
    fun `decodeFb2Content rejects input larger than the size cap`() {
        val oversize = ByteArray(MAX_FB2_BYTES + 1) { 'a'.code.toByte() }

        assertNull(decodeFb2Content(ByteArrayInputStream(oversize)))
    }
}
