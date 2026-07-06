package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.AUTHORS
import io.heapy.kotbusta.jooq.tables.references.BOOKS
import io.heapy.kotbusta.jooq.tables.references.BOOK_AUTHORS
import io.heapy.kotbusta.jooq.tables.references.BOOK_ENRICHMENT
import io.heapy.kotbusta.jooq.tables.references.BOOK_GENRES
import io.heapy.kotbusta.jooq.tables.references.GENRES
import io.heapy.kotbusta.jooq.tables.references.SERIES
import io.heapy.kotbusta.model.Author
import io.heapy.kotbusta.model.Book
import io.heapy.kotbusta.model.BookSummary
import io.heapy.kotbusta.model.SearchIndexBook
import io.heapy.kotbusta.model.SearchResult
import io.heapy.kotbusta.model.Series
import io.heapy.kotbusta.service.EmbeddingCodec
import org.jooq.Condition
import org.jooq.Record
import org.jooq.Result
import org.jooq.impl.DSL

context(_: TransactionContext)
fun getBooks(
    limit: Int,
    offset: Int,
): SearchResult = useTx { dslContext ->
    val results = dslContext
        .select(
            BOOKS.ID,
            BOOKS.TITLE,
            BOOKS.LANGUAGE,
            BOOKS.SERIES_ID,
            BOOKS.SERIES_NUMBER,
            SERIES.NAME.`as`("series_name"),
        )
        .from(BOOKS)
        .leftJoin(SERIES).on(BOOKS.SERIES_ID.eq(SERIES.ID))
        .orderBy(BOOKS.ID.desc())
        .limit(limit)
        .offset(offset)
        .fetch()

    val total = dslContext
        .selectCount()
        .from(BOOKS)
        .fetchOne(0, Long::class.java)
        ?: 0L

    SearchResult(
        books = buildBookSummaryList(results),
        total = total,
        hasMore = offset + limit < total,
    )
}

context(_: TransactionContext)
fun getBookById(
    bookId: Int,
): Book? = useTx { dslContext ->
    val record = dslContext
        .select(
            BOOKS.ID,
            BOOKS.TITLE,
            BOOK_ENRICHMENT.ANNOTATION,
            BOOKS.LANGUAGE,
            BOOKS.FILE_PATH,
            BOOKS.ARCHIVE_PATH,
            BOOKS.FILE_SIZE,
            BOOKS.DATE_ADDED,
            BOOKS.SERIES_NUMBER,
            BOOKS.SERIES_ID,
            SERIES.NAME.`as`("series_name"),
        )
        .from(BOOKS)
        .leftJoin(SERIES).on(BOOKS.SERIES_ID.eq(SERIES.ID))
        .leftJoin(BOOK_ENRICHMENT).on(BOOKS.ID.eq(BOOK_ENRICHMENT.BOOK_ID))
        .where(BOOKS.ID.eq(bookId))
        .fetchOne() ?: return@useTx null

    val authors = getBookAuthors(bookId)
    val genres = getBookGenres(bookId)
    val series = record.get("series_name", String::class.java)
        ?.let { name ->
            Series(
                id = record.get(BOOKS.SERIES_ID)!!,
                name = name,
            )
        }

    Book(
        id = record.get(BOOKS.ID)!!,
        title = record.get(BOOKS.TITLE)!!,
        annotation = record.get(BOOK_ENRICHMENT.ANNOTATION),
        genres = genres,
        language = record.get(BOOKS.LANGUAGE)!!,
        authors = authors,
        series = series,
        seriesNumber = record.get(BOOKS.SERIES_NUMBER)?.takeIf { it != 0 },
        filePath = record.get(BOOKS.FILE_PATH)!!,
        archivePath = record.get(BOOKS.ARCHIVE_PATH)!!,
        fileSize = record.get(BOOKS.FILE_SIZE)?.takeIf { it != 0 },
        dateAdded = record.get(BOOKS.DATE_ADDED)!!,
        coverImageUrl = "/api/books/$bookId/cover",
    )
}

context(_: TransactionContext)
fun getSimilarBooks(
    bookId: Int,
    limit: Int,
): List<BookSummary> = useTx { dslContext ->
    val genreIds = dslContext
        .select(BOOK_GENRES.GENRE_ID)
        .from(BOOK_GENRES)
        .where(BOOK_GENRES.BOOK_ID.eq(bookId))
        .fetch(BOOK_GENRES.GENRE_ID)
        .filterNotNull()

    val authorIds = dslContext
        .select(BOOK_AUTHORS.AUTHOR_ID)
        .from(BOOK_AUTHORS)
        .where(BOOK_AUTHORS.BOOK_ID.eq(bookId))
        .fetch(BOOK_AUTHORS.AUTHOR_ID)
        .filterNotNull()

    if (genreIds.isEmpty() && authorIds.isEmpty()) {
        return@useTx emptyList()
    }

    val relatedCondition = mutableListOf<Condition>().apply {
        if (genreIds.isNotEmpty()) {
            add(
                BOOKS.ID.`in`(
                    DSL.select(BOOK_GENRES.BOOK_ID)
                        .from(BOOK_GENRES)
                        .where(BOOK_GENRES.GENRE_ID.`in`(genreIds)),
                ),
            )
        }
        if (authorIds.isNotEmpty()) {
            add(
                BOOKS.ID.`in`(
                    DSL.select(BOOK_AUTHORS.BOOK_ID)
                        .from(BOOK_AUTHORS)
                        .where(BOOK_AUTHORS.AUTHOR_ID.`in`(authorIds)),
                ),
            )
        }
    }.reduce(Condition::or)

    val results = dslContext
        .select(
            BOOKS.ID,
            BOOKS.TITLE,
            BOOKS.LANGUAGE,
            BOOKS.SERIES_ID,
            BOOKS.SERIES_NUMBER,
            SERIES.NAME.`as`("series_name"),
        )
        .from(BOOKS)
        .leftJoin(SERIES).on(BOOKS.SERIES_ID.eq(SERIES.ID))
        .where(BOOKS.ID.ne(bookId))
        .and(relatedCondition)
        .orderBy(BOOKS.ID.desc())
        .limit(limit)
        .fetch()

    buildBookSummaryList(results)
}

/**
 * Returns a page of books for search indexing using keyset pagination on the
 * primary key (ids greater than [afterId], ascending). Lets the index build
 * stream the whole catalog in bounded-memory batches.
 */
context(_: TransactionContext)
fun getSearchIndexBooksPage(
    afterId: Int,
    limit: Int,
): List<SearchIndexBook> = useTx { dslContext ->
    val baseRecords = dslContext
        .select(
            BOOKS.ID,
            BOOKS.TITLE,
            BOOKS.LANGUAGE,
            BOOK_ENRICHMENT.ANNOTATION,
            BOOK_ENRICHMENT.EMBEDDING,
            SERIES.NAME.`as`("series_name"),
        )
        .from(BOOKS)
        .leftJoin(SERIES).on(BOOKS.SERIES_ID.eq(SERIES.ID))
        .leftJoin(BOOK_ENRICHMENT).on(BOOKS.ID.eq(BOOK_ENRICHMENT.BOOK_ID))
        .where(BOOKS.ID.gt(afterId))
        .orderBy(BOOKS.ID.asc())
        .limit(limit)
        .fetch()

    if (baseRecords.isEmpty()) {
        return@useTx emptyList()
    }

    val bookIds = baseRecords.mapNotNull { it.get(BOOKS.ID) }
    val authorsByBookId = getBookAuthorsMap(bookIds)
    val genresByBookId = getBookGenresMap(bookIds)

    baseRecords.map { record ->
        val bookId = record.get(BOOKS.ID)!!
        SearchIndexBook(
            bookId = bookId,
            title = record.get(BOOKS.TITLE)!!,
            authors = authorsByBookId[bookId] ?: emptyList(),
            series = record.get("series_name", String::class.java),
            language = record.get(BOOKS.LANGUAGE)!!,
            genres = genresByBookId[bookId] ?: emptyList(),
            annotation = record.get(BOOK_ENRICHMENT.ANNOTATION),
            embedding = EmbeddingCodec.decode(record.get(BOOK_ENRICHMENT.EMBEDDING)),
        )
    }
}

context(_: TransactionContext)
fun getBookSummariesByIds(
    bookIds: List<Int>,
): List<BookSummary> = useTx { dslContext ->
    if (bookIds.isEmpty()) {
        return@useTx emptyList()
    }

    val results = dslContext
        .selectDistinct(
            BOOKS.ID,
            BOOKS.TITLE,
            BOOKS.LANGUAGE,
            BOOKS.SERIES_ID,
            BOOKS.SERIES_NUMBER,
            SERIES.NAME.`as`("series_name"),
        )
        .from(BOOKS)
        .leftJoin(SERIES).on(BOOKS.SERIES_ID.eq(SERIES.ID))
        .where(BOOKS.ID.`in`(bookIds))
        .fetch()

    val booksById = buildBookSummaryList(results).associateBy(BookSummary::id)
    bookIds.mapNotNull(booksById::get)
}

context(_: TransactionContext)
fun getBookEmbedding(bookId: Int): FloatArray? = useTx { dslContext ->
    dslContext
        .select(BOOK_ENRICHMENT.EMBEDDING)
        .from(BOOK_ENRICHMENT)
        .where(BOOK_ENRICHMENT.BOOK_ID.eq(bookId))
        .fetchOne(BOOK_ENRICHMENT.EMBEDDING)
        ?.let(EmbeddingCodec::decode)
}

private context(_: TransactionContext)
fun getBookAuthors(
    bookId: Int,
): List<Author> = useTx { dslContext ->
    dslContext
        .select(
            AUTHORS.ID,
            AUTHORS.FULL_NAME,
        )
        .from(AUTHORS)
        .innerJoin(BOOK_AUTHORS).on(AUTHORS.ID.eq(BOOK_AUTHORS.AUTHOR_ID))
        .where(BOOK_AUTHORS.BOOK_ID.eq(bookId))
        .fetch { record ->
            Author(
                id = record.get(AUTHORS.ID)!!,
                fullName = record.get(AUTHORS.FULL_NAME)!!,
            )
        }
}

context(_: TransactionContext)
fun getBookGenres(
    bookId: Int,
): List<String> = useTx { dslContext ->
    dslContext
        .select(GENRES.NAME)
        .from(GENRES)
        .innerJoin(BOOK_GENRES).on(GENRES.ID.eq(BOOK_GENRES.GENRE_ID))
        .where(BOOK_GENRES.BOOK_ID.eq(bookId))
        .fetch(GENRES.NAME)
        .filterNotNull()
}

private context(_: TransactionContext)
fun buildBookSummaryList(
    results: Result<out Record>,
): List<BookSummary> =
    useTx {
        val books = mutableListOf<BookSummary>()
        val bookIds = mutableSetOf<Int>()

        results.forEach { record ->
            val bookId = record.get(BOOKS.ID)!!
            bookIds.add(bookId)
            books.add(
                BookSummary(
                    id = bookId,
                    title = record.get(BOOKS.TITLE)!!,
                    authors = emptyList(),
                    genres = emptyList(),
                    language = record.get(BOOKS.LANGUAGE)!!,
                    series = record.get("series_name", String::class.java),
                    seriesNumber = record.get(BOOKS.SERIES_NUMBER)
                        ?.takeIf { it != 0 },
                    coverImageUrl = "/api/books/$bookId/cover",
                ),
            )
        }

        val bookAuthors = getBookAuthorsMap(bookIds)
        val bookGenres = getBookGenresMap(bookIds)

        books.map { book ->
            book.copy(
                authors = bookAuthors[book.id] ?: emptyList(),
                genres = bookGenres[book.id] ?: emptyList(),
            )
        }
    }

internal context(_: TransactionContext)
fun getBookAuthorsMap(bookIds: Collection<Int>): Map<Int, List<String>> = useTx { dslContext ->
    if (bookIds.isEmpty()) {
        return@useTx emptyMap()
    }

    val normalizedIds = bookIds.distinct()
    val bookAuthors = mutableMapOf<Int, MutableList<String>>()
    normalizedIds.chunked(SQLITE_ID_LOOKUP_BATCH_SIZE).forEach { batch ->
        dslContext
            .select(
                BOOK_AUTHORS.BOOK_ID,
                AUTHORS.FULL_NAME,
            )
            .from(BOOK_AUTHORS)
            .innerJoin(AUTHORS).on(BOOK_AUTHORS.AUTHOR_ID.eq(AUTHORS.ID))
            .where(BOOK_AUTHORS.BOOK_ID.`in`(batch))
            .fetch()
            .forEach { record ->
                val bookId = record.get(BOOK_AUTHORS.BOOK_ID)!!
                val authorName = record.get(AUTHORS.FULL_NAME)!!
                bookAuthors.computeIfAbsent(bookId) { mutableListOf() }
                    .add(authorName)
            }
    }

    bookAuthors
}

internal context(_: TransactionContext)
fun getBookGenresMap(bookIds: Collection<Int>): Map<Int, List<String>> = useTx { dslContext ->
    if (bookIds.isEmpty()) {
        return@useTx emptyMap()
    }

    val normalizedIds = bookIds.distinct()
    val bookGenres = mutableMapOf<Int, MutableList<String>>()
    normalizedIds.chunked(SQLITE_ID_LOOKUP_BATCH_SIZE).forEach { batch ->
        dslContext
            .select(
                BOOK_GENRES.BOOK_ID,
                GENRES.NAME,
            )
            .from(BOOK_GENRES)
            .innerJoin(GENRES).on(BOOK_GENRES.GENRE_ID.eq(GENRES.ID))
            .where(BOOK_GENRES.BOOK_ID.`in`(batch))
            .fetch()
            .forEach { record ->
                val bookId = record.get(BOOK_GENRES.BOOK_ID)!!
                val genreName = record.get(GENRES.NAME)!!
                bookGenres.computeIfAbsent(bookId) { mutableListOf() }
                    .add(genreName)
            }
    }

    bookGenres
}

private const val SQLITE_ID_LOOKUP_BATCH_SIZE = 900
