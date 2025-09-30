package io.heapy.kotbusta.dao.auth

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.USERS
import java.time.OffsetDateTime

class InsertUserQuery {
    context(_: TransactionContext)
    fun insertUser(
        googleId: String,
        email: String,
        name: String,
        avatarUrl: String?,
        createdAt: OffsetDateTime,
        updatedAt: OffsetDateTime
    ): Long = useTx { dslContext ->
        dslContext
            .insertInto(USERS)
            .set(USERS.GOOGLE_ID, googleId)
            .set(USERS.EMAIL, email)
            .set(USERS.NAME, name)
            .set(USERS.AVATAR_URL, avatarUrl)
            .set(USERS.CREATED_AT, createdAt)
            .set(USERS.UPDATED_AT, updatedAt)
            .returningResult(USERS.ID)
            .fetchOne(USERS.ID)
            ?: error("Failed to insert user")
    }
}
