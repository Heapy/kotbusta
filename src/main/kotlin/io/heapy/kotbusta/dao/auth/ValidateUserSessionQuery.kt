package io.heapy.kotbusta.dao.auth

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.USERS

class ValidateUserSessionQuery {
    context(_: TransactionContext)
    fun userExists(
        userId: Long,
    ): Boolean = useTx { dslContext ->
        dslContext
            .select(USERS.ID)
            .from(USERS)
            .where(USERS.ID.eq(userId))
            .fetchOne() != null
    }
}
