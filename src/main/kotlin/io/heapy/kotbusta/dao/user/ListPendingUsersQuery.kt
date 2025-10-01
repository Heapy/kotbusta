package io.heapy.kotbusta.dao.user

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.enums.UserStatusEnum
import io.heapy.kotbusta.jooq.tables.references.USERS
import io.heapy.kotbusta.mapper.mapUsing
import io.heapy.kotbusta.model.User

class ListPendingUsersQuery {
    context(_: TransactionContext)
    fun listPending(
        limit: Int = 20,
        offset: Int = 0
    ): List<User> = useTx { dslContext ->
        dslContext
            .select(
                USERS.ID,
                USERS.GOOGLE_ID,
                USERS.EMAIL,
                USERS.NAME,
                USERS.AVATAR_URL,
                USERS.STATUS,
                USERS.CREATED_AT,
                USERS.UPDATED_AT
            )
            .from(USERS)
            .where(USERS.STATUS.eq(UserStatusEnum.PENDING))
            .orderBy(USERS.CREATED_AT.desc())
            .limit(limit)
            .offset(offset)
            .fetch { record ->
                User(
                    id = record.get(USERS.ID)!!,
                    googleId = record.get(USERS.GOOGLE_ID)!!,
                    email = record.get(USERS.EMAIL)!!,
                    name = record.get(USERS.NAME)!!,
                    avatarUrl = record.get(USERS.AVATAR_URL),
                    status = record.get(USERS.STATUS)!!.mapUsing(UserStatusMapper),
                    createdAt = record.get(USERS.CREATED_AT)!!.toEpochSecond(),
                    updatedAt = record.get(USERS.UPDATED_AT)!!.toEpochSecond()
                )
            }
    }

    context(_: TransactionContext)
    fun countPending(): Long = useTx { dslContext ->
        dslContext
            .selectCount()
            .from(USERS)
            .where(USERS.STATUS.eq(UserStatusEnum.PENDING))
            .fetchOne(0, Long::class.java) ?: 0L
    }
}
