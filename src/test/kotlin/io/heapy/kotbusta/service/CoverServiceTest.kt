package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream

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

    private val syntheticImage =
        byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3, 4, 5, 6, 7, 8)

    private fun fb2(prolog: String): String =
        prolog +
            "<FictionBook xmlns:xlink=\"http://www.w3.org/1999/xlink\">" +
            "<description><title-info>" +
            "<book-title>Русское название книги</book-title>" +
            "<coverpage><image xlink:href=\"#cover.bin\"/></coverpage>" +
            "</title-info></description>" +
            "<body><section><p>Немного русского текста в теле книги.</p></section></body>" +
            "<binary id=\"cover.bin\" content-type=\"image/png\">" +
            Base64.getEncoder().encodeToString(syntheticImage) +
            "</binary></FictionBook>"

    private fun writeZip(dir: Path, bookId: Int, bytes: ByteArray): String {
        val zipPath = dir.resolve("archive.zip")
        ZipOutputStream(zipPath.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("$bookId.fb2"))
            zip.write(bytes)
            zip.closeEntry()
        }
        return zipPath.toString()
    }

    @Test
    fun `extractCoverForBook reads cover from windows-1251 fb2`(@TempDir dir: Path) {
        val bytes = fb2("""<?xml version="1.0" encoding="windows-1251"?>""")
            .toByteArray(Charset.forName("windows-1251"))
        val zipPath = writeZip(dir, 77, bytes)

        assertArrayEquals(syntheticImage, coverService.extractCoverForBook(zipPath, 77))
    }

    @Test
    fun `extractCoverForBook reads cover from UTF-16LE fb2`(@TempDir dir: Path) {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bytes = bom + fb2("""<?xml version="1.0" encoding="utf-16"?>""").toByteArray(Charsets.UTF_16LE)
        val zipPath = writeZip(dir, 88, bytes)

        assertArrayEquals(syntheticImage, coverService.extractCoverForBook(zipPath, 88))
    }

    private companion object : Logger()
}
