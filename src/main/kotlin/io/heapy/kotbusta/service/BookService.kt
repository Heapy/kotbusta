package io.heapy.kotbusta.service

import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.dslContext
import io.heapy.kotbusta.jooq.tables.references.*
import io.heapy.kotbusta.model.*
import org.jooq.*
import org.jooq.impl.DSL
import java.time.OffsetDateTime

class BookService {
    context(_: TransactionContext)
    fun getBooks(limit: Int = 20, offset: Int = 0, userId: Long? = null): SearchResult = dslContext { dslContext ->
        val books = getBooksList(dslContext, limit, offset, userId)
        val total = getTotalBooksCount(dslContext)

        SearchResult(
            books = books,
            total = total,
            hasMore = offset + limit < total
        )
    }

    context(_: TransactionContext, userSession: UserSession)
    fun searchBooks(query: SearchQuery): SearchResult = dslContext { dslContext ->
        val books = searchBooksList(dslContext, query)
        val total = getSearchResultsCount(dslContext, query)

        SearchResult(
            books = books,
            total = total,
            hasMore = query.offset + query.limit < total
        )
    }

    context(_: TransactionContext, userSession: UserSession)
    fun getBookById(bookId: Long): Book? = dslContext { dslContext ->
        getBookDetails(dslContext, bookId)
    }

    context(_: TransactionContext, userSession: UserSession)
    fun getSimilarBooks(bookId: Long, limit: Int = 10): List<BookSummary> = dslContext { dslContext ->
        // Get book details to find similar books
        val book = getBookDetails(dslContext, bookId) ?: return@dslContext emptyList()

        // Get author IDs for the book
        val authorIds = dslContext
            .select(BOOK_AUTHORS.AUTHOR_ID)
            .from(BOOK_AUTHORS)
            .where(BOOK_AUTHORS.BOOK_ID.eq(bookId))
            .fetch(BOOK_AUTHORS.AUTHOR_ID)

        // Build query for similar books
        val query = dslContext
            .selectDistinct(
                BOOKS.ID,
                BOOKS.TITLE,
                BOOKS.GENRE,
                BOOKS.LANGUAGE,
                BOOKS.SERIES_ID,
                BOOKS.SERIES_NUMBER,
                SERIES.NAME.`as`("series_name"),
                DSL.case_()
                    .`when`(USER_STARS.BOOK_ID.isNotNull, DSL.inline(true))
                    .otherwise(DSL.inline(false))
                    .`as`("is_starred"),
                DSL.case_()
                    .`when`(BOOKS.GENRE.eq(book.genre), DSL.inline(1))
                    .otherwise(DSL.inline(0))
                    .`as`("genre_match_score")
            )
            .from(BOOKS)
            .leftJoin(SERIES).on(BOOKS.SERIES_ID.eq(SERIES.ID))
            .leftJoin(BOOK_AUTHORS).on(BOOKS.ID.eq(BOOK_AUTHORS.BOOK_ID))
            .leftJoin(AUTHORS).on(BOOK_AUTHORS.AUTHOR_ID.eq(AUTHORS.ID))
            .leftJoin(USER_STARS)
            .on(BOOKS.ID.eq(USER_STARS.BOOK_ID).and(USER_STARS.USER_ID.eq(userSession.userId)))

        val condition = BOOKS.ID.ne(bookId).and(
            BOOKS.GENRE.eq(book.genre).or(
                AUTHORS.ID.`in`(authorIds)
            )
        )

        val results = query
            .where(condition)
            .orderBy(
                DSL.field("genre_match_score", Integer::class.java).desc(),
                BOOKS.ID.desc()
            )
            .limit(limit)
            .fetch()

        buildBookSummaryList(results)
    }

    context(_: TransactionContext)
    fun getBookCover(bookId: Long): ByteArray? = dslContext { dslContext ->
        dslContext
            .select(BOOKS.COVER_IMAGE)
            .from(BOOKS)
            .where(BOOKS.ID.eq(bookId))
            .fetchOne(BOOKS.COVER_IMAGE)
    }

    context(_: TransactionContext, userSession: UserSession)
    fun starBook(bookId: Long): Boolean = dslContext { dslContext ->
        val inserted = dslContext
            .insertInto(USER_STARS)
            .set(USER_STARS.USER_ID, userSession.userId)
            .set(USER_STARS.BOOK_ID, bookId)
            .set(USER_STARS.CREATED_AT, OffsetDateTime.now())
            .onConflictDoNothing()
            .execute()

        inserted > 0
    }

    context(_: TransactionContext, userSession: UserSession)
    fun unstarBook(bookId: Long): Boolean = dslContext { dslContext ->
        val deleted = dslContext
            .deleteFrom(USER_STARS)
            .where(USER_STARS.USER_ID.eq(userSession.userId))
            .and(USER_STARS.BOOK_ID.eq(bookId))
            .execute()

        deleted > 0
    }

    context(_: TransactionContext, userSession: UserSession)
    fun getStarredBooks(
        limit: Int = 20,
        offset: Int = 0,
    ): SearchResult = dslContext { dslContext ->
        val results = dslContext
            .select(
                BOOKS.ID,
                BOOKS.TITLE,
                BOOKS.GENRE,
                BOOKS.LANGUAGE,
                BOOKS.SERIES_ID,
                BOOKS.SERIES_NUMBER,
                SERIES.NAME.`as`("series_name"),
                DSL.inline(true).`as`("is_starred")
            )
            .from(BOOKS)
            .leftJoin(SERIES).on(BOOKS.SERIES_ID.eq(SERIES.ID))
            .innerJoin(USER_STARS).on(BOOKS.ID.eq(USER_STARS.BOOK_ID))
            .where(USER_STARS.USER_ID.eq(userSession.userId))
            .orderBy(USER_STARS.CREATED_AT.desc())
            .limit(limit)
            .offset(offset)
            .fetch()

        val books = buildBookSummaryList(results)

        // Get total count
        val total = dslContext
            .selectCount()
            .from(USER_STARS)
            .where(USER_STARS.USER_ID.eq(userSession.userId))
            .fetchOne(0, Long::class.java) ?: 0L

        SearchResult(
            books = books,
            total = total,
            hasMore = offset + limit < total
        )
    }

    context(_: TransactionContext)
    private fun getBooksList(dslContext: DSLContext, limit: Int, offset: Int, userId: Long?): List<BookSummary> {
        var query = dslContext
            .select(
                BOOKS.ID,
                BOOKS.TITLE,
                BOOKS.GENRE,
                BOOKS.LANGUAGE,
                BOOKS.SERIES_ID,
                BOOKS.SERIES_NUMBER,
                SERIES.NAME.`as`("series_name")
            )
            .select(
                if (userId != null) {
                    DSL.case_()
                        .`when`(USER_STARS.BOOK_ID.isNotNull, DSL.inline(true))
                        .otherwise(DSL.inline(false))
                        .`as`("is_starred")
                } else {
                    DSL.inline(false).`as`("is_starred")
                }
            )
            .from(BOOKS)
            .leftJoin(SERIES).on(BOOKS.SERIES_ID.eq(SERIES.ID))

        if (userId != null) {
            query = query.leftJoin(USER_STARS)
                .on(BOOKS.ID.eq(USER_STARS.BOOK_ID).and(USER_STARS.USER_ID.eq(userId)))
        }

        val results = query
            .orderBy(BOOKS.ID.desc())
            .limit(limit)
            .offset(offset)
            .fetch()

        return buildBookSummaryList(results)
    }

    context(_: TransactionContext, userSession: UserSession)
    private fun searchBooksList(dslContext: DSLContext, query: SearchQuery): List<BookSummary> {
        val conditions = mutableListOf<Condition>()

        if (query.query.isNotBlank()) {
            val searchTerm = "%${query.query}%"
            conditions.add(
                BOOKS.TITLE.likeIgnoreCase(searchTerm)
                    .or(AUTHORS.FULL_NAME.likeIgnoreCase(searchTerm))
            )
        }

        if (!query.genre.isNullOrBlank()) {
            conditions.add(BOOKS.GENRE.eq(query.genre))
        }

        if (!query.language.isNullOrBlank()) {
            conditions.add(BOOKS.LANGUAGE.eq(query.language))
        }

        if (!query.author.isNullOrBlank()) {
            conditions.add(AUTHORS.FULL_NAME.likeIgnoreCase("%${query.author}%"))
        }

        val selectQuery = dslContext
            .selectDistinct(
                BOOKS.ID,
                BOOKS.TITLE,
                BOOKS.GENRE,
                BOOKS.LANGUAGE,
                BOOKS.SERIES_ID,
                BOOKS.SERIES_NUMBER,
                SERIES.NAME.`as`("series_name"),
                DSL.case_()
                    .`when`(USER_STARS.BOOK_ID.isNotNull, DSL.inline(true))
                    .otherwise(DSL.inline(false))
                    .`as`("is_starred")
            )
            .from(BOOKS)
            .leftJoin(SERIES).on(BOOKS.SERIES_ID.eq(SERIES.ID))
            .leftJoin(BOOK_AUTHORS).on(BOOKS.ID.eq(BOOK_AUTHORS.BOOK_ID))
            .leftJoin(AUTHORS).on(BOOK_AUTHORS.AUTHOR_ID.eq(AUTHORS.ID))
            .leftJoin(USER_STARS)
            .on(BOOKS.ID.eq(USER_STARS.BOOK_ID).and(USER_STARS.USER_ID.eq(userSession.userId)))

        val results = if (conditions.isNotEmpty()) {
            selectQuery.where(DSL.and(conditions))
        } else {
            selectQuery
        }
            .orderBy(BOOKS.ID.desc())
            .limit(query.limit)
            .offset(query.offset)
            .fetch()

        return buildBookSummaryList(results)
    }

    context(_: TransactionContext, userSession: UserSession)
    private fun getBookDetails(dslContext: DSLContext, bookId: Long): Book? {
        val record = dslContext
            .select(
                BOOKS.ID,
                BOOKS.TITLE,
                BOOKS.ANNOTATION,
                BOOKS.GENRE,
                BOOKS.LANGUAGE,
                BOOKS.FILE_PATH,
                BOOKS.ARCHIVE_PATH,
                BOOKS.FILE_SIZE,
                BOOKS.DATE_ADDED,
                BOOKS.SERIES_NUMBER,
                BOOKS.SERIES_ID,
                SERIES.NAME.`as`("series_name"),
                DSL.case_()
                    .`when`(USER_STARS.BOOK_ID.isNotNull, DSL.inline(true))
                    .otherwise(DSL.inline(false))
                    .`as`("is_starred"),
                USER_NOTES.NOTE.`as`("user_note"),
            )
            .from(BOOKS)
            .leftJoin(SERIES).on(BOOKS.SERIES_ID.eq(SERIES.ID))
            .leftJoin(USER_STARS)
            .on(BOOKS.ID.eq(USER_STARS.BOOK_ID).and(USER_STARS.USER_ID.eq(userSession.userId)))
            .leftJoin(USER_NOTES)
            .on(BOOKS.ID.eq(USER_NOTES.BOOK_ID).and(USER_NOTES.USER_ID.eq(userSession.userId)))
            .where(BOOKS.ID.eq(bookId))
            .fetchOne() ?: return null

        val authors = getBookAuthors(dslContext, bookId)
        val series = record.get("series_name", String::class.java)?.let {
            Series(record.get(BOOKS.SERIES_ID)!!, it)
        }

        return Book(
            id = record.get(BOOKS.ID)!!,
            title = record.get(BOOKS.TITLE)!!,
            annotation = record.get(BOOKS.ANNOTATION),
            genre = record.get(BOOKS.GENRE),
            language = record.get(BOOKS.LANGUAGE)!!,
            authors = authors,
            series = series,
            seriesNumber = record.get(BOOKS.SERIES_NUMBER)?.takeIf { it != 0 },
            filePath = record.get(BOOKS.FILE_PATH)!!,
            archivePath = record.get(BOOKS.ARCHIVE_PATH)!!,
            fileSize = record.get(BOOKS.FILE_SIZE)?.takeIf { it != 0L },
            dateAdded = record.get(BOOKS.DATE_ADDED)!!.toEpochSecond(),
            coverImageUrl = "/api/books/${bookId}/cover",
            isStarred = record.get("is_starred", Boolean::class.java) ?: false,
            userNote = record.get("user_note", String::class.java)
        )
    }

    context(_: TransactionContext)
    private fun getBookAuthors(dslContext: DSLContext, bookId: Long): List<Author> {
        return dslContext
            .select(
                AUTHORS.ID,
                AUTHORS.FIRST_NAME,
                AUTHORS.LAST_NAME,
                AUTHORS.FULL_NAME
            )
            .from(AUTHORS)
            .innerJoin(BOOK_AUTHORS).on(AUTHORS.ID.eq(BOOK_AUTHORS.AUTHOR_ID))
            .where(BOOK_AUTHORS.BOOK_ID.eq(bookId))
            .fetch { record ->
                Author(
                    id = record.get(AUTHORS.ID)!!,
                    firstName = record.get(AUTHORS.FIRST_NAME),
                    lastName = record.get(AUTHORS.LAST_NAME)!!,
                    fullName = record.get(AUTHORS.FULL_NAME)!!
                )
            }
    }

    context(_: TransactionContext)
    private fun buildBookSummaryList(results: Result<out Record>): List<BookSummary> = dslContext { dslContext ->
        val books = mutableListOf<BookSummary>()
        val bookIds = mutableSetOf<Long>()

        results.forEach { record ->
            val bookId = record.get(BOOKS.ID)!!
            bookIds.add(bookId)
            books.add(
                BookSummary(
                    id = bookId,
                    title = record.get(BOOKS.TITLE)!!,
                    authors = emptyList(), // Will be filled later
                    genre = record.get(BOOKS.GENRE),
                    language = record.get(BOOKS.LANGUAGE)!!,
                    series = record.get("series_name", String::class.java),
                    seriesNumber = record.get(BOOKS.SERIES_NUMBER)?.takeIf { it != 0 },
                    coverImageUrl = "/api/books/${bookId}/cover",
                    isStarred = record.get("is_starred", Boolean::class.java) ?: false
                )
            )
        }

        // Get authors for all books in one query
        val bookAuthors = mutableMapOf<Long, MutableList<String>>()
        if (bookIds.isNotEmpty()) {
            dslContext
                .select(
                    BOOK_AUTHORS.BOOK_ID,
                    AUTHORS.FULL_NAME
                )
                .from(BOOK_AUTHORS)
                .innerJoin(AUTHORS).on(BOOK_AUTHORS.AUTHOR_ID.eq(AUTHORS.ID))
                .where(BOOK_AUTHORS.BOOK_ID.`in`(bookIds))
                .fetch()
                .forEach { record ->
                    val bookId = record.get(BOOK_AUTHORS.BOOK_ID)!!
                    val authorName = record.get(AUTHORS.FULL_NAME)!!
                    bookAuthors.computeIfAbsent(bookId) { mutableListOf() }.add(authorName)
                }
        }

        // Update books with authors
        return@dslContext books.map { book ->
            book.copy(authors = bookAuthors[book.id] ?: emptyList())
        }
    }

    context(_: TransactionContext)
    private fun getTotalBooksCount(dslContext: DSLContext): Long {
        return dslContext
            .selectCount()
            .from(BOOKS)
            .fetchOne(0, Long::class.java) ?: 0L
    }

    context(_: TransactionContext)
    private fun getSearchResultsCount(dslContext: DSLContext, query: SearchQuery): Long {
        val conditions = mutableListOf<Condition>()

        if (query.query.isNotBlank()) {
            val searchTerm = "%${query.query}%"
            conditions.add(
                BOOKS.TITLE.likeIgnoreCase(searchTerm)
                    .or(AUTHORS.FULL_NAME.likeIgnoreCase(searchTerm))
            )
        }

        if (!query.genre.isNullOrBlank()) {
            conditions.add(BOOKS.GENRE.eq(query.genre))
        }

        if (!query.language.isNullOrBlank()) {
            conditions.add(BOOKS.LANGUAGE.eq(query.language))
        }

        if (!query.author.isNullOrBlank()) {
            conditions.add(AUTHORS.FULL_NAME.likeIgnoreCase("%${query.author}%"))
        }

        val countQuery = dslContext
            .selectCount()
            .from(BOOKS)
            .leftJoin(BOOK_AUTHORS).on(BOOKS.ID.eq(BOOK_AUTHORS.BOOK_ID))
            .leftJoin(AUTHORS).on(BOOK_AUTHORS.AUTHOR_ID.eq(AUTHORS.ID))

        return if (conditions.isNotEmpty()) {
            countQuery.where(DSL.and(conditions))
        } else {
            countQuery
        }.fetchOne(0, Long::class.java) ?: 0L
    }
}
