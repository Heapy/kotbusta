package io.heapy.kotbusta.dao.book

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.*
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.BookSummary
import io.heapy.kotbusta.model.SearchResult
import org.jooq.Record
import org.jooq.Result
import org.jooq.impl.DSL

class GetStarredBooksQuery {
    context(_: TransactionContext, userSession: UserSession)
    fun getStarredBooks(limit: Int, offset: Int): SearchResult = useTx { dslContext ->
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
