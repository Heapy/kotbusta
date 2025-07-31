package io.heapy.kotbusta.service

import java.io.File

interface ConversionService {
    fun getSupportedFormats(): List<String>
    
    suspend fun convertFb2(
        inputFile: File,
        outputFormat: String,
        outputFile: File
    ): ConversionResult
    
    fun isFormatSupported(format: String): Boolean
}

data class ConversionResult(
    val success: Boolean,
    val outputFile: File?,
    val errorMessage: String? = null
)

enum class ConversionFormat(val extension: String, val mimeType: String) {
    EPUB("epub", "application/epub+zip"),
    PDF("pdf", "application/pdf"),
    HTML("html", "text/html"),
    TXT("txt", "text/plain"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    RTF("rtf", "application/rtf"),
    MOBI("mobi", "application/x-mobipocket-ebook"),
    AZW3("azw3", "application/vnd.amazon.ebook")
}