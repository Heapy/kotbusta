package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * Service for on-demand extraction of book covers from FB2 archives.
 * Covers are extracted when requested rather than during import to reduce storage and import time.
 */
class CoverService {
    /**
     * Extract cover image for a specific book from its archive.
     *
     * @param archivePath Path to the ZIP archive containing FB2 files
     * @param bookId ID of the book to extract cover for
     * @return Cover image as byte array, or null if not found
     */
    fun extractCoverForBook(archivePath: String, bookId: Int): ByteArray? {
        return try {
            ZipFile(archivePath).use { zipFile ->
                val entry = zipFile.getEntry("$bookId.fb2") ?: run {
                    log.debug("No FB2 entry found for book $bookId in $archivePath")
                    return null
                }

                val bytes = zipFile.getInputStream(entry).use { it.readAllBytes() }
                val cleanedInputStream = cleanInputStream(ByteArrayInputStream(bytes))
                val coverImage = extractCoverFromFb2(cleanedInputStream)

                if (coverImage != null) {
                    log.debug("Successfully extracted cover for book $bookId (${coverImage.size} bytes)")
                } else {
                    log.debug("No cover found in FB2 file for book $bookId")
                }

                coverImage
            }
        } catch (e: Exception) {
            log.warn("Error extracting cover for book $bookId from $archivePath: ${e.message}", e)
            null
        }
    }

    private fun extractCoverFromFb2(inputStream: InputStream): ByteArray? {
        val xmlInputFactory = XMLInputFactory.newInstance()
        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true)
        xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true)
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false)

        try {
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
                                    log.error("Base64 decoding error for binary $binaryId", e)
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
                // Try to find any image as fallback
                binaryData.values.firstOrNull()
            }

        } catch (e: Exception) {
            log.error("Error extracting cover: ${e.message}", e)
            return null
        }
    }

    private fun cleanInputStream(inputStream: InputStream): InputStream {
        return try {
            val bytes = inputStream.readAllBytes()

            // Try to detect and fix encoding issues
            val content = when {
                isValidUtf8(bytes) -> String(bytes, Charsets.UTF_8)
                else -> String(bytes, Charset.forName("windows-1251"))
            }

            // Clean up invalid XML characters
            val cleanContent = content.replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "")

            ByteArrayInputStream(cleanContent.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            log.warn("Failed to clean input stream: ${e.message}")
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

    private companion object : Logger()
}
