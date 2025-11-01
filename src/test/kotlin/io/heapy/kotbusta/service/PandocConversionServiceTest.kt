package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.extensions.RequiresPandoc
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@RequiresPandoc
class PandocConversionServiceTest {
    private val conversionService = PandocConversionService()
    private val sampleDataPath = "src/test/resources/flibusta-sample"

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should convert FB2 to EPUB`() = runBlocking {
        // Given: Real FB2 file from test data
        val fb2File = File("$sampleDataPath/302859.fb2")
        assertTrue(fb2File.exists(), "Sample FB2 file should exist at ${fb2File.absolutePath}")
        log.info("Input FB2 file: ${fb2File.absolutePath} (${fb2File.length()} bytes)")

        // When: Converting to EPUB
        val outputFile = tempDir.resolve("302859.epub").toFile()
        val result = conversionService.convertFb2(
            inputFile = fb2File,
            outputFormat = "epub",
            outputFile = outputFile
        )

        // Then: Conversion should succeed
        assertTrue(result.success, "Conversion should succeed: ${result.errorMessage}")
        assertNull(result.errorMessage, "Should not have error message")
        assertNotNull(result.outputFile, "Output file should be set")
        assertTrue(outputFile.exists(), "EPUB file should be created")
        assertTrue(outputFile.length() > 0, "EPUB file should not be empty")

        log.info("✓ Successfully converted to EPUB: ${outputFile.length()} bytes")
    }

    @Test
    fun `should convert FB2 to HTML`() = runBlocking {
        // Given: Real FB2 file from test data
        val fb2File = File("$sampleDataPath/302859.fb2")
        assertTrue(fb2File.exists(), "Sample FB2 file should exist")

        // When: Converting to HTML
        val outputFile = tempDir.resolve("302859.html").toFile()
        val result = conversionService.convertFb2(
            inputFile = fb2File,
            outputFormat = "html",
            outputFile = outputFile
        )

        // Then: Conversion should succeed
        assertTrue(result.success, "Conversion should succeed: ${result.errorMessage}")
        assertNotNull(result.outputFile, "Output file should be set")
        assertTrue(outputFile.exists(), "HTML file should be created")
        assertTrue(outputFile.length() > 0, "HTML file should not be empty")

        log.info("✓ Successfully converted to HTML: ${outputFile.length()} bytes")
    }

    @Test
    fun `should fail for non-existent input file`() = runBlocking {
        // Given: Non-existent input file
        val nonExistentFile = File("/non/existent/file.fb2")
        val outputFile = tempDir.resolve("output.epub").toFile()

        // When: Attempting conversion
        val result = conversionService.convertFb2(
            inputFile = nonExistentFile,
            outputFormat = "epub",
            outputFile = outputFile
        )

        // Then: Should fail with error message
        assertFalse(result.success, "Conversion should fail")
        assertNotNull(result.errorMessage, "Should have error message")
        assertTrue(result.errorMessage!!.contains("does not exist"), "Error should mention file doesn't exist")

        log.info("✓ Correctly failed for non-existent file: ${result.errorMessage}")
    }

    @Test
    fun `should fail for unsupported format`() = runBlocking {
        // Given: Valid FB2 file
        val fb2File = File("$sampleDataPath/302859.fb2")
        val outputFile = tempDir.resolve("output.xyz").toFile()

        // When: Converting to unsupported format
        val result = conversionService.convertFb2(
            inputFile = fb2File,
            outputFormat = "xyz",
            outputFile = outputFile
        )

        // Then: Should fail
        assertFalse(result.success, "Conversion should fail for unsupported format")
        assertNotNull(result.errorMessage, "Should have error message")
        assertTrue(result.errorMessage!!.contains("Unsupported"), "Error should mention unsupported format")

        log.info("✓ Correctly failed for unsupported format: ${result.errorMessage}")
    }

    @Test
    fun `should list supported formats`() {
        // When: Getting supported formats
        val formats = conversionService.getSupportedFormats()

        // Then: Should include common formats
        assertTrue(formats.contains("epub"), "Should support EPUB")
        assertTrue(formats.contains("pdf"), "Should support PDF")
        assertTrue(formats.contains("html"), "Should support HTML")
        assertTrue(formats.contains("txt"), "Should support TXT")
        assertTrue(formats.contains("docx"), "Should support DOCX")

        log.info("✓ Supported formats: ${formats.joinToString(", ")}")
    }

    private companion object : Logger()
}
