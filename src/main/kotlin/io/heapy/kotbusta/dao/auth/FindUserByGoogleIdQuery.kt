package io.heapy.kotbusta.dao.auth

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.dslContext
import io.heapy.kotbusta.jooq.tables.records.UsersRecord
import io.heapy.kotbusta.jooq.tables.references.USERS

class FindUserByGoogleIdQuery {
    context(_: TransactionContext)
    fun findByGoogleId(
        googleId: String,
    ): UsersRecord? = dslContext { dslContext ->
        dslContext
            .selectFrom(USERS)
            .where(USERS.GOOGLE_ID.eq(googleId))
            .fetchOne()
    }
}
