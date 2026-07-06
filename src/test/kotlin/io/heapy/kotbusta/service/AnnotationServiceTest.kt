package io.heapy.kotbusta.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class AnnotationServiceTest {
    private val annotationService = AnnotationService()

    @Test
    fun `extractAnnotation returns normalized text from first annotation`() {
        val fb2 = """
            <FictionBook>
              <description>
                <title-info>
                  <annotation>
                    <p> First paragraph. </p>
                    <p>Second <strong>paragraph</strong>.</p>
                  </annotation>
                  <annotation><p>Ignored second annotation.</p></annotation>
                </title-info>
              </description>
            </FictionBook>
        """.trimIndent()

        val annotation = annotationService.extractAnnotation(fb2.byteInputStream())

        assertEquals("First paragraph. Second paragraph.", annotation)
    }

    @Test
    fun `extractAnnotation strips invalid xml control characters`() {
        val fb2 = "<FictionBook><annotation><p>Hello\u0000 world</p></annotation></FictionBook>"

        val annotation = annotationService.extractAnnotation(fb2.byteInputStream())

        assertEquals("Hello world", annotation)
    }

    @Test
    fun `extractAnnotation returns null when annotation is absent`() {
        val fb2 = "<FictionBook><description /></FictionBook>"

        val annotation = annotationService.extractAnnotation(ByteArrayInputStream(fb2.toByteArray()))

        assertNull(annotation)
    }
}
