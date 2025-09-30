package io.heapy.kotbusta.dao.book

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.USER_STARS
import io.heapy.kotbusta.ktor.UserSession
import java.time.OffsetDateTime

class StarBookQuery {
    context(_: TransactionContext, userSession: UserSession)
    fun starBook(bookId: Long): Boolean = useTx { dslContext ->
        val inserted = dslContext
            .insertInto(USER_STARS)
            .set(USER_STARS.USER_ID, userSession.userId)
            .set(USER_STARS.BOOK_ID, bookId)
            .set(USER_STARS.CREATED_AT, OffsetDateTime.now())
            .onConflictDoNothing()
            .execute()

        inserted > 0
    }
}
