package io.heapy.kotbusta.service

import io.heapy.kotbusta.model.Book
import io.heapy.kotbusta.model.FbElement
import io.heapy.kotbusta.model.FbNode
import io.heapy.kotbusta.model.FbText
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.time.Instant

class BookContentServiceTest {
    private val service = BookContentService(booksDataPath = Path.of("src/test/resources/flibusta-sample"))

    @Test
    fun `renders sections and titles as headings and paragraphs`() {
        val fb2 = "<FictionBook><body><section>" +
            "<title><p>Chapter One</p></title>" +
            "<p>Hello <emphasis>world</emphasis> &amp; friends.</p>" +
            "</section></body></FictionBook>"

        val html = service.renderContent(fb2).pages.flatMap { it.nodes }.toDebugHtml()

        assertTrue(html.contains("<h2 class=\"fb2-title\">Chapter One</h2>"), html)
        assertTrue(html.contains("<p>Hello <em>world</em> &amp; friends.</p>"), html)
    }

    @Test
    fun `escapes markup embedded in FB2 text so it cannot execute`() {
        val fb2 = "<FictionBook><body><section>" +
            "<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>" +
            "</section></body></FictionBook>"

        val html = service.renderContent(fb2).pages.flatMap { it.nodes }.toDebugHtml()

        assertFalse(html.contains("<script>"), "raw script tag must not survive: $html")
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), html)
    }

    @Test
    fun `nested sections increase heading depth`() {
        val fb2 = "<FictionBook><body><section><title><p>A</p></title>" +
            "<section><title><p>B</p></title></section>" +
            "</section></body></FictionBook>"

        val html = service.renderContent(fb2).pages.flatMap { it.nodes }.toDebugHtml()

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
        val html = result.pages.flatMap { it.nodes }.toDebugHtml()

        assertTrue(result.hasImages)
        assertTrue(
            html.contains("<img class=\"fb2-image\" alt=\"\" src=\"data:image/png;base64,$base64\">"),
            html,
        )
    }

    @Test
    fun `skips a non-base64 binary`() {
        val fb2 = "<FictionBook xmlns:xlink=\"http://www.w3.org/1999/xlink\"><body><section>" +
            "<p><image xlink:href=\"#img1\"/></p>" +
            "</section></body>" +
            "<binary id=\"img1\" content-type=\"image/png\">not!valid!base64!</binary></FictionBook>"

        val result = service.renderContent(fb2)
        val html = result.pages.flatMap { it.nodes }.toDebugHtml()

        assertFalse(result.hasImages)
        assertFalse(html.contains("<img"), html)
    }

    @Test
    fun `embeds every binary image with no cumulative budget`() {
        val base64A = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val base64B = Base64.getEncoder().encodeToString(byteArrayOf(4, 5, 6))
        val base64C = Base64.getEncoder().encodeToString(byteArrayOf(7, 8, 9))
        val fb2 = "<FictionBook xmlns:xlink=\"http://www.w3.org/1999/xlink\"><body><section>" +
            "<p><image xlink:href=\"#a\"/><image xlink:href=\"#b\"/><image xlink:href=\"#c\"/></p>" +
            "</section></body>" +
            "<binary id=\"a\" content-type=\"image/png\">$base64A</binary>" +
            "<binary id=\"b\" content-type=\"image/png\">$base64B</binary>" +
            "<binary id=\"c\" content-type=\"image/png\">$base64C</binary>" +
            "</FictionBook>"

        val result = service.renderContent(fb2)
        val html = result.pages.flatMap { it.nodes }.toDebugHtml()

        assertTrue(result.hasImages)
        assertTrue(html.contains("data:image/png;base64,$base64A"), html)
        assertTrue(html.contains("data:image/png;base64,$base64B"), html)
        assertTrue(html.contains("data:image/png;base64,$base64C"), html)
    }

    @Test
    fun `renders poems as stanzas and verses`() {
        val fb2 = "<FictionBook><body><section><poem><stanza>" +
            "<v>Line one</v><v>Line two</v>" +
            "</stanza></poem></section></body></FictionBook>"

        val html = service.renderContent(fb2).pages.flatMap { it.nodes }.toDebugHtml()

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

        val html = service.renderContent(fb2).pages.flatMap { it.nodes }.toDebugHtml()

        assertTrue(html.contains("<span>x</span>"), html)
        assertTrue(html.contains("<a href=\"#note1\">y</a>"), html)
        assertFalse(html.contains("evil.example"), html)
    }

    @Test
    fun `renders ids for internal anchor targets`() {
        val fb2 = "<FictionBook xmlns:xlink=\"http://www.w3.org/1999/xlink\"><body><section>" +
            "<p><a xlink:href=\"#note1\">note</a></p>" +
            "<section id=\"note1\"><p>Footnote</p></section>" +
            "</section></body></FictionBook>"

        val result = service.renderContent(fb2)
        val html = result.pages.flatMap { it.nodes }.toDebugHtml()

        assertTrue(html.contains("<a href=\"#note1\">note</a>"), html)
        assertTrue(html.contains("<section id=\"note1\" class=\"fb2-section\">"), html)
        assertEquals(1, result.anchorPageIndex["note1"])
    }

    @Test
    fun `returns no pages for a book without a body`() {
        val fb2 = "<FictionBook><description><title-info>" +
            "<book-title>T</book-title></title-info></description></FictionBook>"

        val result = service.renderContent(fb2)

        assertTrue(result.pages.isEmpty())
        assertFalse(result.hasImages)
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

        assertTrue(result.pages.isNotEmpty(), "expected at least one page")
        val html = result.pages.flatMap { it.nodes }.toDebugHtml()
        assertTrue(html.contains("fb2-body"), html)
    }

    @Test
    fun `renders a body with HTML entities end-to-end without truncating`(@TempDir dir: Path) {
        val fb2 = "<FictionBook><body><section>" +
            "<p>before&nbsp;after &mdash; done &amp; ok</p>" +
            "<p>tail survives</p>" +
            "</section></body></FictionBook>"
        writeFb2Zip(dir, "entities", "55.fb2", fb2.toByteArray(Charsets.UTF_8))

        val result = runBlocking {
            BookContentService(booksDataPath = dir).render(book(55, "55.fb2", "entities"))
        }
        val html = result.pages.flatMap { it.nodes }.toDebugHtml()

        // The undeclared &nbsp;/&mdash; must not abort the parse and drop the rest of the book.
        assertTrue(html.contains("tail survives"), html)
        assertTrue(html.contains("—"), html) // &mdash; -> em dash
        assertTrue(html.contains(" "), html) // &nbsp; -> no-break space
    }

    @Test
    fun `cover binary is excluded from body image embedding`() {
        val bodyBase64 = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4, 5))
        val coverBinary = "not!valid!base64!".repeat(1024)
        val fb2 = "<FictionBook xmlns:xlink=\"http://www.w3.org/1999/xlink\">" +
            "<description><title-info><coverpage><image xlink:href=\"#cover\"/></coverpage></title-info></description>" +
            "<body><section><p><image xlink:href=\"#img1\"/></p></section></body>" +
            "<binary id=\"cover\" content-type=\"image/jpeg\">$coverBinary</binary>" +
            "<binary id=\"img1\" content-type=\"image/png\">$bodyBase64</binary>" +
            "</FictionBook>"

        val result = service.renderContent(fb2)
        val html = result.pages.flatMap { it.nodes }.toDebugHtml()

        assertTrue(result.hasImages)
        assertTrue(
            html.contains("<img class=\"fb2-image\" alt=\"\" src=\"data:image/png;base64,$bodyBase64\">"),
            html,
        )
    }

    @Test
    fun `render maps a corrupt archive to BookFileException`(@TempDir dir: Path) {
        dir.resolve("broken.zip").writeBytes("PK not a real zip".toByteArray())

        assertThrows(BookFileException::class.java) {
            runBlocking {
                BookContentService(booksDataPath = dir).render(book(1, "1.fb2", "broken"))
            }
        }
    }

    @Test
    fun `multiple top-level sections become separate pages`() {
        val fb2 = "<FictionBook><body>" +
            "<section><title><p>One</p></title><p>First chapter text.</p></section>" +
            "<section><title><p>Two</p></title><p>Second chapter text.</p></section>" +
            "<section><title><p>Three</p></title><p>Third chapter text.</p></section>" +
            "</body></FictionBook>"

        val result = service.renderContent(fb2)

        assertEquals(3, result.pages.size)
        val htmls = result.pages.map { it.nodes.toDebugHtml() }
        assertTrue(htmls[0].contains("First chapter text.") && "Second chapter text." !in htmls[0], htmls[0])
        assertTrue(
            htmls[1].contains("Second chapter text.") &&
                "First chapter text." !in htmls[1] &&
                "Third chapter text." !in htmls[1],
            htmls[1],
        )
        assertTrue(htmls[2].contains("Third chapter text.") && "Second chapter text." !in htmls[2], htmls[2])
    }

    @Test
    fun `an oversized section splits into multiple well-formed pages`() {
        val paragraph = "word ".repeat(200) // ~1000 chars
        val paragraphs = (1..60).joinToString("") { "<p>$paragraph</p>" } // ~60000 chars, over budget
        val fb2 = "<FictionBook><body><section id=\"big\">" +
            "<title><p>Big</p></title>$paragraphs</section></body></FictionBook>"

        val result = service.renderContent(fb2)

        assertTrue(result.pages.size > 1, "expected the oversized section to split into more than one page")
        result.pages.forEach { page ->
            val root = page.nodes.single()
            assertTrue(root is FbElement && root.tag == "div" && root.className == "fb2-body", "page root: $root")
            val section = (root as FbElement).children.single()
            assertTrue(
                section is FbElement && section.tag == "section" && section.className == "fb2-section",
                "page section: $section",
            )
        }
    }

    @Test
    fun `bare paragraphs directly under body render without a section wrapper`() {
        val fb2 = "<FictionBook><body><p>No chapters here.</p></body></FictionBook>"

        val result = service.renderContent(fb2)

        assertEquals(1, result.pages.size)
        val html = result.pages.flatMap { it.nodes }.toDebugHtml()
        assertTrue(html.contains("<p>No chapters here.</p>"), html)
        assertFalse(html.contains("<section"), html)
    }

    @Test
    fun `a notes body is paginated after the main body with a synthesized TOC entry`() {
        val fb2 = "<FictionBook><body>" +
            "<section><title><p>Chapter</p></title><p>Main text.</p></section>" +
            "</body>" +
            "<body name=\"notes\">" +
            "<section id=\"note1\"><p>Footnote text.</p></section>" +
            "</body></FictionBook>"

        val result = service.renderContent(fb2)

        assertEquals(2, result.pages.size)
        val mainHtml = result.pages[0].nodes.toDebugHtml()
        val notesHtml = result.pages[1].nodes.toDebugHtml()
        assertTrue(mainHtml.contains("Main text."), mainHtml)
        assertTrue(notesHtml.contains("Footnote text.") && notesHtml.contains("fb2-notes"), notesHtml)
        assertTrue(result.toc.any { it.title == "Notes" && it.page == 2 }, result.toc.toString())
        assertEquals(2, result.anchorPageIndex["note1"])
    }

    @Test
    fun `TOC entries reflect chapter and nested section titles`() {
        val fb2 = "<FictionBook><body>" +
            "<section><title><p>Chapter One</p></title>" +
            "<section><title><p>Section A</p></title><p>Text.</p></section>" +
            "</section>" +
            "</body></FictionBook>"

        val result = service.renderContent(fb2)

        assertEquals(
            listOf("Chapter One" to 2, "Section A" to 3),
            result.toc.map { it.title to it.level },
        )
        assertTrue(result.toc.all { it.page == 1 })
    }

    @Test
    fun `an internal anchor resolves to the page its target section actually lands on`() {
        val paddingSections = (1..5).joinToString("") { "<section><p>Padding.</p></section>" }
        val fb2 = "<FictionBook xmlns:xlink=\"http://www.w3.org/1999/xlink\"><body>" +
            "<section><p><a xlink:href=\"#note1\">note</a></p></section>" +
            paddingSections +
            "<section id=\"note1\"><p>Footnote</p></section>" +
            "</body></FictionBook>"

        val result = service.renderContent(fb2)

        // 1 anchor section + 5 padding sections + 1 note1 section, each its own page.
        assertEquals(7, result.pages.size)
        assertEquals(7, result.anchorPageIndex["note1"])
    }

    private fun book(id: Int, filePath: String, archivePath: String) = Book(
        id = id,
        title = "T",
        annotation = null,
        genres = emptyList(),
        language = "ru",
        authors = emptyList(),
        series = null,
        seriesNumber = null,
        filePath = filePath,
        archivePath = archivePath,
        fileSize = null,
        dateAdded = Instant.fromEpochSeconds(0),
        coverImageUrl = null,
    )

    private fun writeFb2Zip(dir: Path, archiveName: String, entryName: String, bytes: ByteArray) {
        ZipOutputStream(dir.resolve("$archiveName.zip").outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}

/** Test-only: renders a page's node tree back to an HTML-ish string for substring assertions. */
private fun List<FbNode>.toDebugHtml(): String = joinToString("") { it.toDebugHtml() }

private fun FbNode.toDebugHtml(): String = when (this) {
    is FbText -> escapeDebugHtml(value)
    is FbElement -> buildString {
        append('<').append(tag)
        if (id != null) append(" id=\"").append(escapeDebugHtml(id)).append('"')
        if (className != null) append(" class=\"").append(escapeDebugHtml(className)).append('"')
        if (href != null) append(" href=\"").append(escapeDebugHtml(href)).append('"')
        if (tag == "img") {
            append(" alt=\"\"")
            if (src != null) append(" src=\"").append(src).append('"')
        }
        append('>')
        if (tag != "img" && tag != "br") {
            append(children.toDebugHtml())
            append("</").append(tag).append('>')
        }
    }
}

private fun escapeDebugHtml(text: String): String = buildString {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
