package io.heapy.kotbusta.dao.user

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.enums.UserStatusEnum
import io.heapy.kotbusta.jooq.tables.references.USERS
import java.time.OffsetDateTime

class UpdateUserStatusQuery {
    context(_: TransactionContext)
    fun updateStatus(
        userId: Long,
        status: UserStatusEnum
    ): Boolean = useTx { dslContext ->
        val updated = dslContext
            .update(USERS)
            .set(USERS.STATUS, status)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .execute()

        updated > 0
    }
}