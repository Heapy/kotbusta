package io.heapy.kotbusta.service

import io.heapy.kotbusta.model.Author
import io.heapy.kotbusta.model.Book
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Instant

class BookFileServiceTest {
    @TempDir
    lateinit var booksDataPath: Path

    @Test
    fun `materialize returns fb2 entry from flibusta archive`() = runBlocking {
        writeArchive(archivePath = "fb2-001", entryName = "42.fb2", content = "<fb2>raw</fb2>")
        val service = ZipBookFileService(booksDataPath, RecordingConversionService())

        val materialized = service.materialize(book(), "fb2")

        try {
            assertEquals("fb2", materialized.format)
            assertEquals("Clean_Title.fb2", materialized.fileName)
            assertEquals("<fb2>raw</fb2>", materialized.file.readText())
        } finally {
            val tempDir = materialized.file.parentFile
            materialized.cleanup()
            assertFalse(tempDir.exists())
        }
    }

    @Test
    fun `materialize converts fb2 entry to epub`() = runBlocking {
        val conversionService = RecordingConversionService()
        writeArchive(archivePath = "fb2-001", entryName = "42.fb2", content = "<fb2>raw</fb2>")
        val service = ZipBookFileService(booksDataPath, conversionService)

        val materialized = service.materialize(book(), "epub")

        try {
            assertEquals("epub", materialized.format)
            assertEquals("Clean_Title.epub", materialized.fileName)
            assertEquals("converted:<fb2>raw</fb2>", materialized.file.readText())
            assertEquals("epub", conversionService.requests.single().outputFormat)
        } finally {
            materialized.cleanup()
        }
    }

    @Test
    fun `materialize preserves russian title in download filename`() = runBlocking {
        writeArchive(archivePath = "fb2-001", entryName = "42.fb2", content = "<fb2>raw</fb2>")
        val service = ZipBookFileService(booksDataPath, RecordingConversionService())

        val materialized = service.materialize(
            book(title = "Преступление и наказание"),
            "fb2",
        )

        try {
            assertEquals("Преступление_и_наказание.fb2", materialized.fileName)
        } finally {
            materialized.cleanup()
        }
    }

    @Test
    fun `materialize does not split astral character in truncated download filename`() = runBlocking {
        writeArchive(archivePath = "fb2-001", entryName = "42.fb2", content = "<fb2>raw</fb2>")
        val service = ZipBookFileService(booksDataPath, RecordingConversionService())
        val title = "A".repeat(99) + "🎉"

        val materialized = service.materialize(book(title = title), "fb2")

        try {
            assertEquals(
                materialized.fileName,
                String(materialized.fileName.toByteArray(Charsets.UTF_8), Charsets.UTF_8),
            )
            assertFalse(materialized.fileName.removeSuffix(".fb2").endsWith('.'))
            assertFalse(materialized.fileName.removeSuffix(".fb2").endsWith('_'))
        } finally {
            materialized.cleanup()
        }
    }

    @Test
    fun `materialize fails when archive entry is missing`() {
        writeArchive(archivePath = "fb2-001", entryName = "other.fb2", content = "other")
        val service = ZipBookFileService(booksDataPath, RecordingConversionService())

        val error = assertThrows(BookFileException::class.java) {
            runBlocking {
                service.materialize(book(), "fb2")
            }
        }

        assertTrue(error.message!!.contains("42.fb2"))
    }

    private fun writeArchive(
        archivePath: String,
        entryName: String,
        content: String,
    ) {
        val archive = booksDataPath.resolve("$archivePath.zip")
        Files.createDirectories(archive.parent)
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
    }

    private fun book(
        title: String = "Clean Title",
    ): Book =
        Book(
            id = 42,
            title = title,
            annotation = null,
            genres = listOf("Fantasy"),
            language = "en",
            authors = listOf(Author(id = 1, fullName = "Author")),
            series = null,
            seriesNumber = null,
            filePath = "42.fb2",
            archivePath = "fb2-001",
            fileSize = 13,
            dateAdded = Instant.parse("2024-01-01T00:00:00Z"),
            coverImageUrl = null,
        )

    private class RecordingConversionService : ConversionService {
        val requests = mutableListOf<Request>()

        override fun getSupportedFormats(): List<String> = listOf("epub")

        override fun isFormatSupported(format: String): Boolean =
            format.equals("epub", ignoreCase = true)

        override suspend fun convertFb2(
            inputFile: File,
            outputFormat: String,
            outputFile: File,
        ): ConversionResult {
            requests += Request(outputFormat)
            outputFile.writeText("converted:${inputFile.readText()}")
            return ConversionResult(success = true, outputFile = outputFile)
        }

        data class Request(val outputFormat: String)
    }
}
