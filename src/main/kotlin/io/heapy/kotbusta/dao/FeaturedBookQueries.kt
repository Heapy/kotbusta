package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.FEATURED_BOOKS
import io.heapy.kotbusta.model.BookSummary
import kotlin.time.Instant

const val LIVELIB_TOP_SOURCE = "livelib_top"

data class FeaturedBookInsert(
    val bookId: Int,
    val externalTitle: String,
    val externalAuthor: String,
    val rating: Double,
    val sourceRank: Int,
)

/**
 * Replaces the whole snapshot for [source]: clears previously stored rows for
 * that source, then inserts [items]. Full delete-then-insert keeps this simple
 * for the small (tens of rows), once-a-day volume a scrape produces.
 */
context(_: TransactionContext)
fun replaceFeaturedBooks(
    source: String,
    fetchedAt: Instant,
    items: List<FeaturedBookInsert>,
): Unit = useTx { dslContext ->
    dslContext
        .deleteFrom(FEATURED_BOOKS)
        .where(FEATURED_BOOKS.SOURCE.eq(source))
        .execute()

    items.forEach { item ->
        dslContext
            .insertInto(FEATURED_BOOKS)
            .set(FEATURED_BOOKS.BOOK_ID, item.bookId)
            .set(FEATURED_BOOKS.SOURCE, source)
            .set(FEATURED_BOOKS.EXTERNAL_TITLE, item.externalTitle)
            .set(FEATURED_BOOKS.EXTERNAL_AUTHOR, item.externalAuthor)
            .set(FEATURED_BOOKS.RATING, item.rating.toFloat())
            .set(FEATURED_BOOKS.SOURCE_RANK, item.sourceRank)
            .set(FEATURED_BOOKS.FETCHED_AT, fetchedAt)
            .execute()
    }
}

context(_: TransactionContext)
fun getFeaturedBooks(
    source: String,
    limit: Int,
): List<BookSummary> = useTx { dslContext ->
    val rows = dslContext
        .select(FEATURED_BOOKS.BOOK_ID, FEATURED_BOOKS.RATING)
        .from(FEATURED_BOOKS)
        .where(FEATURED_BOOKS.SOURCE.eq(source))
        .orderBy(FEATURED_BOOKS.RATING.desc(), FEATURED_BOOKS.SOURCE_RANK.asc())
        .limit(limit)
        .fetch()

    val orderedIds = rows.mapNotNull { it.get(FEATURED_BOOKS.BOOK_ID) }
    val ratingByBookId = rows.associate {
        it.get(FEATURED_BOOKS.BOOK_ID)!! to it.get(FEATURED_BOOKS.RATING)!!.toDouble()
    }

    getBookSummariesByIds(orderedIds).map { summary ->
        summary.copy(
            rating = ratingByBookId[summary.id],
            ratingSource = source,
        )
    }
}
