package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.model.Book
import io.heapy.kotbusta.model.FbElement
import io.heapy.kotbusta.model.FbNode
import io.heapy.kotbusta.model.FbText
import io.heapy.kotbusta.model.TocEntry
import io.heapy.kotbusta.util.WHITESPACE_RUN
import io.heapy.kotbusta.util.decodeFb2Content
import io.heapy.kotbusta.util.newFb2XmlInputFactory
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

/** Parses a book's FB2 body into a paginated tree for the in-app reader. */
interface BookContentParser {
    suspend fun render(book: Book): ParsedBook
}

/**
 * Renders a book's FB2 body into a sequence of pages for the in-app reader.
 *
 * Resolves the FB2 from the on-disk archive layout (`${booksDataPath}/${archivePath}.zip`,
 * entry `filePath`) exactly like [ZipBookFileService], decodes it with [decodeFb2Content]
 * (charset-correct, so Cyrillic renders without mojibake), then walks it with StAX in two
 * passes over the same decoded text: pass 1 collects inlineable `<binary>` images, pass 2
 * builds a tree of [FbNode]s for the `<body>`, split into pages at chapter (top-level
 * `<section>`) boundaries or, for an unusually large chapter, at a size budget.
 *
 * Security: the tree is a fixed whitelist of element tags/attributes built directly from
 * typed data, never a string the frontend parses as markup, so FB2 content can never be
 * used to inject executable markup into the reader. `SUPPORT_DTD=false` + external
 * entities off closes XXE.
 */
class BookContentService(
    private val booksDataPath: Path,
) : BookContentParser {
    override suspend fun render(book: Book): ParsedBook = withContext(Dispatchers.IO) {
        val archiveFile = booksDataPath.resolve("${book.archivePath}.zip")
        if (!archiveFile.exists()) {
            throw BookFileException("Book archive not found: ${book.archivePath}.zip")
        }

        val content = try {
            ZipFile(archiveFile.toFile()).use { zip ->
                val entry = resolveFb2Entry(zip, book)
                    ?: throw BookFileException("FB2 entry '${book.filePath}' not found in ${book.archivePath}.zip")
                zip.getInputStream(entry).use { decodeFb2Content(it) }
            }
        } catch (e: IOException) {
            // A corrupt/truncated archive surfaces as ZipException (an IOException). That's a
            // content-unavailable case, not a server fault, so map it to the BookFileException
            // the route already turns into a 404 instead of letting it become a 500.
            throw BookFileException("Failed to read ${book.archivePath}.zip: ${e.message}")
        } ?: return@withContext ParsedBook(
            pages = emptyList(),
            toc = emptyList(),
            anchorPageIndex = emptyMap(),
            hasImages = false,
        )

        renderContent(content)
    }

    internal fun renderContent(content: String): ParsedBook {
        val factory = newFb2XmlInputFactory()
        val binaries = collectBinaries(content, factory)
        val body = buildBodyNodes(content, factory, binaries)
        return ParsedBook(
            pages = body.pages,
            toc = body.toc,
            anchorPageIndex = body.anchorPageIndex,
            hasImages = body.imageCount > 0,
        )
    }

    /**
     * Pass 1: collect `<binary>` images as ready-to-embed `data:` URIs. `<binary>` elements
     * appear after `<body>`, so a separate pass builds the id→URI map the body pass resolves
     * against. Every embeddable binary is included — responses are now paginated, so there's
     * no whole-book payload size to protect the way there used to be.
     */
    private fun collectBinaries(content: String, factory: XMLInputFactory): Map<String, String> {
        val byId = HashMap<String, String>()
        val coverIds = HashSet<String>()

        var reader: XMLStreamReader? = null
        try {
            val xml = factory.createXMLStreamReader(StringReader(content))
            reader = xml
            var inBinary = false
            var coverpageDepth = 0
            var id: String? = null
            var contentType = DEFAULT_IMAGE_CONTENT_TYPE
            while (xml.hasNext()) {
                when (xml.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (xml.localName) {
                        "coverpage" -> coverpageDepth++
                        "image" -> if (coverpageDepth > 0) {
                            val href = xml.getAttributeValue(XLINK_NS, "href")
                                ?: xml.getAttributeValue(null, "href")
                            href?.removePrefix("#")?.takeIf(String::isNotBlank)?.let(coverIds::add)
                        }
                        "binary" -> {
                            inBinary = true
                            id = xml.getAttributeValue(null, "id")
                            val declared = xml.getAttributeValue(null, "content-type")?.trim()
                            contentType = if (!declared.isNullOrBlank() && CONTENT_TYPE_RE.matches(declared)) {
                                declared
                            } else {
                                DEFAULT_IMAGE_CONTENT_TYPE
                            }
                        }
                        else -> {}
                    }

                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        val key = id
                        if (inBinary && key != null && key !in coverIds && key !in byId) {
                            val raw = xml.text
                            // Non-base64 payloads can't safely become a data: URI; skip them.
                            if (raw.isNotBlank() && BASE64_RE.matches(raw)) {
                                byId[key] = "data:$contentType;base64,${raw.replace(WHITESPACE_RUN, "")}"
                            }
                        }
                    }

                    XMLStreamConstants.END_ELEMENT -> when (xml.localName) {
                        "coverpage" -> if (coverpageDepth > 0) coverpageDepth--
                        "binary" -> {
                            inBinary = false
                            id = null
                            contentType = DEFAULT_IMAGE_CONTENT_TYPE
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Failed while collecting FB2 binaries: ${e.message}", e)
        } finally {
            reader?.close()
        }

        return byId
    }

    /**
     * Pass 2: convert the FB2 `<body>` into a tree of pages. On a mid-parse failure (e.g.
     * malformed XML) whatever pages were accumulated so far are returned — a partial book
     * beats none.
     */
    private fun buildBodyNodes(
        content: String,
        factory: XMLInputFactory,
        binaries: Map<String, String>,
    ): BodyNodes {
        val writer = Fb2NodeWriter(binaries)
        var reader: XMLStreamReader? = null
        try {
            val xml = factory.createXMLStreamReader(StringReader(content))
            reader = xml
            while (xml.hasNext()) {
                when (xml.next()) {
                    XMLStreamConstants.START_ELEMENT -> writer.startElement(xml)
                    XMLStreamConstants.END_ELEMENT -> writer.endElement(xml)
                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> writer.characters(xml.text)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed while rendering FB2 body (returning partial content): ${e.message}", e)
        } finally {
            reader?.close()
        }
        writer.finish()
        return BodyNodes(writer.pages, writer.toc, writer.anchorPageIndex, writer.imageCount)
    }

    private class BodyNodes(
        val pages: List<ParsedPage>,
        val toc: List<TocEntry>,
        val anchorPageIndex: Map<String, Int>,
        val imageCount: Int,
    )

    /**
     * Streams FB2 body events into a tree of [FbNode]s, splitting into [ParsedPage]s along
     * the way. Only a fixed whitelist of FB2 elements produce nodes; everything else is a
     * no-op wrapper whose text still flows into whatever's currently open.
     *
     * Every currently-open element (from the `<body>` wrapper down to wherever the parser
     * currently is) lives on [elementStack] as a mutable [NodeBuilder]. A page break
     * ([breakPage]) freezes that whole chain into one immutable tree for the completed page,
     * then replaces every entry on the stack with a fresh same-shape builder so subsequent
     * content continues seamlessly into the next page. Because this operates on typed nodes
     * rather than markup text, there's no way for a break to produce something "unbalanced" —
     * a page's content is always a well-formed tree.
     *
     * Breaks only ever happen at [paragraphDepth] `== 0`, i.e. between complete leaf elements
     * (paragraphs, verses, table cells, ...) — never inside one — so a page never splits a
     * sentence mid-way.
     */
    private class Fb2NodeWriter(private val binaries: Map<String, String>) {
        val pages = mutableListOf<ParsedPage>()
        val toc = mutableListOf<TocEntry>()
        val anchorPageIndex = mutableMapOf<String, Int>()
        var imageCount = 0
            private set

        private val elementStack = ArrayDeque<NodeBuilder>()
        private var currentPlainText = StringBuilder()
        private var estimatedSizeChars = 0
        private var pageHasContent = false

        private var bodyDepth = 0
        private var sectionDepth = 0
        private var paragraphDepth = 0
        private var inTitle = false
        private var titleLevel = 0
        private var titleParaCount = 0
        private var suppressChapterBreaks = false

        fun characters(text: String) {
            if (bodyDepth > 0 && paragraphDepth > 0) {
                appendText(text)
                currentPlainText.append(text)
                estimatedSizeChars += text.length
            }
        }

        fun startElement(reader: XMLStreamReader) {
            val name = reader.localName
            if (name == "body") {
                if (pageHasContent) breakPage()
                elementStack.clear()
                sectionDepth = 0
                bodyDepth++
                val bodyName = reader.getAttributeValue(null, "name")
                val notes = bodyName == "notes" || bodyName == "comments"
                suppressChapterBreaks = notes
                elementStack.addLast(NodeBuilder("div", className = if (notes) "fb2-body fb2-notes" else "fb2-body"))
                if (notes) {
                    toc.add(TocEntry(title = "Notes", level = 1, page = pages.size + 1))
                }
                return
            }
            if (bodyDepth == 0) return

            when (name) {
                "section" -> {
                    if (sectionDepth == 0 && !suppressChapterBreaks && pageHasContent) breakPage()
                    sectionDepth++
                    push("section", className = "fb2-section", id = reader.idAttr())
                }
                "title" -> {
                    titleLevel = minOf(sectionDepth + 1, 6)
                    inTitle = true
                    titleParaCount = 0
                    paragraphDepth++
                    push("h$titleLevel", className = "fb2-title", id = reader.idAttr())
                }
                "p" -> if (inTitle) {
                    if (titleParaCount > 0) appendChild(FbElement(tag = "br"))
                    titleParaCount++
                } else {
                    paragraphDepth++
                    push("p", id = reader.idAttr())
                }
                "subtitle" -> {
                    paragraphDepth++
                    push("p", className = "fb2-subtitle", id = reader.idAttr())
                }
                "epigraph" -> push("div", className = "fb2-epigraph", id = reader.idAttr())
                "cite" -> push("blockquote", className = "fb2-cite", id = reader.idAttr())
                "text-author" -> {
                    paragraphDepth++
                    push("p", className = "fb2-text-author", id = reader.idAttr())
                }
                "poem" -> push("div", className = "fb2-poem", id = reader.idAttr())
                "stanza" -> push("div", className = "fb2-stanza", id = reader.idAttr())
                "v" -> {
                    paragraphDepth++
                    push("div", className = "fb2-verse", id = reader.idAttr())
                }
                "empty-line" -> appendChild(FbElement(tag = "div", className = "fb2-empty-line"))
                "emphasis" -> push("em")
                "strong" -> push("strong")
                "strikethrough" -> push("s")
                "sub" -> push("sub")
                "sup" -> push("sup")
                "code" -> push("code")
                "image" -> appendImageNode(reader)
                "a" -> {
                    val href = reader.getAttributeValue(XLINK_NS, "href")
                        ?: reader.getAttributeValue(null, "href")
                    if (href != null && href.startsWith("#")) {
                        push("a", href = href)
                    } else {
                        push("span")
                    }
                }
                "table" -> push("table", className = "fb2-table")
                "tr" -> push("tr")
                "td" -> {
                    paragraphDepth++
                    push("td", id = reader.idAttr())
                }
                "th" -> {
                    paragraphDepth++
                    push("th", id = reader.idAttr())
                }
                // Unknown element: emit no node; its text still flows through characters().
                else -> {}
            }
        }

        fun endElement(reader: XMLStreamReader) {
            val name = reader.localName
            if (name == "body") {
                if (bodyDepth > 0) bodyDepth--
                return
            }
            if (bodyDepth == 0) return

            when (name) {
                "section" -> {
                    pop()
                    if (sectionDepth > 0) sectionDepth--
                }
                "title" -> {
                    val label = elementStack.lastOrNull()?.children?.plainTextOf()?.trim().orEmpty()
                    pop()
                    if (label.isNotEmpty()) {
                        toc.add(TocEntry(title = label, level = titleLevel, page = pages.size + 1))
                    }
                    inTitle = false
                    dropParagraph()
                }
                "p" -> if (!inTitle) {
                    pop()
                    dropParagraph()
                }
                "subtitle", "text-author" -> {
                    pop()
                    dropParagraph()
                }
                "epigraph", "cite", "poem", "stanza" -> pop()
                "v" -> {
                    pop()
                    dropParagraph()
                }
                "emphasis", "strong", "strikethrough", "sub", "sup", "code", "a" -> pop()
                "table", "tr" -> pop()
                "td", "th" -> {
                    pop()
                    dropParagraph()
                }
                // empty-line/image are emitted whole on start; unknown elements: nothing.
                else -> {}
            }
            maybeBreakPage()
        }

        /** Flushes whatever's left as the final page once the whole document has been walked. */
        fun finish() {
            breakPage()
        }

        private fun dropParagraph() {
            if (paragraphDepth > 0) paragraphDepth--
        }

        private fun maybeBreakPage() {
            if (paragraphDepth == 0 && estimatedSizeChars >= PAGE_SIZE_BUDGET_CHARS) breakPage()
        }

        private fun push(tag: String, className: String? = null, id: String? = null, href: String? = null) {
            if (id != null) anchorPageIndex[id] = pages.size + 1
            elementStack.addLast(NodeBuilder(tag, className, id, href))
        }

        private fun pop() {
            val builder = elementStack.removeLast()
            appendChild(builder.freezeWith(null))
        }

        private fun appendChild(node: FbNode) {
            val parent = elementStack.lastOrNull() ?: return
            parent.children.add(node)
            pageHasContent = true
        }

        private fun appendText(text: String) {
            val parent = elementStack.lastOrNull() ?: return
            val last = parent.children.lastOrNull()
            if (last is FbText) {
                parent.children[parent.children.size - 1] = FbText(last.value + text)
            } else {
                parent.children.add(FbText(text))
            }
            pageHasContent = true
        }

        private fun appendImageNode(reader: XMLStreamReader) {
            val href = reader.getAttributeValue(XLINK_NS, "href")
                ?: reader.getAttributeValue(null, "href")
                ?: return
            val dataUri = binaries[href.removePrefix("#")] ?: return
            appendChild(FbElement(tag = "img", className = "fb2-image", src = dataUri))
            imageCount++
        }

        /**
         * Closes out the current page: freezes the whole currently-open ancestor chain
         * (from the `<body>` wrapper down to wherever the parser currently is) into one
         * immutable tree, then replaces every entry on [elementStack] with a fresh
         * same-tag/class/id builder so subsequent content continues into the next page.
         */
        private fun breakPage() {
            if (!pageHasContent || elementStack.isEmpty()) return
            pages.add(ParsedPage(nodes = listOf(freezeChain()), plainText = currentPlainText.toString()))
            for (i in elementStack.indices) {
                val old = elementStack[i]
                elementStack[i] = NodeBuilder(old.tag, old.className, old.id, old.href)
            }
            currentPlainText = StringBuilder()
            estimatedSizeChars = 0
            pageHasContent = false
        }

        private fun freezeChain(): FbNode {
            var acc: FbNode? = null
            for (i in elementStack.indices.reversed()) {
                acc = elementStack[i].freezeWith(acc)
            }
            return acc!!
        }

        private fun List<FbNode>.plainTextOf(): String = joinToString("") { node ->
            when (node) {
                is FbText -> node.value
                is FbElement -> node.children.plainTextOf()
            }
        }

        /** A currently-open element; frozen into an immutable [FbElement] when it closes. */
        private class NodeBuilder(
            val tag: String,
            val className: String? = null,
            val id: String? = null,
            val href: String? = null,
        ) {
            val children = mutableListOf<FbNode>()

            fun freezeWith(openChild: FbNode?): FbElement = FbElement(
                tag = tag,
                className = className,
                id = id,
                href = href,
                children = if (openChild != null) children + openChild else children.toList(),
            )
        }
    }

    private companion object : Logger() {
        private const val XLINK_NS = "http://www.w3.org/1999/xlink"
        private const val DEFAULT_IMAGE_CONTENT_TYPE = "image/jpeg"

        // Soft target for how much rendered text a page carries before a chapter that's
        // still open gets split into a continuation page. Roughly a real book chapter's
        // worth; a single leaf (e.g. one giant <p>) is never split to stay under it.
        private const val PAGE_SIZE_BUDGET_CHARS = 40_000

        private val CONTENT_TYPE_RE = Regex("""^[\w.+-]+/[\w.+-]+$""")
        private val BASE64_RE = Regex("""^[A-Za-z0-9+/=\s]*$""")
    }
}

class ParsedBook(
    val pages: List<ParsedPage>,
    val toc: List<TocEntry>,
    val anchorPageIndex: Map<String, Int>,
    val hasImages: Boolean,
)

class ParsedPage(
    val nodes: List<FbNode>,
    val plainText: String,
)

private fun XMLStreamReader.idAttr(): String? =
    getAttributeValue(null, "id")?.takeIf(String::isNotBlank)
