package io.heapy.kotbusta.dao.auth

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.dslContext
import io.heapy.kotbusta.jooq.tables.references.USERS
import java.time.OffsetDateTime

class UpdateUserDao {
    context(_: TransactionContext)
    fun updateUser(
        userId: Long,
        email: String,
        name: String,
        avatarUrl: String?,
        updatedAt: OffsetDateTime
    ): Int = dslContext { dslContext ->
        dslContext
            .update(USERS)
            .set(USERS.EMAIL, email)
            .set(USERS.NAME, name)
            .set(USERS.AVATAR_URL, avatarUrl)
            .set(USERS.UPDATED_AT, updatedAt)
            .where(USERS.ID.eq(userId))
            .execute()
    }
}