package io.heapy.kotbusta.parser

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.AUTHORS
import io.heapy.kotbusta.jooq.tables.references.BOOKS
import io.heapy.kotbusta.jooq.tables.references.BOOK_AUTHORS
import io.heapy.kotbusta.jooq.tables.references.BOOK_GENRES
import io.heapy.kotbusta.jooq.tables.references.GENRES
import io.heapy.kotbusta.jooq.tables.references.SERIES
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.service.TimeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

class InpxParser(
    private val transactionProvider: TransactionProvider,
    private val timeService: TimeService,
) {
    private data class ParsedBook(
        val bookId: Int,
        val title: String,
        val authors: List<String>,
        val series: String?,
        val seriesNumber: Int?,
        val genres: List<String>,
        val language: String,
        val fileFormat: String,
        val filePath: String,
        val archivePath: String,
        val fileSize: Int?,
        val dateAdded: Instant,
    )

    suspend fun parseAndImport(
        booksDataPath: Path,
        stats: ImportStats,
    ) {
        val inpxFilePath = booksDataPath.resolve("flibusta_fb2_local.inpx")
        stats.addMessage("Starting INPX parsing from: $inpxFilePath")

        val allBooks = coroutineScope {
            ZipFile(inpxFilePath.toString()).use { zipFile ->
                val entries = zipFile.entries().asSequence()
                    .filter { it.name.endsWith(".inp") }
                    .toList()

                stats.addMessage("Found ${entries.size} .inp files to process")

                // Process INP files in parallel and collect all books
                entries
                    .mapIndexed { index, entry ->
                        stats.incInpFiles()
                        async(Dispatchers.IO) {
                            stats.addMessage("Processing ${entry.name} (${index + 1}/${entries.size})")

                            val lines = zipFile.getInputStream(entry)
                                .bufferedReader(Charsets.UTF_8)
                                .useLines(Sequence<String>::toList)

                            val archiveName = entry.name.removeSuffix(".inp")

                            // Parse all lines and collect books in memory
                            val books = lines.mapNotNull { line ->
                                parseBookLine(line, archiveName, stats)
                            }

                            stats.addMessage("Completed parsing ${entry.name}: ${books.size} books parsed")
                            books
                        }
                    }
                    .awaitAll()
                    .flatten()
                    .distinctBy { it.bookId }
            }
        }

        stats.addMessage("Parsed ${allBooks.size} books, now persisting to database...")

        transactionProvider.transaction(READ_WRITE) {
            bulkInsertBooks(stats, allBooks)
        }

        stats.addMessage("INPX parsing and import completed successfully")
    }

    context(_: TransactionContext)
    private fun bulkInsertBooks(
        stats: ImportStats,
        books: List<ParsedBook>,
        createdAt: Instant = Clock.System.now(),
    ) = useTx { dslContext ->
        stats.addMessage("Starting bulk insert of ${books.size} books")
        dslContext.truncateTable(BOOK_GENRES).execute()
        dslContext.truncateTable(BOOK_AUTHORS).execute()
        dslContext.truncateTable(BOOKS).execute()
        dslContext.truncateTable(GENRES).execute()
        dslContext.truncateTable(AUTHORS).execute()
        dslContext.truncateTable(SERIES).execute()

        val uniqueSeries = storeSeries(stats, books)
        val uniqueAuthors = storeAuthors(stats, books)
        val uniqueGenres = storeGenres(stats, books)

        storeBooks(stats, books, uniqueSeries, createdAt)

        val authorRelationshipCount = storeBooksAuthors(stats, books, uniqueAuthors)
        val genreRelationshipCount = storeBooksGenres(stats, books, uniqueGenres)

        stats.addMessage("Bulk insert completed: ${books.size} books, ${uniqueAuthors.size} authors, ${uniqueSeries.size} series, ${uniqueGenres.size} genres, $authorRelationshipCount author relationships, $genreRelationshipCount genre relationships")
    }

    context(_: TransactionContext)
    private fun storeBooksAuthors(
        stats: ImportStats,
        books: List<ParsedBook>,
        uniqueAuthors: MutableMap<String, Int>,
    ): Int = useTx { dslContext ->
        val bookAuthorPairs = books
            .flatMap { book ->
                book.authors
                    .map { author ->
                        val authorId = uniqueAuthors[author]
                            ?: error("Author not found: $author")
                        book.bookId to authorId
                    }
            }

        stats.addMessage("Inserting ${bookAuthorPairs.size} book-author relationships")

        if (bookAuthorPairs.isNotEmpty()) {
            dslContext
                .batch(
                    bookAuthorPairs.map { (bookId, authorId) ->
                        dslContext
                            .insertInto(BOOK_AUTHORS)
                            .set(BOOK_AUTHORS.BOOK_ID, bookId)
                            .set(BOOK_AUTHORS.AUTHOR_ID, authorId)
                    },
                )
                .execute()
        }

        bookAuthorPairs.size
    }

    context(_: TransactionContext)
    private fun storeBooks(
        stats: ImportStats,
        books: List<ParsedBook>,
        uniqueSeries: MutableMap<String, Int>,
        createdAt: Instant,
    ) = useTx { dslContext ->
        stats.addMessage("Inserting ${books.size} books")

        if (books.isNotEmpty()) {
            dslContext
                .batch(
                    books.map { book ->
                        val seriesId = book.series
                            ?.let { seriesId ->
                                uniqueSeries[seriesId]
                            }

                        dslContext
                            .insertInto(BOOKS)
                            .set(BOOKS.ID, book.bookId)
                            .set(BOOKS.TITLE, book.title)
                            .set(BOOKS.GENRE, book.genre)
                            .set(BOOKS.LANGUAGE, book.language)
                            .set(BOOKS.SERIES_ID, seriesId)
                            .set(BOOKS.SERIES_NUMBER, book.seriesNumber)
                            .set(BOOKS.FILE_FORMAT, book.fileFormat)
                            .set(BOOKS.FILE_PATH, book.filePath)
                            .set(BOOKS.ARCHIVE_PATH, book.archivePath)
                            .set(BOOKS.FILE_SIZE, book.fileSize)
                            .set(BOOKS.DATE_ADDED, book.dateAdded)
                            .set(BOOKS.CREATED_AT, createdAt)
                    },
                )
                .execute()
        }
    }

    context(_: TransactionContext)
    private fun storeAuthors(
        stats: ImportStats,
        books: List<ParsedBook>,
    ): MutableMap<String, Int> = useTx { dslContext ->
        var authorId = 0
        val uniqueAuthors = books
            .fold(mutableMapOf<String, Int>()) { acc, book ->
                book.authors.forEach { author ->
                    val existingId = acc[author]
                    acc[author] = existingId ?: ++authorId
                }
                acc
            }

        stats.addMessage("Inserting ${uniqueAuthors.size} new authors")

        // Batch insert new authors
        if (uniqueAuthors.isNotEmpty()) {
            dslContext
                .batch(
                    uniqueAuthors.map { (author, id) ->
                        dslContext
                            .insertInto(AUTHORS)
                            .set(AUTHORS.ID, id)
                            .set(AUTHORS.FULL_NAME, author)
                    },
                )
                .execute()
        }

        uniqueAuthors
    }

    context(_: TransactionContext)
    private fun storeSeries(
        stats: ImportStats,
        books: List<ParsedBook>,
    ): MutableMap<String, Int> = useTx { dslContext ->
        var seriesId = 0
        val uniqueSeries = books
            .fold(mutableMapOf<String, Int>()) { acc, book ->
                book.series?.let { series ->
                    val existingId = acc[series]
                    acc[series] = existingId ?: ++seriesId
                }
                acc
            }

        stats.addMessage("Inserting ${uniqueSeries.size} unique series")

        if (uniqueSeries.isNotEmpty()) {
            dslContext
                .batch(
                    uniqueSeries.map { (series, id) ->
                        dslContext
                            .insertInto(SERIES)
                            .set(SERIES.ID, id)
                            .set(SERIES.NAME, series)
                    },
                )
                .execute()
        }

        uniqueSeries
    }

    context(_: TransactionContext)
    private fun storeGenres(
        stats: ImportStats,
        books: List<ParsedBook>,
    ): MutableMap<String, Int> = useTx { dslContext ->
        var genreId = 0
        val uniqueGenres = books
            .fold(mutableMapOf<String, Int>()) { acc, book ->
                book.genres.forEach { genre ->
                    val existingId = acc[genre]
                    acc[genre] = existingId ?: ++genreId
                }
                acc
            }

        stats.addMessage("Inserting ${uniqueGenres.size} unique genres")

        if (uniqueGenres.isNotEmpty()) {
            dslContext
                .batch(
                    uniqueGenres.map { (genre, id) ->
                        dslContext
                            .insertInto(GENRES)
                            .set(GENRES.ID, id)
                            .set(GENRES.NAME, genre)
                    },
                )
                .execute()
        }

        uniqueGenres
    }

    context(_: TransactionContext)
    private fun storeBooksGenres(
        stats: ImportStats,
        books: List<ParsedBook>,
        uniqueGenres: MutableMap<String, Int>,
    ): Int = useTx { dslContext ->
        val bookGenrePairs = books
            .flatMap { book ->
                book.genres
                    .map { genre ->
                        val genreId = uniqueGenres[genre]
                            ?: error("Genre not found: $genre")
                        book.bookId to genreId
                    }
            }

        stats.addMessage("Inserting ${bookGenrePairs.size} book-genre relationships")

        if (bookGenrePairs.isNotEmpty()) {
            dslContext
                .batch(
                    bookGenrePairs.map { (bookId, genreId) ->
                        dslContext
                            .insertInto(BOOK_GENRES)
                            .set(BOOK_GENRES.BOOK_ID, bookId)
                            .set(BOOK_GENRES.GENRE_ID, genreId)
                    },
                )
                .execute()
        }

        bookGenrePairs.size
    }

    private fun parseBookLine(
        line: String,
        archivePath: String,
        stats: ImportStats,
    ): ParsedBook? {
        return try {
            val parts = line.split('\u0004') // Field separator in INP files
            if (parts.size < 8) {
                log.warn("Invalid line: $line")
                stats.incInvalidBooks()
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
            val dateAdded = parts.getOrNull(10)
            val language = parts.getOrNull(11) ?: "ru"
            parts.getOrNull(12) // too many empty, so not useful for anything

            val warnings = buildList {
                if (bookId == null) add("Invalid bookId: $bookId")
                if (fileSize == null) add("Invalid fileSize: $fileSize")
                if (fileFormat == null) add("Invalid fileFormat: $fileFormat")
            }

            if (warnings.isNotEmpty()) {
                log.warn("Invalid book: $line. Warnings: $warnings")
                stats.incInvalidBooks()
                return null
            }

            if (deleted == "1") {
                log.debug("Book $bookId deleted")
                stats.incDeletedBooks()
                return null
            }

            // Parse authors
            val authors = parseAuthors(authorPart)
            if (authors.isEmpty()) {
                log.warn("No authors for book $bookId")
                stats.incInvalidBooks()
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

            stats.incAddedBooks()

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
                dateAdded = parseDateAdded(dateAdded),
            )
        } catch (e: Exception) {
            log.error("Error parsing line: $line", e)
            stats.incInvalidBooks()
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

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private fun parseDateAdded(dateStr: String?): Instant {
        if (dateStr.isNullOrBlank()) return timeService.now()

        return try {
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
