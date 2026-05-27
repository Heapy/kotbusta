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
import org.jooq.DSLContext
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

    /**
     * Streams the INPX file-by-file, persisting each `.inp` entry in its own
     * transaction with chunked, conflict-safe inserts. Only the (bounded) author/
     * series/genre id registries are held across the whole import, so peak memory
     * stays small even for libraries with hundreds of thousands of books.
     */
    suspend fun parseAndImport(
        booksDataPath: Path,
        stats: ImportStats,
        createdAt: Instant = Clock.System.now(),
    ) {
        val inpxFilePath = booksDataPath.resolve("flibusta_fb2_local.inpx")
        stats.addMessage("Starting INPX parsing from: $inpxFilePath")

        // Reset target tables once, before streaming inserts.
        transactionProvider.transaction(READ_WRITE) {
            truncateAll()
        }

        val authors = IdRegistry()
        val series = IdRegistry()
        val genres = IdRegistry()

        ZipFile(inpxFilePath.toString()).use { zipFile ->
            val entries = zipFile.entries().asSequence()
                .filter { it.name.endsWith(".inp") }
                .toList()

            stats.addMessage("Found ${entries.size} .inp files to process")

            entries.forEachIndexed { index, entry ->
                stats.incInpFiles()
                val archiveName = entry.name.removeSuffix(".inp")

                val books = zipFile.getInputStream(entry)
                    .bufferedReader(Charsets.UTF_8)
                    .useLines { lines ->
                        lines.mapNotNull { line -> parseBookLine(line, archiveName, stats) }.toList()
                    }

                if (books.isNotEmpty()) {
                    transactionProvider.transaction(READ_WRITE) {
                        persist(books, authors, series, genres, createdAt)
                    }
                }

                stats.addMessage("Processed ${entry.name} (${index + 1}/${entries.size}): ${books.size} books")
            }
        }

        stats.addMessage(
            "INPX import completed: ${stats.booksAdded.load()} books, " +
                "${authors.size} authors, ${series.size} series, ${genres.size} genres",
        )
    }

    context(_: TransactionContext)
    private fun truncateAll() = useTx { dsl ->
        dsl.truncateTable(BOOK_GENRES).execute()
        dsl.truncateTable(BOOK_AUTHORS).execute()
        dsl.truncateTable(BOOKS).execute()
        dsl.truncateTable(GENRES).execute()
        dsl.truncateTable(AUTHORS).execute()
        dsl.truncateTable(SERIES).execute()
    }

    context(_: TransactionContext)
    private fun persist(
        books: List<ParsedBook>,
        authors: IdRegistry,
        series: IdRegistry,
        genres: IdRegistry,
        createdAt: Instant,
    ) = useTx { dsl ->
        // Resolve ids first; newly seen names are queued for insertion.
        books.forEach { book ->
            book.authors.forEach(authors::resolve)
            book.series?.let(series::resolve)
            book.genres.forEach(genres::resolve)
        }

        insertAuthors(dsl, authors.drainPending())
        insertSeries(dsl, series.drainPending())
        insertGenres(dsl, genres.drainPending())

        insertBooks(dsl, books, series.ids, createdAt)
        insertBookAuthors(dsl, books, authors.ids)
        insertBookGenres(dsl, books, genres.ids)
    }

    private fun insertAuthors(dsl: DSLContext, entries: List<Pair<String, Int>>) {
        entries.chunked(CHUNK_SIZE).forEach { chunk ->
            dsl.batch(
                chunk.map { (name, id) ->
                    dsl.insertInto(AUTHORS)
                        .set(AUTHORS.ID, id)
                        .set(AUTHORS.FULL_NAME, name)
                },
            ).execute()
        }
    }

    private fun insertSeries(dsl: DSLContext, entries: List<Pair<String, Int>>) {
        entries.chunked(CHUNK_SIZE).forEach { chunk ->
            dsl.batch(
                chunk.map { (name, id) ->
                    dsl.insertInto(SERIES)
                        .set(SERIES.ID, id)
                        .set(SERIES.NAME, name)
                },
            ).execute()
        }
    }

    private fun insertGenres(dsl: DSLContext, entries: List<Pair<String, Int>>) {
        entries.chunked(CHUNK_SIZE).forEach { chunk ->
            dsl.batch(
                chunk.map { (name, id) ->
                    dsl.insertInto(GENRES)
                        .set(GENRES.ID, id)
                        .set(GENRES.NAME, name)
                },
            ).execute()
        }
    }

    private fun insertBooks(
        dsl: DSLContext,
        books: List<ParsedBook>,
        seriesIds: Map<String, Int>,
        createdAt: Instant,
    ) {
        books.chunked(CHUNK_SIZE).forEach { chunk ->
            dsl.batch(
                chunk.map { book ->
                    dsl.insertInto(BOOKS)
                        .set(BOOKS.ID, book.bookId)
                        .set(BOOKS.TITLE, book.title)
                        .set(BOOKS.LANGUAGE, book.language)
                        .set(BOOKS.SERIES_ID, book.series?.let(seriesIds::getValue))
                        .set(BOOKS.SERIES_NUMBER, book.seriesNumber)
                        .set(BOOKS.FILE_FORMAT, book.fileFormat)
                        .set(BOOKS.FILE_PATH, book.filePath)
                        .set(BOOKS.ARCHIVE_PATH, book.archivePath)
                        .set(BOOKS.FILE_SIZE, book.fileSize)
                        .set(BOOKS.DATE_ADDED, book.dateAdded)
                        .set(BOOKS.CREATED_AT, createdAt)
                        .onConflictDoNothing()
                },
            ).execute()
        }
    }

    private fun insertBookAuthors(
        dsl: DSLContext,
        books: List<ParsedBook>,
        authorIds: Map<String, Int>,
    ) {
        val pairs = books.flatMap { book ->
            book.authors.map { author -> book.bookId to authorIds.getValue(author) }
        }
        pairs.chunked(CHUNK_SIZE).forEach { chunk ->
            dsl.batch(
                chunk.map { (bookId, authorId) ->
                    dsl.insertInto(BOOK_AUTHORS)
                        .set(BOOK_AUTHORS.BOOK_ID, bookId)
                        .set(BOOK_AUTHORS.AUTHOR_ID, authorId)
                        .onConflictDoNothing()
                },
            ).execute()
        }
    }

    private fun insertBookGenres(
        dsl: DSLContext,
        books: List<ParsedBook>,
        genreIds: Map<String, Int>,
    ) {
        val pairs = books.flatMap { book ->
            book.genres.map { genre -> book.bookId to genreIds.getValue(genre) }
        }
        pairs.chunked(CHUNK_SIZE).forEach { chunk ->
            dsl.batch(
                chunk.map { (bookId, genreId) ->
                    dsl.insertInto(BOOK_GENRES)
                        .set(BOOK_GENRES.BOOK_ID, bookId)
                        .set(BOOK_GENRES.GENRE_ID, genreId)
                        .onConflictDoNothing()
                },
            ).execute()
        }
    }

    private fun parseBookLine(
        line: String,
        archivePath: String,
        stats: ImportStats,
    ): ParsedBook? {
        return try {
            val parts = line.split('') // Field separator in INP files
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

    /**
     * Assigns stable, sequential ids to distinct names and remembers which names
     * still need to be inserted into the database.
     */
    private class IdRegistry {
        val ids = HashMap<String, Int>()
        private val pending = ArrayList<Pair<String, Int>>()
        private var next = 1

        val size: Int get() = ids.size

        fun resolve(name: String): Int =
            ids[name] ?: (next++).also { id ->
                ids[name] = id
                pending += name to id
            }

        fun drainPending(): List<Pair<String, Int>> {
            if (pending.isEmpty()) return emptyList()
            val out = ArrayList(pending)
            pending.clear()
            return out
        }
    }

    private companion object : Logger() {
        private const val CHUNK_SIZE = 1000
    }
}
