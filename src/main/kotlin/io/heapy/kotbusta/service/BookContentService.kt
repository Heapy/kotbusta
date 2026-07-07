package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.model.Book
import io.heapy.kotbusta.util.WHITESPACE_RUN
import io.heapy.kotbusta.util.decodeFb2Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.StringReader
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import kotlin.io.path.exists

/**
 * Renders a book's FB2 body into sanitized HTML for the in-app reader.
 *
 * Resolves the FB2 from the on-disk archive layout (`${booksDataPath}/${archivePath}.zip`,
 * entry `filePath`) exactly like [ZipBookFileService], decodes it with [decodeFb2Content]
 * (charset-correct, so Cyrillic renders without mojibake), then walks it with StAX in two
 * passes over the same decoded text: pass 1 collects inlineable `<binary>` images (bounded
 * by a payload budget), pass 2 emits the `<body>` as a fixed whitelist of HTML tags.
 *
 * Security: every text run is HTML-escaped and only whitelisted tags/attributes are emitted,
 * so FB2 content injected into the reader (via `dangerouslySetInnerHTML`) can never execute.
 * `SUPPORT_DTD=false` + external entities off closes XXE.
 */
class BookContentService(
    private val booksDataPath: Path,
) {
    suspend fun render(book: Book): RenderedBook = withContext(Dispatchers.IO) {
        val archiveFile = booksDataPath.resolve("${book.archivePath}.zip")
        if (!archiveFile.exists()) {
            throw BookFileException("Book archive not found: ${book.archivePath}.zip")
        }

        val content = try {
            ZipFile(archiveFile.toFile()).use { zip ->
                val entry = zip.entries().asSequence().find { it.name == book.filePath }
                    ?: zip.getEntry("${book.id}.fb2")
                    ?: throw BookFileException("FB2 entry '${book.filePath}' not found in ${book.archivePath}.zip")
                zip.getInputStream(entry).use { decodeFb2Content(it) }
            }
        } catch (e: IOException) {
            // A corrupt/truncated archive surfaces as ZipException (an IOException). That's a
            // content-unavailable case, not a server fault, so map it to the BookFileException
            // the route already turns into a 404 instead of letting it become a 500.
            throw BookFileException("Failed to read ${book.archivePath}.zip: ${e.message}")
        } ?: return@withContext RenderedBook(html = "", hasImages = false, truncated = false)

        renderContent(content)
    }

    internal fun renderContent(content: String): RenderedBook {
        val factory = newXmlInputFactory()
        val binaries = collectBinaries(content, factory)
        val body = buildBodyHtml(content, factory, binaries.byId)
        return RenderedBook(
            html = body.html,
            hasImages = body.imageCount > 0,
            truncated = binaries.truncated,
        )
    }

    /**
     * Pass 1: collect `<binary>` images as ready-to-embed `data:` URIs. `<binary>` elements
     * appear after `<body>`, so a separate pass builds the id→URI map the body pass resolves
     * against. Enforces a per-image cap and a cumulative budget; anything skipped flips
     * [BinaryResult.truncated] so the UI can note that images were omitted.
     */
    private fun collectBinaries(content: String, factory: XMLInputFactory): BinaryResult {
        val byId = HashMap<String, String>()
        var truncated = false
        var usedBase64Chars = 0L

        val reader = factory.createXMLStreamReader(StringReader(content))
        try {
            var inBinary = false
            var id: String? = null
            var contentType = DEFAULT_IMAGE_CONTENT_TYPE
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> if (reader.localName == "binary") {
                        inBinary = true
                        id = reader.getAttributeValue(null, "id")
                        val declared = reader.getAttributeValue(null, "content-type")?.trim()
                        contentType = if (!declared.isNullOrBlank() && CONTENT_TYPE_RE.matches(declared)) {
                            declared
                        } else {
                            DEFAULT_IMAGE_CONTENT_TYPE
                        }
                    }

                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        val key = id
                        if (inBinary && key != null && key !in byId) {
                            val raw = reader.text
                            if (raw.isNotBlank() && BASE64_RE.matches(raw)) {
                                val base64 = raw.replace(WHITESPACE_RUN, "")
                                when {
                                    base64.length > MAX_SINGLE_IMAGE_BASE64_CHARS -> truncated = true
                                    usedBase64Chars + base64.length > MAX_TOTAL_IMAGE_BASE64_CHARS -> truncated = true
                                    else -> {
                                        byId[key] = "data:$contentType;base64,$base64"
                                        usedBase64Chars += base64.length
                                    }
                                }
                            } else if (raw.isNotBlank()) {
                                truncated = true
                            }
                        }
                    }

                    XMLStreamConstants.END_ELEMENT -> if (reader.localName == "binary") {
                        inBinary = false
                        id = null
                        contentType = DEFAULT_IMAGE_CONTENT_TYPE
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Failed while collecting FB2 binaries: ${e.message}", e)
        } finally {
            reader.close()
        }

        return BinaryResult(byId, truncated)
    }

    /**
     * Pass 2: convert the FB2 `<body>` into whitelisted HTML. On a mid-parse failure (e.g.
     * malformed XML) the HTML accumulated so far is returned — a partial book beats none.
     */
    private fun buildBodyHtml(
        content: String,
        factory: XMLInputFactory,
        binaries: Map<String, String>,
    ): BodyHtml {
        val reader = factory.createXMLStreamReader(StringReader(content))
        val writer = Fb2HtmlWriter(binaries)
        try {
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> writer.startElement(reader)
                    XMLStreamConstants.END_ELEMENT -> writer.endElement(reader)
                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> writer.characters(reader.text)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed while rendering FB2 body (returning partial content): ${e.message}", e)
        } finally {
            reader.close()
        }
        return BodyHtml(writer.html.toString(), writer.imageCount)
    }

    private fun newXmlInputFactory(): XMLInputFactory =
        XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_COALESCING, true)
            setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }

    private class BinaryResult(val byId: Map<String, String>, val truncated: Boolean)

    private class BodyHtml(val html: String, val imageCount: Int)

    /**
     * Streams FB2 body events into an HTML [StringBuilder], mapping the FB2 vocabulary to a
     * fixed set of tags. Text is emitted only inside text-bearing elements (so inter-element
     * indentation whitespace is dropped) and is always HTML-escaped.
     */
    private class Fb2HtmlWriter(private val binaries: Map<String, String>) {
        val html = StringBuilder()
        var imageCount = 0
            private set

        private var bodyDepth = 0
        private var sectionDepth = 0
        private var paragraphDepth = 0
        private var inTitle = false
        private var titleLevel = 0
        private var titleParaCount = 0
        private val anchorClose = ArrayDeque<String>()

        fun characters(text: String) {
            if (bodyDepth > 0 && paragraphDepth > 0) {
                html.appendEscaped(text)
            }
        }

        fun startElement(reader: XMLStreamReader) {
            val name = reader.localName
            if (name == "body") {
                bodyDepth++
                val notes = reader.getAttributeValue(null, "name") == "notes"
                html.append(if (notes) "<div class=\"fb2-body fb2-notes\">" else "<div class=\"fb2-body\">")
                return
            }
            if (bodyDepth == 0) return

            when (name) {
                "section" -> {
                    sectionDepth++
                    html.append("<section class=\"fb2-section\">")
                }
                "title" -> {
                    titleLevel = minOf(sectionDepth + 1, 6)
                    inTitle = true
                    titleParaCount = 0
                    paragraphDepth++
                    html.append("<h").append(titleLevel).append(" class=\"fb2-title\">")
                }
                "p" -> if (inTitle) {
                    if (titleParaCount > 0) html.append("<br>")
                    titleParaCount++
                } else {
                    paragraphDepth++
                    html.append("<p>")
                }
                "subtitle" -> {
                    paragraphDepth++
                    html.append("<p class=\"fb2-subtitle\">")
                }
                "epigraph" -> html.append("<div class=\"fb2-epigraph\">")
                "cite" -> html.append("<blockquote class=\"fb2-cite\">")
                "text-author" -> {
                    paragraphDepth++
                    html.append("<p class=\"fb2-text-author\">")
                }
                "poem" -> html.append("<div class=\"fb2-poem\">")
                "stanza" -> html.append("<div class=\"fb2-stanza\">")
                "v" -> {
                    paragraphDepth++
                    html.append("<div class=\"fb2-verse\">")
                }
                "empty-line" -> html.append("<div class=\"fb2-empty-line\"></div>")
                "emphasis" -> html.append("<em>")
                "strong" -> html.append("<strong>")
                "strikethrough" -> html.append("<s>")
                "sub" -> html.append("<sub>")
                "sup" -> html.append("<sup>")
                "code" -> html.append("<code>")
                "image" -> appendImage(reader)
                "a" -> {
                    val href = reader.getAttributeValue(XLINK_NS, "href")
                        ?: reader.getAttributeValue(null, "href")
                    if (href != null && href.startsWith("#")) {
                        html.append("<a href=\"").appendEscaped(href).append("\">")
                        anchorClose.addLast("</a>")
                    } else {
                        html.append("<span>")
                        anchorClose.addLast("</span>")
                    }
                }
                "table" -> html.append("<table class=\"fb2-table\">")
                "tr" -> html.append("<tr>")
                "td" -> {
                    paragraphDepth++
                    html.append("<td>")
                }
                "th" -> {
                    paragraphDepth++
                    html.append("<th>")
                }
                // Unknown element: emit no tag; its text still flows through characters().
                else -> {}
            }
        }

        fun endElement(reader: XMLStreamReader) {
            val name = reader.localName
            if (name == "body") {
                if (bodyDepth > 0) {
                    html.append("</div>")
                    bodyDepth--
                }
                return
            }
            if (bodyDepth == 0) return

            when (name) {
                "section" -> {
                    html.append("</section>")
                    if (sectionDepth > 0) sectionDepth--
                }
                "title" -> {
                    html.append("</h").append(titleLevel).append(">")
                    inTitle = false
                    dropParagraph()
                }
                "p" -> if (inTitle) {
                    // opening emitted no <p>, so nothing to close
                } else {
                    html.append("</p>")
                    dropParagraph()
                }
                "subtitle", "text-author" -> {
                    html.append("</p>")
                    dropParagraph()
                }
                "epigraph" -> html.append("</div>")
                "cite" -> html.append("</blockquote>")
                "poem", "stanza" -> html.append("</div>")
                "v" -> {
                    html.append("</div>")
                    dropParagraph()
                }
                "emphasis" -> html.append("</em>")
                "strong" -> html.append("</strong>")
                "strikethrough" -> html.append("</s>")
                "sub" -> html.append("</sub>")
                "sup" -> html.append("</sup>")
                "code" -> html.append("</code>")
                "a" -> html.append(anchorClose.removeLastOrNull() ?: "</span>")
                "table" -> html.append("</table>")
                "tr" -> html.append("</tr>")
                "td" -> {
                    html.append("</td>")
                    dropParagraph()
                }
                "th" -> {
                    html.append("</th>")
                    dropParagraph()
                }
                // empty-line/image are emitted whole on start; unknown elements: nothing.
                else -> {}
            }
        }

        private fun dropParagraph() {
            if (paragraphDepth > 0) paragraphDepth--
        }

        private fun appendImage(reader: XMLStreamReader) {
            val href = reader.getAttributeValue(XLINK_NS, "href")
                ?: reader.getAttributeValue(null, "href")
                ?: return
            val dataUri = binaries[href.removePrefix("#")] ?: return
            // dataUri is validated in pass 1 (safe content-type + base64), so it needs no escaping.
            html.append("<img class=\"fb2-image\" alt=\"\" src=\"").append(dataUri).append("\">")
            imageCount++
        }
    }

    private companion object : Logger() {
        private const val XLINK_NS = "http://www.w3.org/1999/xlink"
        private const val DEFAULT_IMAGE_CONTENT_TYPE = "image/jpeg"

        // Budgets measured in base64 characters (≈ the bytes each image adds to the JSON payload).
        private const val MAX_SINGLE_IMAGE_BASE64_CHARS = 3 * 1024 * 1024
        private const val MAX_TOTAL_IMAGE_BASE64_CHARS = 8L * 1024 * 1024

        private val CONTENT_TYPE_RE = Regex("""^[\w.+-]+/[\w.+-]+$""")
        private val BASE64_RE = Regex("""^[A-Za-z0-9+/=\s]*$""")
    }
}

class RenderedBook(
    val html: String,
    val hasImages: Boolean,
    val truncated: Boolean,
)

private fun StringBuilder.appendEscaped(text: String): StringBuilder {
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
    return this
}
