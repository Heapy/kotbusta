package io.heapy.kotbusta.dao.auth

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.dslContext
import io.heapy.kotbusta.jooq.tables.references.USERS

class ValidateUserSessionDao {
    context(_: TransactionContext)
    fun userExists(userId: Long): Boolean = dslContext { dslContext ->
        dslContext
            .select(USERS.ID)
            .from(USERS)
            .where(USERS.ID.eq(userId))
            .fetchOne() != null
    }
}