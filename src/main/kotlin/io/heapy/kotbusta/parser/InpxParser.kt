package io.heapy.kotbusta.parser

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.service.TimeService
import org.jooq.DSLContext
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
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

    private data class StagingTables(
        val books: String,
        val authors: String,
        val series: String,
        val genres: String,
        val bookAuthors: String,
        val bookGenres: String,
    ) {
        val allChildrenFirst: List<String> = listOf(
            bookAuthors,
            bookGenres,
            books,
            authors,
            series,
            genres,
        )
    }

    /**
     * Loads the INPX into per-run staging tables first, then swaps the live
     * catalog in one transaction. Until the final swap commits, readers continue
     * to see the previous live catalog.
     */
    suspend fun parseAndImport(
        booksDataPath: Path,
        stats: ImportStats,
        createdAt: Instant = Clock.System.now(),
    ) {
        val inpxFilePath = booksDataPath.resolve("flibusta_fb2_local.inpx")
        stats.addMessage("Starting INPX parsing from: $inpxFilePath")

        val staging = newStagingTables()
        val authorIds = HashMap<String, Int>()
        val seriesIds = HashMap<String, Int>()
        val genreIds = HashMap<String, Int>()

        try {
            transactionProvider.transaction(READ_WRITE) {
                cleanupStaleStagingTables()
                createStagingTables(staging)
            }

            ZipFile(inpxFilePath.toString()).use { zipFile ->
                val entries = zipFile.entries().asSequence()
                    .filter { it.name.endsWith(".inp") }
                    .sortedBy { it.name }
                    .toList()

                stats.setInpFilesTotal(entries.size)
                stats.addMessage("Found ${entries.size} .inp files to process")

                entries.forEachIndexed { index, entry ->
                    stats.setCurrentInpFile(entry.name)
                    val archiveName = entry.name.removeSuffix(".inp")
                    val books = zipFile.getInputStream(entry)
                        .bufferedReader(Charsets.UTF_8)
                        .useLines { lines ->
                            lines.mapNotNull { line -> parseBookLine(line, archiveName, stats) }.toList()
                        }
                        .deduplicateByBookId(stats)

                    if (books.isNotEmpty()) {
                        transactionProvider.transaction(READ_WRITE) {
                            persistToStaging(
                                staging = staging,
                                books = books,
                                authorIds = authorIds,
                                seriesIds = seriesIds,
                                genreIds = genreIds,
                                createdAt = createdAt,
                            )
                        }
                    }

                    stats.incInpFiles()
                    stats.addMessage("Processed ${entry.name} (${index + 1}/${entries.size}): ${books.size} books")
                }
                stats.setCurrentInpFile(null)
            }

            transactionProvider.transaction(READ_WRITE) {
                swapLiveCatalog(staging)
                dropStagingTables(staging)
            }

            stats.addMessage(
                "INPX import completed: ${stats.booksAdded.load()} books, " +
                    "${stats.booksDeleted.load()} deleted records skipped, ${authorIds.size} authors, " +
                    "${seriesIds.size} series, ${genreIds.size} genres",
            )
        } catch (e: Exception) {
            stats.setCurrentInpFile(null)
            bestEffortDrop(staging)
            throw e
        }
    }

    context(_: TransactionContext)
    private fun createStagingTables(staging: StagingTables) = useTx { dsl ->
        dsl.execute(
            """
            CREATE TABLE ${staging.authors}
            (
                ID        INTEGER PRIMARY KEY AUTOINCREMENT,
                FULL_NAME TEXT NOT NULL
            )
            """.trimIndent(),
        )
        dsl.execute(
            """
            CREATE TABLE ${staging.series}
            (
                ID   INTEGER PRIMARY KEY AUTOINCREMENT,
                NAME TEXT NOT NULL
            )
            """.trimIndent(),
        )
        dsl.execute(
            """
            CREATE TABLE ${staging.genres}
            (
                ID   INTEGER PRIMARY KEY AUTOINCREMENT,
                NAME TEXT UNIQUE NOT NULL
            )
            """.trimIndent(),
        )
        dsl.execute(
            """
            CREATE TABLE ${staging.books}
            (
                ID            INTEGER PRIMARY KEY,
                TITLE         TEXT NOT NULL,
                LANGUAGE      TEXT NOT NULL,
                SERIES_ID     INTEGER,
                SERIES_NUMBER INTEGER,
                FILE_FORMAT   TEXT NOT NULL,
                FILE_PATH     TEXT NOT NULL,
                ARCHIVE_PATH  TEXT NOT NULL,
                FILE_SIZE     INTEGER,
                DATE_ADDED    TEXT NOT NULL,
                CREATED_AT    TEXT NOT NULL
            )
            """.trimIndent(),
        )
        dsl.execute(
            """
            CREATE TABLE ${staging.bookAuthors}
            (
                BOOK_ID   INTEGER NOT NULL,
                AUTHOR_ID INTEGER NOT NULL,
                PRIMARY KEY (BOOK_ID, AUTHOR_ID)
            )
            """.trimIndent(),
        )
        dsl.execute(
            """
            CREATE TABLE ${staging.bookGenres}
            (
                BOOK_ID  INTEGER NOT NULL,
                GENRE_ID INTEGER NOT NULL,
                PRIMARY KEY (BOOK_ID, GENRE_ID)
            )
            """.trimIndent(),
        )
    }

    context(_: TransactionContext)
    private fun persistToStaging(
        staging: StagingTables,
        books: List<ParsedBook>,
        authorIds: MutableMap<String, Int>,
        seriesIds: MutableMap<String, Int>,
        genreIds: MutableMap<String, Int>,
        createdAt: Instant,
    ) = useTx { dsl ->
        val canonicalBooks = filterCanonicalBooks(dsl, staging, books)
        if (canonicalBooks.isEmpty()) return@useTx

        resolveNames(
            dsl = dsl,
            table = staging.authors,
            nameColumn = "FULL_NAME",
            names = canonicalBooks.flatMapTo(HashSet(), ParsedBook::authors),
            cache = authorIds,
        )
        resolveNames(
            dsl = dsl,
            table = staging.series,
            nameColumn = "NAME",
            names = canonicalBooks.mapNotNullTo(HashSet(), ParsedBook::series),
            cache = seriesIds,
        )
        resolveNames(
            dsl = dsl,
            table = staging.genres,
            nameColumn = "NAME",
            names = canonicalBooks.flatMapTo(HashSet(), ParsedBook::genres),
            cache = genreIds,
        )

        replaceExistingStagedBooks(dsl, staging, canonicalBooks.mapTo(HashSet(), ParsedBook::bookId))
        insertBooks(dsl, staging, canonicalBooks, seriesIds, createdAt)
        insertBookAuthors(dsl, staging, canonicalBooks, authorIds)
        insertBookGenres(dsl, staging, canonicalBooks, genreIds)
    }

    private fun List<ParsedBook>.deduplicateByBookId(stats: ImportStats): List<ParsedBook> {
        if (size < 2) return this

        val booksById = LinkedHashMap<Int, ParsedBook>(size)
        var duplicates = 0
        for (book in this) {
            if (booksById.put(book.bookId, book) != null) {
                duplicates += 1
            }
        }

        if (duplicates > 0) {
            stats.addMessage(
                "Found $duplicates duplicate book record(s) in one INP file; keeping the last record for each ID",
            )
        }

        return booksById.values.toList()
    }

    private fun filterCanonicalBooks(
        dsl: DSLContext,
        staging: StagingTables,
        books: List<ParsedBook>,
    ): List<ParsedBook> {
        val existingArchivePaths = selectExistingArchivePaths(
            dsl = dsl,
            staging = staging,
            bookIds = books.mapTo(HashSet(), ParsedBook::bookId),
        )

        return books.filter { book ->
            val existingArchivePath = existingArchivePaths[book.bookId]
                ?: return@filter true

            shouldReplaceStagedBook(
                bookId = book.bookId,
                existingArchivePath = existingArchivePath,
                incomingArchivePath = book.archivePath,
            )
        }
    }

    private fun shouldReplaceStagedBook(
        bookId: Int,
        existingArchivePath: String,
        incomingArchivePath: String,
    ): Boolean {
        val existingContainsBook = archiveContainsBook(existingArchivePath, bookId)
        val incomingContainsBook = archiveContainsBook(incomingArchivePath, bookId)

        return when {
            incomingContainsBook && !existingContainsBook -> true
            existingContainsBook && !incomingContainsBook -> false
            else -> true
        }
    }

    private fun archiveContainsBook(
        archivePath: String,
        bookId: Int,
    ): Boolean {
        val range = ARCHIVE_RANGE_REGEX.matchEntire(archivePath)
            ?.let { match ->
                val start = match.groupValues[1].toIntOrNull()
                val end = match.groupValues[2].toIntOrNull()
                if (start != null && end != null && start <= end) {
                    start..end
                } else {
                    null
                }
            }

        return range?.contains(bookId) == true
    }

    private fun selectExistingArchivePaths(
        dsl: DSLContext,
        staging: StagingTables,
        bookIds: Set<Int>,
    ): Map<Int, String> {
        if (bookIds.isEmpty()) return emptyMap()

        return buildMap {
            bookIds.chunked(CHUNK_SIZE).forEach { chunk ->
                val placeholders = chunk.joinToString(", ") { "?" }
                dsl.resultQuery(
                    "SELECT ID, ARCHIVE_PATH FROM ${staging.books} WHERE ID IN ($placeholders)",
                    *chunk.toTypedArray(),
                ).fetch().forEach { record ->
                    put(
                        record.get("ID", Int::class.java),
                        record.get("ARCHIVE_PATH", String::class.java),
                    )
                }
            }
        }
    }

    private fun replaceExistingStagedBooks(
        dsl: DSLContext,
        staging: StagingTables,
        bookIds: Set<Int>,
    ) {
        deleteRowsByBookIds(dsl, staging.bookAuthors, "BOOK_ID", bookIds)
        deleteRowsByBookIds(dsl, staging.bookGenres, "BOOK_ID", bookIds)
        deleteRowsByBookIds(dsl, staging.books, "ID", bookIds)
    }

    private fun deleteRowsByBookIds(
        dsl: DSLContext,
        table: String,
        idColumn: String,
        bookIds: Set<Int>,
    ) {
        bookIds.chunked(CHUNK_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(", ") { "?" }
            dsl.query(
                "DELETE FROM $table WHERE ${quoteIdentifier(idColumn)} IN ($placeholders)",
                *chunk.toTypedArray(),
            ).execute()
        }
    }

    private fun resolveNames(
        dsl: DSLContext,
        table: String,
        nameColumn: String,
        names: Set<String>,
        cache: MutableMap<String, Int>,
    ) {
        val missing = names.filter { it !in cache }
        if (missing.isEmpty()) return

        missing.chunked(CHUNK_SIZE).forEach { chunk ->
            cache.putAll(selectNameIds(dsl, table, nameColumn, chunk))
        }

        val toInsert = missing.filter { it !in cache }
        if (toInsert.isEmpty()) return

        toInsert.chunked(CHUNK_SIZE).forEach { chunk ->
            dsl.batch(
                chunk.map { name ->
                    dsl.query("INSERT INTO $table (${quoteIdentifier(nameColumn)}) VALUES (?)", name)
                },
            ).execute()
        }
        toInsert.chunked(CHUNK_SIZE).forEach { chunk ->
            cache.putAll(selectNameIds(dsl, table, nameColumn, chunk))
        }
    }

    private fun selectNameIds(
        dsl: DSLContext,
        table: String,
        nameColumn: String,
        names: List<String>,
    ): Map<String, Int> {
        if (names.isEmpty()) return emptyMap()
        val placeholders = names.joinToString(", ") { "?" }
        return dsl
            .resultQuery(
                "SELECT ID, ${quoteIdentifier(nameColumn)} FROM $table WHERE ${quoteIdentifier(nameColumn)} IN ($placeholders)",
                *names.toTypedArray(),
            )
            .fetch()
            .associate { record ->
                record.get(nameColumn, String::class.java) to record.get("ID", Int::class.java)
            }
    }

    private fun insertBooks(
        dsl: DSLContext,
        staging: StagingTables,
        books: List<ParsedBook>,
        seriesIds: Map<String, Int>,
        createdAt: Instant,
    ) {
        books.chunked(CHUNK_SIZE).forEach { chunk ->
            dsl.batch(
                chunk.map { book ->
                    val seriesId = book.series?.let(seriesIds::getValue)
                    dsl.query(
                        """
                        INSERT INTO ${staging.books}
                            (ID, TITLE, LANGUAGE, SERIES_ID, SERIES_NUMBER, FILE_FORMAT,
                             FILE_PATH, ARCHIVE_PATH, FILE_SIZE, DATE_ADDED, CREATED_AT)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        book.bookId,
                        book.title,
                        book.language,
                        seriesId,
                        book.seriesNumber,
                        book.fileFormat,
                        book.filePath,
                        book.archivePath,
                        book.fileSize,
                        book.dateAdded.toString(),
                        createdAt.toString(),
                    )
                },
            ).execute()
        }
    }

    private fun insertBookAuthors(
        dsl: DSLContext,
        staging: StagingTables,
        books: List<ParsedBook>,
        authorIds: Map<String, Int>,
    ) {
        val pairs = books.flatMap { book ->
            book.authors.distinct().map { author -> book.bookId to authorIds.getValue(author) }
        }
        pairs.chunked(CHUNK_SIZE).forEach { chunk ->
            dsl.batch(
                chunk.map { (bookId, authorId) ->
                    dsl.query(
                        "INSERT INTO ${staging.bookAuthors} (BOOK_ID, AUTHOR_ID) VALUES (?, ?)",
                        bookId,
                        authorId,
                    )
                },
            ).execute()
        }
    }

    private fun insertBookGenres(
        dsl: DSLContext,
        staging: StagingTables,
        books: List<ParsedBook>,
        genreIds: Map<String, Int>,
    ) {
        val pairs = books.flatMap { book ->
            book.genres.distinct().map { genre -> book.bookId to genreIds.getValue(genre) }
        }
        pairs.chunked(CHUNK_SIZE).forEach { chunk ->
            dsl.batch(
                chunk.map { (bookId, genreId) ->
                    dsl.query(
                        "INSERT INTO ${staging.bookGenres} (BOOK_ID, GENRE_ID) VALUES (?, ?)",
                        bookId,
                        genreId,
                    )
                },
            ).execute()
        }
    }

    context(_: TransactionContext)
    private fun swapLiveCatalog(staging: StagingTables) = useTx { dsl ->
        dsl.execute("DELETE FROM BOOK_AUTHORS")
        dsl.execute("DELETE FROM BOOK_GENRES")
        dsl.execute("DELETE FROM BOOKS")
        dsl.execute("DELETE FROM AUTHORS")
        dsl.execute("DELETE FROM SERIES")
        dsl.execute("DELETE FROM GENRES")

        dsl.execute("INSERT INTO AUTHORS (ID, FULL_NAME) SELECT ID, FULL_NAME FROM ${staging.authors}")
        dsl.execute("INSERT INTO SERIES (ID, NAME) SELECT ID, NAME FROM ${staging.series}")
        dsl.execute("INSERT INTO GENRES (ID, NAME) SELECT ID, NAME FROM ${staging.genres}")
        dsl.execute(
            """
            INSERT INTO BOOKS
                (ID, TITLE, LANGUAGE, SERIES_ID, SERIES_NUMBER, FILE_FORMAT,
                 FILE_PATH, ARCHIVE_PATH, FILE_SIZE, DATE_ADDED, CREATED_AT)
            SELECT ID, TITLE, LANGUAGE, SERIES_ID, SERIES_NUMBER, FILE_FORMAT,
                   FILE_PATH, ARCHIVE_PATH, FILE_SIZE, DATE_ADDED, CREATED_AT
            FROM ${staging.books}
            """.trimIndent(),
        )
        dsl.execute("INSERT INTO BOOK_AUTHORS (BOOK_ID, AUTHOR_ID) SELECT BOOK_ID, AUTHOR_ID FROM ${staging.bookAuthors}")
        dsl.execute("INSERT INTO BOOK_GENRES (BOOK_ID, GENRE_ID) SELECT BOOK_ID, GENRE_ID FROM ${staging.bookGenres}")
    }

    context(_: TransactionContext)
    private fun cleanupStaleStagingTables() = useTx { dsl ->
        val staleTables = dsl
            .resultQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'IMPORT_%'")
            .fetch("name", String::class.java)
            .filterNotNull()

        staleTables.forEach { table ->
            dsl.execute("DROP TABLE IF EXISTS ${quoteIdentifier(table)}")
        }
    }

    context(_: TransactionContext)
    private fun dropStagingTables(staging: StagingTables) = useTx { dsl ->
        staging.allChildrenFirst.forEach { table ->
            dsl.execute("DROP TABLE IF EXISTS $table")
        }
    }

    private suspend fun bestEffortDrop(staging: StagingTables) {
        try {
            transactionProvider.transaction(READ_WRITE) {
                dropStagingTables(staging)
            }
        } catch (dropError: Exception) {
            log.warn("Failed to clean up staging tables after import failure", dropError)
        }
    }

    private fun newStagingTables(): StagingTables {
        val runId = UUID.randomUUID().toString().replace("-", "_").uppercase()
        fun name(suffix: String) = quoteIdentifier("IMPORT_${runId}_$suffix")
        return StagingTables(
            books = name("BOOKS"),
            authors = name("AUTHORS"),
            series = name("SERIES"),
            genres = name("GENRES"),
            bookAuthors = name("BOOK_AUTHORS"),
            bookGenres = name("BOOK_GENRES"),
        )
    }

    private fun quoteIdentifier(identifier: String): String =
        "\"${identifier.replace("\"", "\"\"")}\""

    private fun parseBookLine(
        line: String,
        archivePath: String,
        stats: ImportStats,
    ): ParsedBook? {
        return try {
            val parts = line.split('') // Field separator in INP files
            if (parts.size < 8) {
                recordInvalidBook(stats, "Invalid line: $line")
                return null
            }

            val authorPart = parts[0]
            val genre = parts[1]
            val title = parts[2]
            val seriesPart = parts[3]
            val seriesNumber = parts[4].toIntOrNull()
            val bookId = parts[5].toIntOrNull()
            val fileSize = parts[6].toIntOrNull()
            val deleted = parts.getOrNull(8)
            val fileFormat = parts.getOrNull(9)
            val dateAdded = parts.getOrNull(10)
            val language = parts.getOrNull(11) ?: "ru"

            val warnings = buildList {
                if (bookId == null) add("Invalid bookId: $bookId")
                if (fileSize == null) add("Invalid fileSize: $fileSize")
                if (fileFormat == null) add("Invalid fileFormat: $fileFormat")
            }

            if (warnings.isNotEmpty()) {
                recordInvalidBook(stats, "Invalid book: $line. Warnings: $warnings")
                return null
            }

            if (deleted == "1") {
                log.debug("Book $bookId deleted upstream")
                stats.resetSequentialBookErrors()
                stats.incDeletedBooks()
                return null
            }

            val authors = parseAuthors(authorPart)
            if (authors.isEmpty()) {
                recordInvalidBook(stats, "No authors for book $bookId")
                return null
            }

            val series = if (seriesPart.isNotBlank()) seriesPart.trim() else null
            val genres = if (genre.isNotBlank()) {
                genre.split(':')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }

            val filePath = "$bookId.$fileFormat"

            stats.resetSequentialBookErrors()
            stats.incAddedBooks()

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
        } catch (e: ImportAbortedException) {
            throw e
        } catch (e: Exception) {
            recordInvalidBook(stats, "Error parsing line: $line", e)
            null
        }
    }

    private fun recordInvalidBook(
        stats: ImportStats,
        message: String,
        exception: Exception? = null,
    ) {
        if (exception == null) {
            log.warn(message)
        } else {
            log.error(message, exception)
        }

        val sequentialErrors = stats.incInvalidBooks()
        if (sequentialErrors >= ImportStats.MAX_SEQUENTIAL_BOOK_ERRORS) {
            val abortMessage =
                "Stopping import after $sequentialErrors sequential invalid book records"
            stats.addMessage(abortMessage)
            throw ImportAbortedException(abortMessage)
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

    private companion object : Logger() {
        private const val CHUNK_SIZE = 1000
        private val ARCHIVE_RANGE_REGEX = Regex(""".*?(\d+)-(\d+)$""")
    }
}

private class ImportAbortedException(
    message: String,
) : IllegalStateException(message)
