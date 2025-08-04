package io.heapy.kotbusta.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class PandocConversionService : ConversionService {
    
    companion object {
        private const val PANDOC_TIMEOUT_SECONDS = 120L
        private val supportedFormats = listOf(
            ConversionFormat.EPUB,
            ConversionFormat.PDF,
            ConversionFormat.HTML,
            ConversionFormat.TXT,
            ConversionFormat.DOCX,
            ConversionFormat.RTF
        )
    }
    
    override fun getSupportedFormats(): List<String> {
        return supportedFormats.map { it.extension }
    }
    
    override fun isFormatSupported(format: String): Boolean {
        return supportedFormats.any { it.extension.equals(format, ignoreCase = true) }
    }
    
    override suspend fun convertFb2(
        inputFile: File,
        outputFormat: String,
        outputFile: File
    ): ConversionResult = withContext(Dispatchers.IO) {
        
        if (!inputFile.exists()) {
            return@withContext ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "Input file does not exist: ${inputFile.absolutePath}"
            )
        }
        
        if (!isFormatSupported(outputFormat)) {
            return@withContext ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "Unsupported output format: $outputFormat. Supported formats: ${getSupportedFormats()}"
            )
        }
        
        if (!isPandocAvailable()) {
            return@withContext ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "Pandoc is not installed or not available in PATH. Please install pandoc to use conversion features."
            )
        }
        
        try {
            outputFile.parentFile?.mkdirs()
            
            val pandocCommand = buildPandocCommand(inputFile, outputFile, outputFormat)
            val processBuilder = ProcessBuilder(pandocCommand)
            processBuilder.directory(inputFile.parentFile)
            
            val process = processBuilder.start()
            val completed = process.waitFor(PANDOC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            
            if (!completed) {
                process.destroyForcibly()
                return@withContext ConversionResult(
                    success = false,
                    outputFile = null,
                    errorMessage = "Conversion timed out after $PANDOC_TIMEOUT_SECONDS seconds"
                )
            }
            
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val errorOutput = process.errorStream.bufferedReader().readText()
                return@withContext ConversionResult(
                    success = false,
                    outputFile = null,
                    errorMessage = "Pandoc conversion failed with exit code $exitCode: $errorOutput"
                )
            }
            
            if (!outputFile.exists() || outputFile.length() == 0L) {
                return@withContext ConversionResult(
                    success = false,
                    outputFile = null,
                    errorMessage = "Conversion completed but output file is empty or missing"
                )
            }
            
            ConversionResult(
                success = true,
                outputFile = outputFile
            )
            
        } catch (e: IOException) {
            ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "IO error during conversion: ${e.message}"
            )
        } catch (e: InterruptedException) {
            ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "Conversion was interrupted: ${e.message}"
            )
        } catch (e: Exception) {
            ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "Unexpected error during conversion: ${e.message}"
            )
        }
    }
    
    private suspend fun isPandocAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("pandoc", "--version").start()
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            completed && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    private fun buildPandocCommand(
        inputFile: File,
        outputFile: File,
        outputFormat: String
    ): List<String> {
        val command = mutableListOf(
            "pandoc",
            inputFile.absolutePath,
            "-f", "fb2",
            "-t", outputFormat.lowercase(),
            "-o", outputFile.absolutePath
        )
        
        when (outputFormat.lowercase()) {
            "pdf" -> {
                command.addAll(listOf("--pdf-engine=xelatex"))
            }
            "epub" -> {
                command.addAll(listOf("--epub-metadata", createMetadataFile(inputFile)))
            }
            "html" -> {
                command.addAll(listOf("--standalone", "--self-contained"))
            }
        }
        
        return command
    }
    
    private fun createMetadataFile(inputFile: File): String {
        val metadataContent = """
            <?xml version="1.0"?>
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>${inputFile.nameWithoutExtension}</dc:title>
                <dc:creator>Unknown Author</dc:creator>
                <dc:language>en</dc:language>
            </metadata>
        """.trimIndent()
        
        val metadataFile = File.createTempFile("metadata_", ".xml")
        metadataFile.writeText(metadataContent)
        metadataFile.deleteOnExit()
        
        return metadataFile.absolutePath
    }
}