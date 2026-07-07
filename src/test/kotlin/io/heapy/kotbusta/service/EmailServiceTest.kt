package io.heapy.kotbusta.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class EmailServiceTest {
    private fun rawEmail(subject: String, attachmentName: String): String =
        buildRawEmail(
            from = "sender@example.com",
            to = "device@kindle.com",
            subject = subject,
            body = "Please find your requested book attached.",
            attachmentBytes = byteArrayOf(1, 2, 3),
            attachmentName = attachmentName,
            mimeType = "application/epub+zip",
        ).toString(Charsets.ISO_8859_1)

    private fun assertAllHeaderLinesWithinRfc5322(raw: String) {
        raw.split("\r\n").forEach { line ->
            assertTrue(
                line.toByteArray(Charsets.ISO_8859_1).size <= 998,
                "line exceeds RFC 5322 998-octet limit (${line.length} chars): ${line.take(80)}...",
            )
        }
    }

    @Test
    fun `headers are pure ascii for cyrillic titles`() {
        val raw = rawEmail(
            subject = "Your book: Медитация випассаны",
            attachmentName = "Медитация випассаны.epub",
        )

        assertTrue(raw.all { it.code < 0x80 }, "raw message must contain only ASCII bytes")
    }

    @Test
    fun `subject is an encoded word that decodes back to the title`() {
        val raw = rawEmail(
            subject = "Your book: Медитация",
            attachmentName = "Медитация.epub",
        )

        val subject = raw.lines().first { it.startsWith("Subject: ") }.removePrefix("Subject: ")
        assertTrue(subject.startsWith("=?UTF-8?B?") && subject.endsWith("?="), "subject: $subject")

        val decoded = Base64.getDecoder()
            .decode(subject.removePrefix("=?UTF-8?B?").removeSuffix("?="))
            .toString(Charsets.UTF_8)
        assertEquals("Your book: Медитация", decoded)
    }

    @Test
    fun `cyrillic attachment name is sent as rfc2231 filename only`() {
        val raw = rawEmail(
            subject = "Your book: Медитация випассаны",
            attachmentName = "Медитация випассаны.epub",
        )

        val disposition = raw.lines().first { it.startsWith("Content-Disposition: ") }
        assertEquals(
            "Content-Disposition: attachment; " +
                "filename*=UTF-8''%D0%9C%D0%B5%D0%B4%D0%B8%D1%82%D0%B0%D1%86%D0%B8%D1%8F%20" +
                "%D0%B2%D0%B8%D0%BF%D0%B0%D1%81%D1%81%D0%B0%D0%BD%D1%8B.epub",
            disposition,
        )
        assertTrue(raw.contains("""Content-Type: application/epub+zip; name="book.epub""""))
    }

    @Test
    fun `ascii subject and filename pass through unchanged`() {
        val raw = rawEmail(
            subject = "Your book: Clean_Title",
            attachmentName = "Clean_Title.epub",
        )

        assertTrue(raw.contains("Subject: Your book: Clean_Title\r\n"))
        assertTrue(raw.contains("Content-Disposition: attachment; filename=\"Clean_Title.epub\"\r\n"))
        assertFalse(raw.contains("filename*"))
    }

    @Test
    fun `ascii attachment filename with spaces is sent as plain quoted filename`() {
        val raw = rawEmail(
            subject = "Your book: War and Peace",
            attachmentName = "War and Peace.epub",
        )

        assertTrue(raw.contains("Content-Disposition: attachment; filename=\"War and Peace.epub\""))
        assertFalse(raw.contains("filename*"))
    }

    @Test
    fun `sanitized cyrillic title keeps raw message lines within rfc5322 limit`() {
        val longCyrillic = "Медитація".repeat(40)
        val title = sanitizeBookTitle(longCyrillic)
        val raw = rawEmail(
            subject = "Your book: $title",
            attachmentName = "$title.epub",
        )

        assertAllHeaderLinesWithinRfc5322(raw)
    }

    @Test
    fun `sanitized ascii title keeps raw message lines within rfc5322 limit`() {
        val longAscii = "War and Peace ".repeat(120)
        val title = sanitizeBookTitle(longAscii)
        val raw = rawEmail(
            subject = "Your book: $title",
            attachmentName = "$title.epub",
        )

        assertAllHeaderLinesWithinRfc5322(raw)
    }

    @Test
    fun `lines end with crlf`() {
        val raw = rawEmail(
            subject = "Your book: Clean_Title",
            attachmentName = "Clean_Title.epub",
        )

        assertFalse(raw.replace("\r\n", "").contains('\n'), "found LF without CR")
        assertTrue(raw.endsWith("\r\n"))
    }

    @Test
    fun `long subject folds into encoded words of at most 75 chars`() {
        val raw = rawEmail(
            subject = "Your book: Медитация випассаны. Искусство жить осознанно и полной жизнью",
            attachmentName = "book.epub",
        )

        val lines = raw.lines()
        val subjectStart = lines.indexOfFirst { it.startsWith("Subject: ") }
        val encodedWords = mutableListOf(lines[subjectStart].removePrefix("Subject: "))
        var next = subjectStart + 1
        while (lines[next].startsWith(" ")) {
            encodedWords += lines[next].trim()
            next++
        }

        assertTrue(encodedWords.size > 1, "expected folded subject, got $encodedWords")
        encodedWords.forEach { word ->
            assertTrue(word.length <= 75, "encoded word too long: $word")
            assertTrue(word.startsWith("=?UTF-8?B?") && word.endsWith("?="), word)
        }
        val decoded = encodedWords.joinToString("") { word ->
            Base64.getDecoder()
                .decode(word.removePrefix("=?UTF-8?B?").removeSuffix("?="))
                .toString(Charsets.UTF_8)
        }
        assertEquals(
            "Your book: Медитация випассаны. Искусство жить осознанно и полной жизнью",
            decoded,
        )
    }

    @Test
    fun `header injection in the title is neutralized`() {
        val title = sanitizeBookTitle("Evil\r\nBcc: evil@example.com")
        val raw = rawEmail(
            subject = "Your book: $title",
            attachmentName = "$title.epub",
        )

        assertFalse(raw.lines().any { it.startsWith("Bcc:") }, "injected header must not appear")
        assertTrue(raw.contains("Subject: Your book: Evil Bcc: evil@example.com\r\n"))
    }

    @Test
    fun `sanitize strips control characters and quotes`() {
        assertEquals("Evil Bcc: x", sanitizeBookTitle("Evil\r\nBcc:\u0000 \"x\""))
        assertEquals("book", sanitizeBookTitle("  \r\n "))
    }
}
