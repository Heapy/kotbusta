package io.heapy.kotbusta.dao.book

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.USER_STARS
import io.heapy.kotbusta.ktor.UserSession

class UnstarBookQuery {
    context(_: TransactionContext, userSession: UserSession)
    fun unstarBook(bookId: Long): Boolean = useTx { dslContext ->
        val deleted = dslContext
            .deleteFrom(USER_STARS)
            .where(USER_STARS.USER_ID.eq(userSession.userId))
            .and(USER_STARS.BOOK_ID.eq(bookId))
            .execute()

        deleted > 0
    }
}
