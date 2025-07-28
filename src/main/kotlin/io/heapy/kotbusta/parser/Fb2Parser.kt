package io.heapy.kotbusta.parser

import io.heapy.kotbusta.database.DatabaseInitializer
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.sql.Connection
import java.util.zip.ZipFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

class Fb2Parser {
    
    fun extractBookCovers(archivePath: String) {
        println("Extracting covers from: $archivePath")
        
        val connection = DatabaseInitializer.getConnection()
        connection.use { conn ->
            conn.autoCommit = false
            
            ZipFile(archivePath).use { zipFile ->
                val entries = zipFile.entries().asSequence()
                    .filter { it.name.endsWith(".fb2") }
                    .take(100) // Process first 100 for testing
                    .toList()
                
                println("Processing ${entries.size} FB2 files for cover extraction")
                
                entries.forEachIndexed { index, entry ->
                    val bookId = entry.name.removeSuffix(".fb2").toLongOrNull()
                    if (bookId != null) {
                        zipFile.getInputStream(entry).use { inputStream ->
                            val coverImage = extractCoverFromFb2(inputStream)
                            if (coverImage != null) {
                                updateBookCover(conn, bookId, coverImage)
                                println("Extracted cover for book $bookId")
                            }
                        }
                    }
                    
                    if ((index + 1) % 50 == 0) {
                        conn.commit()
                        println("Committed cover batch ${index + 1}")
                    }
                }
                
                conn.commit()
                println("Cover extraction completed")
            }
        }
    }
    
    fun extractBookMetadata(archivePath: String, bookId: Long): BookMetadata? {
        ZipFile(archivePath).use { zipFile ->
            val entry = zipFile.getEntry("$bookId.fb2") ?: return null
            
            zipFile.getInputStream(entry).use { inputStream ->
                return parseBookMetadata(inputStream)
            }
        }
    }
    
    private fun extractCoverFromFb2(inputStream: InputStream): ByteArray? {
        val xmlInputFactory = XMLInputFactory.newInstance()
        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true)
        
        try {
            val reader = xmlInputFactory.createXMLStreamReader(inputStream, "UTF-8")
            
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
                                    val imageData = java.util.Base64.getDecoder().decode(base64Data)
                                    binaryData[binaryId] = imageData
                                } catch (e: Exception) {
                                    // Invalid base64, ignore
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
            System.err.println("Error extracting cover: ${e.message}")
            return null
        }
    }
    
    private fun parseBookMetadata(inputStream: InputStream): BookMetadata? {
        val xmlInputFactory = XMLInputFactory.newInstance()
        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true)
        
        try {
            val reader = xmlInputFactory.createXMLStreamReader(inputStream, "UTF-8")
            
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
            System.err.println("Error parsing book metadata: ${e.message}")
            return null
        }
    }
    
    private fun updateBookCover(connection: Connection, bookId: Long, coverImage: ByteArray) {
        val sql = "UPDATE books SET cover_image = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setBytes(1, coverImage)
            stmt.setLong(2, bookId)
            stmt.executeUpdate()
        }
    }
    
    private fun updateBookAnnotation(connection: Connection, bookId: Long, annotation: String) {
        val sql = "UPDATE books SET annotation = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, annotation)
            stmt.setLong(2, bookId)
            stmt.executeUpdate()
        }
    }
}

data class BookMetadata(
    val title: String,
    val annotation: String?
)