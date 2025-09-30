package io.heapy.kotbusta.parser

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.*
import io.heapy.kotbusta.model.Author
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.model.Series
import io.heapy.kotbusta.service.ImportJobService.JobId
import kotlinx.coroutines.*
import java.nio.file.Path
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

class InpxParser(
    private val transactionProvider: TransactionProvider,
) {
    suspend fun parseAndImport(
        booksDataPath: Path,
        jobId: JobId,
        stats: ImportStats,
    ) {
        val inpxFilePath = booksDataPath.resolve("flibusta_fb2_local.inpx")
        log.info("Starting INPX parsing from: $inpxFilePath")

        coroutineScope {
            ZipFile(inpxFilePath.toString()).use { zipFile ->
                val entries = zipFile.entries().asSequence()
                    .filter { it.name.endsWith(".inp") }
                    .toList()

                log.info("Found ${entries.size} .inp files to process")

                // Process INP files in parallel
                entries.mapIndexed { index, entry ->
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
                }.awaitAll()

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
                stats.incInvalidInpLine()
                return false
            }

            val authorPart = parts[0]
            val genre = parts[1]
            val title = parts[2]
            val seriesPart = parts[3]
            val seriesNumber = parts[4].toIntOrNull()
            val bookId = parts[5].toLongOrNull() ?: run {
                log.warn("Invalid bookId: ${parts[5]}")
                stats.incInvalidBookId()
                return false
            }
            val fileSize = parts[6].toLongOrNull()
            val libId = parts[7] // seems that it's the same as bookId
            val deleted = parts.getOrNull(8)
            val fileFormat = parts.getOrNull(9)
            val dateAdded = parts.getOrNull(10)
            val language = parts.getOrNull(11) ?: "ru"
            val keywords = parts.getOrNull(12) // too many empty, so not useful for anything

            if (deleted == "1") {
                log.warn("Book $bookId deleted")
                stats.incBooksDeleted()
                return false // Skip deleted books
            }

            // Parse authors
            val authors = parseAuthors(authorPart)
            if (authors.isEmpty()) {
                log.warn("No authors for book $bookId")
                stats.incBookNoAuthors()
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
                dateAdded = parseDateAdded(dateAdded)
            )
            stats.incBookAdded()
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

    private fun parseDateAdded(dateStr: String?): OffsetDateTime {
        if (dateStr.isNullOrBlank()) return OffsetDateTime.now()

        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val date = LocalDate.parse(dateStr, formatter)
            date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC)
        } catch (e: Exception) {
            log.error("Error parsing date: $dateStr", e)
            OffsetDateTime.now()
        }
    }

    context(_: TransactionContext)
    private fun insertBook(
        bookId: Long,
        title: String,
        authors: List<Author>,
        series: Series?,
        seriesNumber: Int?,
        genre: String?,
        language: String,
        filePath: String,
        archivePath: String,
        fileSize: Long?,
        dateAdded: OffsetDateTime
    ) = useTx { dslContext ->
        // Insert or get series
        val seriesId = series?.let { insertOrGetSeries(it.name) }

        // Insert or get authors
        val authorIds = authors.map { insertOrGetAuthor(it) }

        // Insert book using jOOQ with ON DUPLICATE KEY UPDATE (PostgreSQL UPSERT)
        dslContext
            .insertInto(BOOKS)
            .set(BOOKS.ID, bookId)
            .set(BOOKS.TITLE, title)
            .set(BOOKS.GENRE, genre)
            .set(BOOKS.LANGUAGE, language)
            .set(BOOKS.SERIES_ID, seriesId)
            .set(BOOKS.SERIES_NUMBER, seriesNumber)
            .set(BOOKS.FILE_PATH, filePath)
            .set(BOOKS.ARCHIVE_PATH, archivePath)
            .set(BOOKS.FILE_SIZE, fileSize)
            .set(BOOKS.DATE_ADDED, dateAdded)
            .onConflict(BOOKS.ID)
            .doUpdate()
            .set(BOOKS.TITLE, title)
            .set(BOOKS.GENRE, genre)
            .set(BOOKS.LANGUAGE, language)
            .set(BOOKS.SERIES_ID, seriesId)
            .set(BOOKS.SERIES_NUMBER, seriesNumber)
            .set(BOOKS.FILE_PATH, filePath)
            .set(BOOKS.ARCHIVE_PATH, archivePath)
            .set(BOOKS.FILE_SIZE, fileSize)
            .set(BOOKS.DATE_ADDED, dateAdded)
            .execute()

        // Insert book-author relationships
        authorIds.forEach { authorId ->
            dslContext
                .insertInto(BOOK_AUTHORS)
                .set(BOOK_AUTHORS.BOOK_ID, bookId)
                .set(BOOK_AUTHORS.AUTHOR_ID, authorId)
                .onConflict(BOOK_AUTHORS.BOOK_ID, BOOK_AUTHORS.AUTHOR_ID)
                .doNothing()
                .execute()
        }
    }

    context(_: TransactionContext)
    private fun insertOrGetSeries(name: String): Long = useTx { dslContext ->
        // Try to get existing series - use LIMIT 1 to avoid multiple results
        val existing = dslContext
            .select(SERIES.ID)
            .from(SERIES)
            .where(SERIES.NAME.eq(name))
            .fetchOne(SERIES.ID)

        if (existing != null) {
            return@useTx existing
        }

        dslContext
            .insertInto(SERIES)
            .set(SERIES.NAME, name)
            .returning(SERIES.ID)
            .fetchOne(SERIES.ID)
            ?: error("Failed to insert series: $name")
    }

    context(_: TransactionContext)
    private fun insertOrGetAuthor(author: Author): Long = useTx { dslContext ->
        // Try to get existing author - use LIMIT 1 to avoid multiple results
        val existing = dslContext
            .select(AUTHORS.ID)
            .from(AUTHORS)
            .where(AUTHORS.FULL_NAME.eq(author.fullName))
            .limit(1)
            .fetchOne(AUTHORS.ID)

        if (existing != null) {
            return@useTx existing
        }

        dslContext
            .insertInto(AUTHORS)
            .set(AUTHORS.FIRST_NAME, author.firstName)
            .set(AUTHORS.LAST_NAME, author.lastName)
            .set(AUTHORS.FULL_NAME, author.fullName)
            .returning(AUTHORS.ID)
            .fetchOne(AUTHORS.ID)
            ?: error("Failed to insert author: ${author.fullName}")
    }

    private companion object : Logger()
}
