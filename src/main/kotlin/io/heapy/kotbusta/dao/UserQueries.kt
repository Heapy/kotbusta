package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.records.UsersRecord
import io.heapy.kotbusta.jooq.tables.references.USERS
import io.heapy.kotbusta.mapper.TypeMapper
import io.heapy.kotbusta.mapper.mapUsing
import io.heapy.kotbusta.model.User
import io.heapy.kotbusta.model.UserInfo
import io.heapy.kotbusta.model.UserStatus
import java.time.OffsetDateTime
import kotlin.time.Clock
import kotlin.time.Instant

val UserStatusMapper = TypeMapper<UserStatus, String>(
    left = { input -> input.name },
    right = { output -> UserStatus.valueOf(output) },
)

context(_: TransactionContext)
fun findUserByGoogleId(
    googleId: String,
): UsersRecord? = useTx { dslContext ->
    dslContext
        .selectFrom(USERS)
        .where(USERS.GOOGLE_ID.eq(googleId))
        .fetchOne()
}

context(_: TransactionContext)
fun insertUser(
    googleId: String,
    email: String,
    name: String,
    avatarUrl: String?,
    createdAt: Instant,
    updatedAt: Instant,
): Int = useTx { dslContext ->
    dslContext
        .insertInto(USERS)
        .set(USERS.GOOGLE_ID, googleId)
        .set(USERS.EMAIL, email)
        .set(USERS.NAME, name)
        .set(USERS.AVATAR_URL, avatarUrl)
        .set(USERS.STATUS, UserStatus.PENDING.mapUsing(UserStatusMapper))
        .set(USERS.CREATED_AT, createdAt)
        .set(USERS.UPDATED_AT, updatedAt)
        .returningResult(USERS.ID)
        .fetchOne(USERS.ID)
        ?: error("Failed to insert user")
}

context(_: TransactionContext)
fun updateUser(
    userId: Int,
    email: String,
    name: String,
    avatarUrl: String?,
    updatedAt: Instant,
): Int = useTx { dslContext ->
    dslContext
        .update(USERS)
        .set(USERS.EMAIL, email)
        .set(USERS.NAME, name)
        .set(USERS.AVATAR_URL, avatarUrl)
        .set(USERS.UPDATED_AT, updatedAt)
        .where(USERS.ID.eq(userId))
        .execute()
}

context(_: TransactionContext)
fun validateUserSession(
    userId: Int,
): Boolean = useTx { dslContext ->
    dslContext
        .select(USERS.ID)
        .from(USERS)
        .where(USERS.ID.eq(userId))
        .fetchOne() != null
}

context(_: TransactionContext)
fun getUserInfo(
    userId: Int,
): UserInfo? = useTx { dslContext ->
    dslContext
        .select(
            USERS.ID,
            USERS.EMAIL,
            USERS.NAME,
            USERS.AVATAR_URL,
            USERS.STATUS,
        )
        .from(USERS)
        .where(USERS.ID.eq(userId))
        .fetchOne { record ->
            UserInfo(
                userId = record.get(USERS.ID)!!,
                email = record.get(USERS.EMAIL)!!,
                name = record.get(USERS.NAME)!!,
                avatarUrl = record.get(USERS.AVATAR_URL),
                status = record.get(USERS.STATUS)!!.mapUsing(UserStatusMapper),
            )
        }
}


context(_: TransactionContext)
fun listPendingUsers(
    limit: Int = 20,
    offset: Int = 0,
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
            USERS.UPDATED_AT,
        )
        .from(USERS)
        .where(USERS.STATUS.eq(UserStatus.PENDING.mapUsing(UserStatusMapper)))
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
                createdAt = record.get(USERS.CREATED_AT)!!,
                updatedAt = record.get(USERS.UPDATED_AT)!!,
            )
        }
}

context(_: TransactionContext)
fun countPendingUsers(): Int = useTx { dslContext ->
    dslContext
        .selectCount()
        .from(USERS)
        .where(USERS.STATUS.eq(UserStatus.PENDING.mapUsing(UserStatusMapper)))
        .fetchOne(0, Int::class.java)
        ?: 0
}


context(_: TransactionContext)
fun updateUserStatus(
    userId: Int,
    status: UserStatus,
    updatedAt: Instant = Clock.System.now(),
): Boolean = useTx { dslContext ->
    val updated = dslContext
        .update(USERS)
        .set(USERS.STATUS, status.mapUsing(UserStatusMapper))
        .set(USERS.UPDATED_AT, updatedAt)
        .where(USERS.ID.eq(userId))
        .execute()

    updated > 0
}
