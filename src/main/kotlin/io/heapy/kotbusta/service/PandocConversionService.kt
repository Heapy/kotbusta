package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class PandocConversionService : ConversionService {

    companion object : Logger() {
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
        log.debug("Starting conversion: ${inputFile.name} -> $outputFormat")

        if (!inputFile.exists()) {
            log.warn("Input file does not exist: ${inputFile.absolutePath}")
            return@withContext ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "Input file does not exist: ${inputFile.absolutePath}"
            )
        }

        if (!isFormatSupported(outputFormat)) {
            log.warn("Unsupported output format: $outputFormat")
            return@withContext ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "Unsupported output format: $outputFormat. Supported formats: ${getSupportedFormats()}"
            )
        }

        if (!isPandocAvailable()) {
            log.error("Pandoc is not available")
            return@withContext ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "Pandoc is not installed or not available in PATH. Please install pandoc to use conversion features."
            )
        }

        try {
            outputFile.parentFile?.mkdirs()

            val pandocCommand = buildPandocCommand(inputFile, outputFile, outputFormat)
            log.debug("Executing pandoc command: ${pandocCommand.joinToString(" ")}")

            val processBuilder = ProcessBuilder(pandocCommand)
            processBuilder.directory(inputFile.parentFile)

            val process = processBuilder.start()
            val completed = process.waitFor(PANDOC_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (!completed) {
                log.warn("Conversion timed out after $PANDOC_TIMEOUT_SECONDS seconds")
                process.destroyForcibly()
                return@withContext ConversionResult(
                    success = false,
                    outputFile = null,
                    errorMessage = "Conversion timed out after $PANDOC_TIMEOUT_SECONDS seconds"
                )
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val errorOutput = process.errorStream.bufferedReader().use { it.readText() }
                log.error("Pandoc conversion failed with exit code $exitCode: $errorOutput")
                return@withContext ConversionResult(
                    success = false,
                    outputFile = null,
                    errorMessage = "Pandoc conversion failed with exit code $exitCode: $errorOutput"
                )
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                log.error("Conversion completed but output file is empty or missing")
                return@withContext ConversionResult(
                    success = false,
                    outputFile = null,
                    errorMessage = "Conversion completed but output file is empty or missing"
                )
            }

            log.info("Conversion successful: ${inputFile.name} -> ${outputFile.name} (${outputFile.length()} bytes)")
            ConversionResult(
                success = true,
                outputFile = outputFile
            )

        } catch (e: IOException) {
            log.error("IO error during conversion", e)
            ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "IO error during conversion: ${e.message}"
            )
        } catch (e: InterruptedException) {
            log.error("Conversion was interrupted", e)
            ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "Conversion was interrupted: ${e.message}"
            )
        } catch (e: Exception) {
            log.error("Unexpected error during conversion", e)
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
            log.error("Error while checking pandoc availability", e)
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
            inputFile.name, // Use relative name instead of absolute path
            "-f", "fb2",
            "-t", outputFormat.lowercase(),
            "-o", outputFile.absolutePath,
            "--extract-media=." // Extract media to current directory
        )

        when (outputFormat.lowercase()) {
            "pdf" -> {
                command.addAll(listOf("--pdf-engine=xelatex"))
            }
            "html" -> {
                command.addAll(listOf("--standalone", "--self-contained"))
            }
        }

        return command
    }

}
