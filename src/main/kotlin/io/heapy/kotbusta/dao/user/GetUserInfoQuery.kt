package io.heapy.kotbusta.dao.user

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.USERS
import io.heapy.kotbusta.mapper.mapUsing
import io.heapy.kotbusta.model.UserInfo

class GetUserInfoQuery {
    context(_: TransactionContext)
    fun getUserInfo(userId: Long): UserInfo? = useTx { dslContext ->
        dslContext
            .select(
                USERS.ID,
                USERS.EMAIL,
                USERS.NAME,
                USERS.AVATAR_URL,
                USERS.STATUS
            )
            .from(USERS)
            .where(USERS.ID.eq(userId))
            .fetchOne { record ->
                UserInfo(
                    userId = record.get(USERS.ID)!!,
                    email = record.get(USERS.EMAIL)!!,
                    name = record.get(USERS.NAME)!!,
                    avatarUrl = record.get(USERS.AVATAR_URL),
                    status = record.get(USERS.STATUS)!!.mapUsing(UserStatusMapper)
                )
            }
    }
}