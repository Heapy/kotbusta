package io.heapy.kotbusta.parser

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.dslContext
import io.heapy.kotbusta.jooq.tables.references.*
import io.heapy.kotbusta.model.Author
import io.heapy.kotbusta.model.Series
import java.nio.file.Path
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

class InpxParser(
    private val transactionProvider: TransactionProvider,
) {
    suspend fun parseAndImport(booksDataPath: Path) {
        val inpxFilePath = booksDataPath.resolve("flibusta_fb2_local.inpx")
        log.info("Starting INPX parsing from: $inpxFilePath")

        transactionProvider.transaction(READ_WRITE) {
            ZipFile(inpxFilePath.toString()).use { zipFile ->
                val entries = zipFile.entries().asSequence()
                    .filter { it.name.endsWith(".inp") }
                    .toList()

                log.info("Found ${entries.size} .inp files to process")

                entries.forEachIndexed { index, entry ->
                    log.info("Processing ${entry.name} (${index + 1}/${entries.size})")

                    zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.lineSequence().forEach { line ->
                            parseBookLine(line, entry.name.removeSuffix(".inp"))
                        }
                    }

                    if ((index + 1) % 10 == 0) {
                        log.info("Processed batch ${index + 1}")
                    }
                }

                log.info("INPX parsing completed successfully")
            }
        }
    }

    context(_: TransactionContext)
    private fun parseBookLine(line: String, archivePath: String) {
        try {
            val parts = line.split('\u0004') // Field separator in INP files
            if (parts.size < 8) return

            val authorPart = parts[0]
            val genre = parts[1]
            val title = parts[2]
            val seriesPart = parts[3]
            val seriesNumber = parts[4].toIntOrNull()
            val bookId = parts[5].toLongOrNull() ?: return
            val fileSize = parts[6].toLongOrNull()
            val libId = parts[7] // seems that it's the same as bookId
            val deleted = parts.getOrNull(8)
            val fileFormat = parts.getOrNull(9)
            val dateAdded = parts.getOrNull(10)
            val language = parts.getOrNull(11) ?: "ru"
            val keywords = parts.getOrNull(12) // too many empty, so not useful for anything

            if (deleted == "1") return // Skip deleted books

            // Parse authors
            val authors = parseAuthors(authorPart)
            if (authors.isEmpty()) return

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
        } catch (e: Exception) {
            log.error("Error parsing line: $line", e)
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

            Author(0, firstName, lastName, fullName)
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
    ) = dslContext { dslContext ->
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
    private fun insertOrGetSeries(name: String): Long = dslContext { dslContext ->
        // Try to get existing series
        val existing = dslContext
            .select(SERIES.ID)
            .from(SERIES)
            .where(SERIES.NAME.eq(name))
            .fetchOne(SERIES.ID)

        if (existing != null) {
            return@dslContext existing
        }

        // Insert new series and return the generated ID
        dslContext
            .insertInto(SERIES)
            .set(SERIES.NAME, name)
            .returning(SERIES.ID)
            .fetchOne(SERIES.ID)
            ?: throw RuntimeException("Failed to insert series: $name")
    }

    context(_: TransactionContext)
    private fun insertOrGetAuthor(author: Author): Long = dslContext { dslContext ->
        // Try to get existing author
        val existing = dslContext
            .select(AUTHORS.ID)
            .from(AUTHORS)
            .where(AUTHORS.FULL_NAME.eq(author.fullName))
            .fetchOne(AUTHORS.ID)

        if (existing != null) {
            return@dslContext existing
        }

        // Insert new author and return the generated ID
        dslContext
            .insertInto(AUTHORS)
            .set(AUTHORS.FIRST_NAME, author.firstName)
            .set(AUTHORS.LAST_NAME, author.lastName)
            .set(AUTHORS.FULL_NAME, author.fullName)
            .returning(AUTHORS.ID)
            .fetchOne(AUTHORS.ID)
            ?: throw RuntimeException("Failed to insert author: ${author.fullName}")
    }

    private companion object : Logger()
}
