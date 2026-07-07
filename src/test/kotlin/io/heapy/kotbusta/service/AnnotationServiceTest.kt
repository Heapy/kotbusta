package io.heapy.kotbusta.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

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

    @Test
    fun `extractAnnotation decodes windows-1251 file that declares windows-1251`() {
        val fb2 = """<?xml version="1.0" encoding="windows-1251"?>""" +
            "<FictionBook><description><title-info><annotation>" +
            "<p>Приключенческий роман о путешествии.</p>" +
            "</annotation></title-info></description></FictionBook>"
        val bytes = fb2.toByteArray(Charset.forName("windows-1251"))

        val annotation = annotationService.extractAnnotation(ByteArrayInputStream(bytes))

        assertEquals("Приключенческий роман о путешествии.", annotation)
    }

    @Test
    fun `extractAnnotation decodes utf-8 file with BOM`() {
        val fb2 = "﻿" + """<?xml version="1.0" encoding="utf-8"?>""" +
            "<FictionBook><description><title-info><annotation>" +
            "<p>Русский текст аннотации.</p>" +
            "</annotation></title-info></description></FictionBook>"
        val bytes = fb2.toByteArray(Charsets.UTF_8)

        val annotation = annotationService.extractAnnotation(ByteArrayInputStream(bytes))

        assertEquals("Русский текст аннотации.", annotation)
    }

    @Test
    fun `extractAnnotation returns annotation even when the body is malformed`() {
        // A broken <body> must not affect extraction: the annotation is read from
        // <description> and parsing stops before the body is ever reached.
        val fb2 = "<FictionBook><description><title-info>" +
            "<annotation><p>Good annotation.</p></annotation>" +
            "</title-info></description>" +
            "<body><section><p>oops <b>unclosed</section></body></FictionBook>"

        val annotation = annotationService.extractAnnotation(fb2.byteInputStream())

        assertEquals("Good annotation.", annotation)
    }

    @Test
    fun `extractAnnotation returns null for annotation-less book with malformed body`() {
        // No annotation and a broken body: extraction stops at </description>, so it
        // returns null cleanly instead of failing on the body.
        val fb2 = "<FictionBook><description><title-info>" +
            "<book-title>Title</book-title></title-info></description>" +
            "<body><section><p>oops <b>unclosed</section></body></FictionBook>"

        val annotation = annotationService.extractAnnotation(fb2.byteInputStream())

        assertNull(annotation)
    }
}
