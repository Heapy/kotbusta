package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.model.Book
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.exists

/**
 * Resolves a book file (optionally converting it) for download or Kindle delivery.
 */
interface BookFileService {
    /**
     * Materializes [book] into [format] as a file on disk. The caller owns the
     * returned result's temp directory and MUST call [MaterializedBook.cleanup].
     */
    suspend fun materialize(book: Book, format: String): MaterializedBook
}

class MaterializedBook(
    val file: File,
    val fileName: String,
    val format: String,
    private val tempDir: File,
) {
    fun cleanup() {
        tempDir.deleteRecursively()
    }
}

/**
 * Resolves a book's bytes from the on-disk archive layout
 * (`${booksDataPath}/${archivePath}.zip`, entry `filePath`) and, when a
 * non-FB2 format is requested, converts the extracted FB2 via [ConversionService].
 *
 * Shared by the download route and the Kindle send worker so both go through the
 * exact same resolve-and-convert path. Each call works in its own temp directory
 * to avoid collisions between concurrent requests.
 */
class ZipBookFileService(
    private val booksDataPath: Path,
    private val conversionService: ConversionService,
) : BookFileService {
    override suspend fun materialize(book: Book, format: String): MaterializedBook {
        val normalizedFormat = format.lowercase()
        val archiveFile = booksDataPath.resolve("${book.archivePath}.zip")
        if (!archiveFile.exists()) {
            throw BookFileException("Book archive not found: ${book.archivePath}.zip")
        }

        val tempDir = Files.createTempDirectory("kotbusta-book-").toFile()
        try {
            val fb2File = File(tempDir, "${book.id}.fb2")
            ZipFile(archiveFile.toFile()).use { zip ->
                val entry = zip.entries().asSequence().find { it.name == book.filePath }
                    ?: throw BookFileException("FB2 entry '${book.filePath}' not found in ${book.archivePath}.zip")
                zip.getInputStream(entry).use { input ->
                    fb2File.outputStream().use(input::copyTo)
                }
            }

            if (normalizedFormat == "fb2") {
                return MaterializedBook(fb2File, "${sanitizedTitle(book)}.fb2", "fb2", tempDir)
            }

            val outputFile = File(tempDir, "${book.id}.$normalizedFormat")
            val result = conversionService.convertFb2(
                inputFile = fb2File,
                outputFormat = normalizedFormat,
                outputFile = outputFile,
            )
            if (!result.success || result.outputFile == null) {
                throw BookFileException("Conversion to $normalizedFormat failed: ${result.errorMessage}")
            }

            return MaterializedBook(
                file = result.outputFile,
                fileName = "${sanitizedTitle(book)}.$normalizedFormat",
                format = normalizedFormat,
                tempDir = tempDir,
            )
        } catch (e: Throwable) {
            tempDir.deleteRecursively()
            throw e
        }
    }

    private fun sanitizedTitle(book: Book): String =
        book.title
            .trim()
            .ifBlank { "book_${book.id}" }
            .replace(UNSAFE_FILENAME_CHARS, "_")
            .replace(WHITESPACE, "_")
            .replace(UNDERSCORES, "_")
            .trim('.', '_')
            .ifBlank { "book_${book.id}" }
            .truncateForFilename()

    private fun String.truncateForFilename(): String {
        val truncated = take(100)
        val withoutTrailingHighSurrogate = if (truncated.lastOrNull()?.isHighSurrogate() == true) {
            truncated.dropLast(1)
        } else {
            truncated
        }

        return withoutTrailingHighSurrogate.trimEnd('.', '_')
    }

    private companion object : Logger() {
        private val UNSAFE_FILENAME_CHARS = Regex("""[\p{Cntrl}/\\:*?"<>|]+""")
        private val WHITESPACE = Regex("""\s+""")
        private val UNDERSCORES = Regex("""_+""")
    }
}

class BookFileException(message: String) : Exception(message)
