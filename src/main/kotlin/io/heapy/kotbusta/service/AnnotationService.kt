package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.util.WHITESPACE_RUN
import io.heapy.kotbusta.util.decodeFb2Content
import io.heapy.kotbusta.util.newFb2XmlInputFactory
import java.io.InputStream
import java.io.StringReader
import javax.xml.stream.XMLStreamConstants

class AnnotationService {
    fun extractAnnotation(inputStream: InputStream): String? {
        val content = decodeFb2Content(inputStream) ?: return null
        val xmlInputFactory = newFb2XmlInputFactory()

        return try {
            // Parse from a character Reader (not the raw bytes) so StAX consumes the
            // already-decoded text and ignores the `encoding=` in the XML declaration.
            // decodeFb2Content already picked the right charset; letting the parser
            // re-guess it from the prolog is what produced the windows-1251 mojibake.
            val reader = xmlInputFactory.createXMLStreamReader(StringReader(content))
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
                        } else if (reader.localName == "description") {
                            // The annotation only lives inside <description>; once it
                            // closes we're done. This avoids scanning — and choking on
                            // a malformed or huge <body> for the books that have no
                            // annotation, which is common in the Flibusta dump.
                            break
                        }
                    }
                }
            }
            reader.close()

            text.toString()
                .replace(WHITESPACE_RUN, " ")
                .replace(Regex("""\s+([,.;:!?])"""), "$1")
                .trim()
                .takeIf(String::isNotBlank)
        } catch (e: Exception) {
            log.warn("Failed to extract FB2 annotation: ${e.message}", e)
            null
        }
    }

    private companion object : Logger()
}
