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
}
