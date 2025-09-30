package io.heapy.kotbusta.dao.book

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.*
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.BookSummary
import io.heapy.kotbusta.model.SearchQuery
import io.heapy.kotbusta.model.SearchResult
import org.jooq.Condition
import org.jooq.Record
import org.jooq.Result
import org.jooq.impl.DSL

class SearchBooksQuery {
    context(_: TransactionContext, userSession: UserSession)
    fun searchBooks(query: SearchQuery): SearchResult = useTx { dslContext ->
        val conditions = buildConditions(query)

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

        val books = buildBookSummaryList(results)

        val total = getSearchResultsCount(query)

        SearchResult(
            books = books,
            total = total,
            hasMore = query.offset + query.limit < total
        )
    }

    context(_: TransactionContext)
    private fun buildConditions(query: SearchQuery): List<Condition> {
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

        return conditions
    }

    context(_: TransactionContext)
    private fun getSearchResultsCount(query: SearchQuery): Long = useTx { dslContext ->
        val conditions = buildConditions(query)

        val countQuery = dslContext
            .selectCount()
            .from(BOOKS)
            .leftJoin(BOOK_AUTHORS).on(BOOKS.ID.eq(BOOK_AUTHORS.BOOK_ID))
            .leftJoin(AUTHORS).on(BOOK_AUTHORS.AUTHOR_ID.eq(AUTHORS.ID))

        if (conditions.isNotEmpty()) {
            countQuery.where(DSL.and(conditions))
        } else {
            countQuery
        }.fetchOne(0, Long::class.java) ?: 0L
    }

    context(_: TransactionContext)
    private fun buildBookSummaryList(results: Result<out Record>): List<BookSummary> = useTx { dslContext ->
        val books = mutableListOf<BookSummary>()
        val bookIds = mutableSetOf<Long>()

        results.forEach { record ->
            val bookId = record.get(BOOKS.ID)!!
            bookIds.add(bookId)
            books.add(
                BookSummary(
                    id = bookId,
                    title = record.get(BOOKS.TITLE)!!,
                    authors = emptyList(),
                    genre = record.get(BOOKS.GENRE),
                    language = record.get(BOOKS.LANGUAGE)!!,
                    series = record.get("series_name", String::class.java),
                    seriesNumber = record.get(BOOKS.SERIES_NUMBER)?.takeIf { it != 0 },
                    coverImageUrl = "/api/books/${bookId}/cover",
                    isStarred = record.get("is_starred", Boolean::class.java) ?: false
                )
            )
        }

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

        books.map { book ->
            book.copy(authors = bookAuthors[book.id] ?: emptyList())
        }
    }
}
