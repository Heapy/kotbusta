package io.heapy.kotbusta.parser

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.dslContext
import io.heapy.kotbusta.jooq.tables.references.BOOKS
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

class Fb2Parser(
    private val transactionProvider: TransactionProvider,
) {
    suspend fun extractBookCovers(archivePath: String) {
        log.info("Extracting covers from: $archivePath")

        val parallelism = Runtime.getRuntime().availableProcessors()
        log.info("Using $parallelism parallel workers for cover extraction")

        coroutineScope {
            ZipFile(archivePath).use { zipFile ->
                val entries = zipFile.entries().asSequence()
                    .filter { it.name.endsWith(".fb2") }
                    .toList()

                log.info("Processing ${entries.size} FB2 files for cover extraction")

                // Process entries in chunks to avoid too many concurrent operations
                entries.chunked(100).forEach { chunk ->
                    chunk.map { entry ->
                        async(Dispatchers.IO) {
                            try {
                                val bookId = entry.name.removeSuffix(".fb2").toLongOrNull()
                                if (bookId != null) {
                                    val bytes = zipFile.getInputStream(entry).use { it.readAllBytes() }
                                    val cleanedInputStream = cleanInputStream(ByteArrayInputStream(bytes))
                                    val coverImage = extractCoverFromFb2(cleanedInputStream)

                                    if (coverImage != null) {
                                        transactionProvider.transaction(READ_WRITE) {
                                            updateBookCover(bookId, coverImage)
                                        }
                                        log.debug("✅ Extracted cover for book $bookId")
                                        true
                                    } else {
                                        log.debug("⚠️  No cover found for book $bookId")
                                        false
                                    }
                                } else {
                                    false
                                }
                            } catch (e: Exception) {
                                log.warn("❌ Error processing ${entry.name}: ${e.message}")
                                false
                            }
                        }
                    }.awaitAll()

                    log.info("Processed chunk of ${chunk.size} files")
                }

                log.info("Cover extraction completed")
            }
        }
    }

    fun extractBookMetadata(archivePath: String, bookId: Long): BookMetadata? {
        ZipFile(archivePath).use { zipFile ->
            val entry = zipFile.getEntry("$bookId.fb2") ?: return null

            zipFile.getInputStream(entry).use { rawInputStream ->
                val cleanedInputStream = cleanInputStream(rawInputStream)
                return parseBookMetadata(cleanedInputStream)
            }
        }
    }

    private fun extractCoverFromFb2(inputStream: InputStream): ByteArray? {
        val xmlInputFactory = XMLInputFactory.newInstance()
        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true)
        xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true)
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false)

        try {
            // Try to auto-detect encoding instead of forcing UTF-8
            val reader = xmlInputFactory.createXMLStreamReader(inputStream)

            var inBinary = false
            var binaryId: String? = null
            var coverImageId: String? = null
            val binaryData = mutableMapOf<String, ByteArray>()

            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        when (reader.localName) {
                            "coverpage" -> {
                                // Look for image reference in coverpage
                                while (reader.hasNext() && reader.next() != XMLStreamConstants.END_ELEMENT) {
                                    if (reader.eventType == XMLStreamConstants.START_ELEMENT && reader.localName == "image") {
                                        val href = reader.getAttributeValue("http://www.w3.org/1999/xlink", "href")
                                            ?: reader.getAttributeValue(null, "href")
                                        if (href != null && href.startsWith("#")) {
                                            coverImageId = href.substring(1)
                                            break
                                        }
                                    }
                                }
                            }
                            "binary" -> {
                                inBinary = true
                                binaryId = reader.getAttributeValue(null, "id")
                            }
                        }
                    }
                    XMLStreamConstants.CHARACTERS -> {
                        if (inBinary && binaryId != null) {
                            val base64Data = reader.text.trim()
                            if (base64Data.isNotEmpty()) {
                                try {
                                    val imageData = kotlin.io.encoding.Base64.Mime.decode(base64Data)
                                    binaryData[binaryId] = imageData
                                } catch (e: Exception) {
                                    LoggerFactory.getLogger(Fb2Parser::class.java)
                                        .error("Base64 decoding error", e)
                                }
                            }
                        }
                    }
                    XMLStreamConstants.END_ELEMENT -> {
                        if (reader.localName == "binary") {
                            inBinary = false
                            binaryId = null
                        }
                    }
                }
            }

            reader.close()

            // Return cover image if found
            return if (coverImageId != null && binaryData.containsKey(coverImageId)) {
                binaryData[coverImageId]
            } else {
                // Try to find any image
                binaryData.values.firstOrNull()
            }

        } catch (e: Exception) {
            log.error("Error extracting cover: ${e.message}", e)
            return null
        }
    }

    private fun cleanInputStream(inputStream: InputStream): InputStream {
        return try {
            // Read all bytes first
            val bytes = inputStream.readAllBytes()

            // Try to detect and fix encoding issues
            val content = when {
                // Try UTF-8 first
                isValidUtf8(bytes) -> String(bytes, Charsets.UTF_8)
                // Fall back to Windows-1251 (common in Russian texts)
                else -> String(bytes, Charset.forName("windows-1251"))
            }

            // Clean up any invalid XML characters
            val cleanContent = content
                .replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "") // Remove control characters

            ByteArrayInputStream(cleanContent.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            // If cleaning fails, return original stream
            inputStream
        }
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
            decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun parseBookMetadata(inputStream: InputStream): BookMetadata? {
        val xmlInputFactory = XMLInputFactory.newInstance()
        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true)
        xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true)
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false)

        try {
            // Try to auto-detect encoding instead of forcing UTF-8
            val reader = xmlInputFactory.createXMLStreamReader(inputStream)

            var title: String? = null
            var annotation: String? = null
            var inAnnotation = false
            val annotationBuilder = StringBuilder()

            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        when (reader.localName) {
                            "book-title" -> {
                                if (reader.hasNext()) {
                                    reader.next()
                                    if (reader.eventType == XMLStreamConstants.CHARACTERS) {
                                        title = reader.text.trim()
                                    }
                                }
                            }
                            "annotation" -> {
                                inAnnotation = true
                                annotationBuilder.clear()
                            }
                        }
                    }
                    XMLStreamConstants.CHARACTERS -> {
                        if (inAnnotation) {
                            annotationBuilder.append(reader.text)
                        }
                    }
                    XMLStreamConstants.END_ELEMENT -> {
                        if (reader.localName == "annotation") {
                            inAnnotation = false
                            annotation = annotationBuilder.toString().trim()
                        }
                        // Stop after title-info section
                        else if (reader.localName == "title-info") {
                            break
                        }
                    }
                }
            }

            reader.close()

            return if (title != null) {
                BookMetadata(title, annotation)
            } else null

        } catch (e: Exception) {
            log.error("Error parsing book metadata: ${e.message}", e)
            return null
        }
    }

    context(_: TransactionContext)
    private fun updateBookCover(bookId: Long, coverImage: ByteArray) = dslContext { dslContext ->
        dslContext
            .update(BOOKS)
            .set(BOOKS.COVER_IMAGE, coverImage)
            .where(BOOKS.ID.eq(bookId))
            .execute()
    }

    context(_: TransactionContext)
    private fun updateBookAnnotation(bookId: Long, annotation: String) = dslContext { dslContext ->
        dslContext
            .update(BOOKS)
            .set(BOOKS.ANNOTATION, annotation)
            .where(BOOKS.ID.eq(bookId))
            .execute()
    }

    private companion object : Logger()
}

data class BookMetadata(
    val title: String,
    val annotation: String?
)
