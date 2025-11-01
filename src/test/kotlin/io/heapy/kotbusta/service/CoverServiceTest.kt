package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CoverServiceTest {
    private val coverService = CoverService()
    private val sampleDataPath = "src/test/resources/flibusta-sample"

    @Test
    fun `should extract cover from real FB2 archive`() {
        val archivePath = "$sampleDataPath/fb2-test-3.zip"
        val archiveFile = File(archivePath)

        assertTrue(archiveFile.exists(), "Sample archive should exist at $archivePath")

        val testBookId = 79797
        val cover = coverService.extractCoverForBook(archivePath, testBookId)

        assertNotNull(cover, "Cover should be extracted")
    }

    @Test
    fun `should return null for non-existent book in archive`() {
        val archivePath = "$sampleDataPath/fb2-test-1.zip"
        val archiveFile = File(archivePath)

        assertTrue(archiveFile.exists(), "Sample archive should exist")

        // Try to extract cover for a book ID that doesn't exist
        val nonExistentBookId = 999999999
        val cover = coverService.extractCoverForBook(archivePath, nonExistentBookId)

        assertNull(cover, "Should return null for non-existent book")
    }

    @Test
    fun `should return null for non-existent archive`() {
        val cover = coverService.extractCoverForBook("/non/existent/archive.zip", 123)
        assertNull(cover, "Should return null for non-existent archive")
        log.info("✓ Correctly returned null for non-existent archive")
    }

    private companion object : Logger()
}
