package io.heapy.kotbusta.parser

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.model.State.ParsedBook
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch

class InpxParser(
    private val booksDataPath: Path,
    private val dispatcher: CoroutineDispatcher,
) {
    private val inpFilesProcessed = AtomicInt(0)
    private val booksAdded = AtomicInt(0)
    private val booksDeleted = AtomicInt(0)
    private val bookErrors = AtomicInt(0)

    suspend fun parseAndImport(): Map<Int, ParsedBook> {
        val inpxFilePath = booksDataPath.resolve("flibusta_fb2_local.inpx")
        log.info("Starting INPX parsing from: $inpxFilePath")

        return coroutineScope {
            ZipFile(inpxFilePath.toString()).use { zipFile ->
                val entries = zipFile.entries().asSequence()
                    .filter { it.name.endsWith(".inp") }
                    .toList()

                log.info("Found ${entries.size} .inp files to process")

                // Process INP files in parallel and collect all books
                entries
                    .mapIndexed { index, entry ->
                        inpFilesProcessed.incrementAndFetch()
                        async(dispatcher) {
                            log.info("Processing ${entry.name} (${index + 1}/${entries.size})")

                            val lines = zipFile.getInputStream(entry)
                                .bufferedReader(Charsets.UTF_8)
                                .useLines(Sequence<String>::toList)

                            val archiveName = entry.name.removeSuffix(".inp")

                            // Parse all lines and collect books in memory
                            val books = lines.mapNotNull { line ->
                                parseBookLine(line, archiveName)
                            }

                            log.info("Completed parsing ${entry.name}: ${books.size} books parsed")
                            books
                        }
                    }
                    .awaitAll()
                    .asSequence()
                    .flatten()
                    .associateBy { it.bookId }
            }
        }.also {
            log.info(
                """
                INPX parsing finished. ${inpFilesProcessed.load()} INP files processed
                Books added: ${booksAdded.load()}
                Books failed: ${bookErrors.load()}
                Books deleted: ${booksDeleted.load()}
                """.trimIndent()
            )
        }
    }

    private fun parseBookLine(
        line: String,
        archivePath: String,
    ): ParsedBook? {
        return try {
            val parts = line.split('\u0004') // Field separator in INP files
            if (parts.size < 8) {
                log.warn("Invalid line: $line")
                bookErrors.incrementAndFetch()
                return null
            }

            val authorPart = parts[0]
            val genre = parts[1]
            val title = parts[2]
            val seriesPart = parts[3]
            val seriesNumber = parts[4].toIntOrNull()
            val bookId = parts[5].toIntOrNull()
            val fileSize = parts[6].toIntOrNull()
            parts[7] // seems that it's the same as bookId
            val deleted = parts.getOrNull(8)
            val fileFormat = parts.getOrNull(9)
            parts.getOrNull(10) // date added
            val language = parts.getOrNull(11) ?: "ru"
            parts.getOrNull(12) // too many empty, so not useful for anything

            val warnings = buildList {
                if (bookId == null) add("Invalid bookId: $bookId")
                if (fileSize == null) add("Invalid fileSize: $fileSize")
                if (fileFormat == null) add("Invalid fileFormat: $fileFormat")
            }

            if (warnings.isNotEmpty()) {
                log.warn("Invalid book: $line. Warnings: $warnings")
                bookErrors.incrementAndFetch()
                return null
            }

            if (deleted == "1") {
                log.debug("Book $bookId deleted")
                booksDeleted.incrementAndFetch()
                return null
            }

            // Parse authors
            val authors = parseAuthors(authorPart)
            if (authors.isEmpty()) {
                log.warn("No authors for book $bookId")
                bookErrors.incrementAndFetch()
                return null
            }

            // Parse series
            val series = if (seriesPart.isNotBlank()) seriesPart.trim() else null

            // Parse genres (split by ':' and filter out empty ones)
            val genres = if (genre.isNotBlank()) {
                genre.split(':')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }

            // Determine file paths
            val filePath = "${bookId}.${fileFormat}"

            booksAdded.incrementAndFetch()

            // Return parsed book data
            ParsedBook(
                bookId = bookId!!,
                title = title.trim(),
                authors = authors,
                series = series,
                seriesNumber = seriesNumber,
                genres = genres,
                language = language,
                fileFormat = fileFormat!!,
                filePath = filePath,
                archivePath = archivePath,
                fileSize = fileSize,
            )
        } catch (e: Exception) {
            log.error("Error parsing line: $line", e)
            bookErrors.incrementAndFetch()
            null
        }
    }

    private fun parseAuthors(authorPart: String): List<String> {
        return if (authorPart.isNotBlank()) {
            authorPart
                .split(':')
                .filter { it.isNotBlank() }
                .map { author ->
                    author.replace(',', ' ')
                }
        } else {
            emptyList()
        }
    }

    private companion object : Logger()
}
