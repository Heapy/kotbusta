package io.heapy.kotbusta.dao.user

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.enums.UserStatusEnum
import io.heapy.kotbusta.jooq.tables.references.USERS

class GetUserStatusQuery {
    context(_: TransactionContext)
    fun getUserStatus(userId: Long): UserStatusEnum? = useTx { dslContext ->
        dslContext
            .select(USERS.STATUS)
            .from(USERS)
            .where(USERS.ID.eq(userId))
            .fetchOne(USERS.STATUS)
    }
}