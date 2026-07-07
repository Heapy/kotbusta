package io.heapy.kotbusta.service

import io.heapy.kotbusta.model.Book
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.Base64
import kotlin.time.Instant

class BookContentServiceTest {
    private val service = BookContentService(booksDataPath = Path.of("src/test/resources/flibusta-sample"))

    @Test
    fun `renders sections and titles as headings and paragraphs`() {
        val fb2 = "<FictionBook><body><section>" +
            "<title><p>Chapter One</p></title>" +
            "<p>Hello <emphasis>world</emphasis> &amp; friends.</p>" +
            "</section></body></FictionBook>"

        val html = service.renderContent(fb2).html

        assertTrue(html.contains("<h2 class=\"fb2-title\">Chapter One</h2>"), html)
        assertTrue(html.contains("<p>Hello <em>world</em> &amp; friends.</p>"), html)
    }

    @Test
    fun `escapes markup embedded in FB2 text so it cannot execute`() {
        val fb2 = "<FictionBook><body><section>" +
            "<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>" +
            "</section></body></FictionBook>"

        val html = service.renderContent(fb2).html

        assertFalse(html.contains("<script>"), "raw script tag must not survive: $html")
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), html)
    }

    @Test
    fun `nested sections increase heading depth`() {
        val fb2 = "<FictionBook><body><section><title><p>A</p></title>" +
            "<section><title><p>B</p></title></section>" +
            "</section></body></FictionBook>"

        val html = service.renderContent(fb2).html

        assertTrue(html.contains("<h2 class=\"fb2-title\">A</h2>"), html)
        assertTrue(html.contains("<h3 class=\"fb2-title\">B</h3>"), html)
    }

    @Test
    fun `inlines a referenced image as a data URI`() {
        val base64 = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4, 5))
        val fb2 = "<FictionBook xmlns:xlink=\"http://www.w3.org/1999/xlink\"><body><section>" +
            "<p>See <image xlink:href=\"#img1\"/></p>" +
            "</section></body>" +
            "<binary id=\"img1\" content-type=\"image/png\">$base64</binary></FictionBook>"

        val result = service.renderContent(fb2)

        assertTrue(result.hasImages)
        assertFalse(result.truncated)
        assertTrue(
            result.html.contains("<img class=\"fb2-image\" alt=\"\" src=\"data:image/png;base64,$base64\">"),
            result.html,
        )
    }

    @Test
    fun `skips a non-base64 binary and flags the book as truncated`() {
        val fb2 = "<FictionBook xmlns:xlink=\"http://www.w3.org/1999/xlink\"><body><section>" +
            "<p><image xlink:href=\"#img1\"/></p>" +
            "</section></body>" +
            "<binary id=\"img1\" content-type=\"image/png\">not!valid!base64!</binary></FictionBook>"

        val result = service.renderContent(fb2)

        assertFalse(result.hasImages)
        assertTrue(result.truncated)
        assertFalse(result.html.contains("<img"), result.html)
    }

    @Test
    fun `renders poems as stanzas and verses`() {
        val fb2 = "<FictionBook><body><section><poem><stanza>" +
            "<v>Line one</v><v>Line two</v>" +
            "</stanza></poem></section></body></FictionBook>"

        val html = service.renderContent(fb2).html

        assertTrue(
            html.contains(
                "<div class=\"fb2-poem\"><div class=\"fb2-stanza\">" +
                    "<div class=\"fb2-verse\">Line one</div><div class=\"fb2-verse\">Line two</div>" +
                    "</div></div>",
            ),
            html,
        )
    }

    @Test
    fun `keeps internal anchors but neutralizes external links`() {
        val fb2 = "<FictionBook xmlns:xlink=\"http://www.w3.org/1999/xlink\"><body><section>" +
            "<p><a xlink:href=\"http://evil.example\">x</a><a xlink:href=\"#note1\">y</a></p>" +
            "</section></body></FictionBook>"

        val html = service.renderContent(fb2).html

        assertTrue(html.contains("<span>x</span>"), html)
        assertTrue(html.contains("<a href=\"#note1\">y</a>"), html)
        assertFalse(html.contains("evil.example"), html)
    }

    @Test
    fun `returns empty html for a book without a body`() {
        val fb2 = "<FictionBook><description><title-info>" +
            "<book-title>T</book-title></title-info></description></FictionBook>"

        val result = service.renderContent(fb2)

        assertEquals("", result.html)
        assertFalse(result.hasImages)
        assertFalse(result.truncated)
    }

    @Test
    fun `renders the body of a real FB2 archive`() {
        val book = Book(
            id = 79797,
            title = "Sample",
            annotation = null,
            genres = emptyList(),
            language = "ru",
            authors = emptyList(),
            series = null,
            seriesNumber = null,
            filePath = "79797.fb2",
            archivePath = "fb2-test-3",
            fileSize = null,
            dateAdded = Instant.fromEpochSeconds(0),
            coverImageUrl = null,
        )

        val result = runBlocking { service.render(book) }

        assertTrue(result.html.isNotBlank(), "expected non-empty rendered body")
        assertTrue(result.html.contains("fb2-body"), result.html)
    }
}
