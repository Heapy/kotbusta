package io.heapy.kotbusta.dao.book

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.*
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.BookSummary
import org.jooq.Record
import org.jooq.Result
import org.jooq.impl.DSL

class GetSimilarBooksQuery {
    context(_: TransactionContext, userSession: UserSession)
    fun getSimilarBooks(bookId: Long, genre: String?, limit: Int): List<BookSummary> = useTx { dslContext ->
        if (genre == null) return@useTx emptyList()

        val authorIds = dslContext
            .select(BOOK_AUTHORS.AUTHOR_ID)
            .from(BOOK_AUTHORS)
            .where(BOOK_AUTHORS.BOOK_ID.eq(bookId))
            .fetch(BOOK_AUTHORS.AUTHOR_ID)

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
                    .`when`(BOOKS.GENRE.eq(genre), DSL.inline(1))
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
            BOOKS.GENRE.eq(genre).or(
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
