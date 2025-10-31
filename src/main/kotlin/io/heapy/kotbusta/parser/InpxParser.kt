package io.heapy.kotbusta.parser

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.dao.insertBook
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.model.Author
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.model.Series
import io.heapy.kotbusta.service.TimeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

class InpxParser(
    private val transactionProvider: TransactionProvider,
    private val timeService: TimeService,
) {
    suspend fun parseAndImport(
        booksDataPath: Path,
        stats: ImportStats,
    ) {
        val inpxFilePath = booksDataPath.resolve("flibusta_fb2_local.inpx")
        stats.addMessage("Starting INPX parsing from: $inpxFilePath")

        coroutineScope {
            ZipFile(inpxFilePath.toString()).use { zipFile ->
                val entries = zipFile.entries().asSequence()
                    .filter { it.name.endsWith(".inp") }
                    .toList()

                log.info("Found ${entries.size} .inp files to process")

                // Process INP files in parallel
                entries
                    .mapIndexed { index, entry ->
                        stats.incInpFiles()
                        async(Dispatchers.IO) {
                            log.info("Processing ${entry.name} (${index + 1}/${entries.size})")

                            val lines = zipFile.getInputStream(entry)
                                .bufferedReader(Charsets.UTF_8)
                                .useLines(Sequence<String>::toList)

                            val archiveName = entry.name.removeSuffix(".inp")

                            // Process lines in batches within a transaction
                            transactionProvider.transaction(READ_WRITE) {
                                lines.chunked(100).forEach { batch ->
                                    batch.forEach { line ->
                                        parseBookLine(line, archiveName, stats)
                                    }
                                }
                            }

                            log.info("Completed processing ${entry.name}: ${stats.booksAdded} books added, ${stats.bookErrors} errors")
                        }
                    }
                    .awaitAll()

                log.info("INPX parsing completed successfully")
            }
        }
    }

    context(_: TransactionContext)
    private fun parseBookLine(
        line: String,
        archivePath: String,
        stats: ImportStats,
    ): Boolean {
        return try {
            val parts = line.split('\u0004') // Field separator in INP files
            if (parts.size < 8) {
                log.warn("Invalid line: $line")
                stats.incInvalidBooks()
                return false
            }

            val authorPart = parts[0]
            val genre = parts[1]
            val title = parts[2]
            val seriesPart = parts[3]
            val seriesNumber = parts[4].toIntOrNull()
            val bookId = parts[5].toIntOrNull() ?: run {
                log.warn("Invalid bookId: ${parts[5]}")
                stats.incInvalidBooks()
                return false
            }
            val fileSize = parts[6].toIntOrNull()
            parts[7] // seems that it's the same as bookId
            val deleted = parts.getOrNull(8)
            val fileFormat = parts.getOrNull(9)
            val dateAdded = parts.getOrNull(10)
            val language = parts.getOrNull(11) ?: "ru"
            parts.getOrNull(12) // too many empty, so not useful for anything

            if (deleted == "1") {
                log.debug("Book $bookId deleted")
                stats.incDeletedBooks()
                return false // Skip deleted books
            }

            // Parse authors
            val authors = parseAuthors(authorPart)
            if (authors.isEmpty()) {
                log.warn("No authors for book $bookId")
                stats.incInvalidBooks()
                return false
            }

            // Parse series
            val series = if (seriesPart.isNotBlank()) {
                Series(0, seriesPart.trim())
            } else null

            // Determine file paths
            val filePath = "${bookId}.${fileFormat}"

            // Insert book into database
            insertBook(
                bookId = bookId,
                title = title.trim(),
                authors = authors,
                series = series,
                seriesNumber = seriesNumber,
                genre = genre.trim().takeIf { it.isNotBlank() },
                language = language,
                filePath = filePath,
                archivePath = archivePath,
                fileSize = fileSize,
                dateAdded = parseDateAdded(dateAdded),
            )
            stats.incAddedBooks()
            true
        } catch (e: Exception) {
            log.error("Error parsing line: $line", e)
            false
        }
    }

    private fun parseAuthors(authorPart: String): List<Author> {
        if (authorPart.isBlank()) return emptyList()

        return authorPart.split(':').mapNotNull { authorStr ->
            val parts = authorStr.split(',').map { it.trim() }
            if (parts.isEmpty() || parts[0].isBlank()) return@mapNotNull null

            val lastName = parts[0]
            val firstName = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            val middleName = parts.getOrNull(2)?.takeIf { it.isNotBlank() }

            val fullName = buildString {
                append(lastName)
                if (firstName != null) {
                    append(", $firstName")
                    if (middleName != null) {
                        append(" $middleName")
                    }
                }
            }

            Author(
                id = 0,
                firstName = firstName,
                lastName = lastName,
                fullName = fullName,
            )
        }
    }

    private fun parseDateAdded(dateStr: String?): Instant {
        if (dateStr.isNullOrBlank()) return timeService.now()

        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val date = LocalDate.parse(dateStr, formatter)
            date.atStartOfDay()
                .atOffset(java.time.ZoneOffset.UTC)
                .toInstant()
                .toKotlinInstant()
        } catch (e: Exception) {
            log.error("Error parsing date: $dateStr", e)
            timeService.now()
        }
    }

    private companion object : Logger()
}
