package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

class AnnotationService {
    fun extractAnnotation(inputStream: InputStream): String? {
        val cleanedInputStream = cleanInputStream(inputStream)
        val xmlInputFactory = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_COALESCING, true)
            setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }

        return try {
            val reader = xmlInputFactory.createXMLStreamReader(cleanedInputStream)
            val text = StringBuilder()
            var annotationDepth = 0

            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        if (reader.localName == "annotation" || annotationDepth > 0) {
                            annotationDepth++
                        }
                    }

                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        if (annotationDepth > 0) {
                            text.append(reader.text).append(' ')
                        }
                    }

                    XMLStreamConstants.END_ELEMENT -> {
                        if (annotationDepth > 0) {
                            annotationDepth--
                            if (annotationDepth == 0) {
                                break
                            }
                        }
                    }
                }
            }
            reader.close()

            text.toString()
                .replace(Regex("\\s+"), " ")
                .replace(Regex("\\s+([,.;:!?])"), "$1")
                .trim()
                .takeIf(String::isNotBlank)
        } catch (e: Exception) {
            log.warn("Failed to extract FB2 annotation: ${e.message}", e)
            null
        }
    }

    private fun cleanInputStream(inputStream: InputStream): InputStream {
        return try {
            val bytes = inputStream.readAllBytes()
            val content = when {
                isValidUtf8(bytes) -> String(bytes, Charsets.UTF_8)
                else -> String(bytes, Charset.forName("windows-1251"))
            }
            val cleanContent = content.replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "")
            ByteArrayInputStream(cleanContent.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            log.warn("Failed to clean FB2 input stream: ${e.message}", e)
            inputStream
        }
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        return try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
            true
        } catch (_: Exception) {
            false
        }
    }

    private companion object : Logger()
}
