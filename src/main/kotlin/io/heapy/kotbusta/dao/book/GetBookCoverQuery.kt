package io.heapy.kotbusta.dao.book

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.BOOKS

class GetBookCoverQuery {
    context(_: TransactionContext)
    fun getBookCover(bookId: Long): ByteArray? = useTx { dslContext ->
        dslContext
            .select(BOOKS.COVER_IMAGE)
            .from(BOOKS)
            .where(BOOKS.ID.eq(bookId))
            .fetchOne(BOOKS.COVER_IMAGE)
    }
}