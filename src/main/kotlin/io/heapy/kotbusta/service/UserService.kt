package io.heapy.kotbusta.service

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.dslContext
import io.heapy.kotbusta.jooq.tables.references.BOOKS
import io.heapy.kotbusta.jooq.tables.references.DOWNLOADS
import io.heapy.kotbusta.jooq.tables.references.USERS
import io.heapy.kotbusta.jooq.tables.references.USER_COMMENTS
import io.heapy.kotbusta.jooq.tables.references.USER_NOTES
import io.heapy.kotbusta.model.Download
import io.heapy.kotbusta.model.RecentActivity
import io.heapy.kotbusta.model.UserComment
import io.heapy.kotbusta.model.UserNote
import java.time.OffsetDateTime

class UserService {
    context(_: TransactionContext, userSession: UserSession)
    fun addComment(
        bookId: Long,
        comment: String,
    ): UserComment? = dslContext { dslContext ->
        val insertedId = dslContext
            .insertInto(USER_COMMENTS)
            .set(USER_COMMENTS.USER_ID, userSession.userId)
            .set(USER_COMMENTS.BOOK_ID, bookId)
            .set(USER_COMMENTS.COMMENT, comment)
            .set(USER_COMMENTS.CREATED_AT, OffsetDateTime.now())
            .set(USER_COMMENTS.UPDATED_AT, OffsetDateTime.now())
            .returningResult(USER_COMMENTS.ID)
            .fetchOne()
            ?.value1()

        insertedId?.let {
            getLatestComment(bookId)
        }
    }

    context(_: TransactionContext, userSession: UserSession)
    fun updateComment(
        commentId: Long,
        comment: String,
    ): Boolean = dslContext { dslContext ->
        val updated = dslContext
            .update(USER_COMMENTS)
            .set(USER_COMMENTS.COMMENT, comment)
            .set(USER_COMMENTS.UPDATED_AT, OffsetDateTime.now())
            .where(USER_COMMENTS.ID.eq(commentId))
            .and(USER_COMMENTS.USER_ID.eq(userSession.userId))
            .execute()

        updated > 0
    }

    context(_: TransactionContext, userSession: UserSession)
    fun deleteComment(commentId: Long): Boolean = dslContext { dslContext ->
        val deleted = dslContext
            .deleteFrom(USER_COMMENTS)
            .where(USER_COMMENTS.ID.eq(commentId))
            .and(USER_COMMENTS.USER_ID.eq(userSession.userId))
            .execute()

        deleted > 0
    }

    context(_: TransactionContext)
    fun getBookComments(
        bookId: Long,
        limit: Int = 20,
        offset: Int = 0,
    ): List<UserComment> = dslContext { dslContext ->
        dslContext
            .select(
                USER_COMMENTS.ID,
                USER_COMMENTS.USER_ID,
                USER_COMMENTS.BOOK_ID,
                USER_COMMENTS.COMMENT,
                USER_COMMENTS.CREATED_AT,
                USER_COMMENTS.UPDATED_AT,
                USERS.NAME.`as`("user_name"),
                USERS.AVATAR_URL.`as`("user_avatar_url"),
                BOOKS.TITLE.`as`("book_title")
            )
            .from(USER_COMMENTS)
            .innerJoin(USERS).on(USER_COMMENTS.USER_ID.eq(USERS.ID))
            .innerJoin(BOOKS).on(USER_COMMENTS.BOOK_ID.eq(BOOKS.ID))
            .where(USER_COMMENTS.BOOK_ID.eq(bookId))
            .orderBy(USER_COMMENTS.CREATED_AT.desc())
            .limit(limit)
            .offset(offset)
            .fetch { record ->
                UserComment(
                    id = record.get(USER_COMMENTS.ID)!!,
                    userId = record.get(USER_COMMENTS.USER_ID)!!,
                    userName = record.get("user_name", String::class.java)!!,
                    userAvatarUrl = record.get("user_avatar_url", String::class.java),
                    bookId = record.get(USER_COMMENTS.BOOK_ID)!!,
                    bookTitle = record.get("book_title", String::class.java)!!,
                    comment = record.get(USER_COMMENTS.COMMENT)!!,
                    createdAt = record.get(USER_COMMENTS.CREATED_AT)!!.toEpochSecond(),
                    updatedAt = record.get(USER_COMMENTS.UPDATED_AT)!!.toEpochSecond()
                )
            }
    }

    context(_: TransactionContext, userSession: UserSession)
    fun addOrUpdateNote(
        bookId: Long,
        note: String,
        isPrivate: Boolean = true,
    ): UserNote? = dslContext { dslContext ->
        // Check if note already exists
        val existingNote = dslContext
            .select(USER_NOTES.ID)
            .from(USER_NOTES)
            .where(USER_NOTES.USER_ID.eq(userSession.userId))
            .and(USER_NOTES.BOOK_ID.eq(bookId))
            .fetchOne()

        if (existingNote != null) {
            // Update existing note
            val updated = dslContext
                .update(USER_NOTES)
                .set(USER_NOTES.NOTE, note)
                .set(USER_NOTES.IS_PRIVATE, isPrivate)
                .set(USER_NOTES.UPDATED_AT, OffsetDateTime.now())
                .where(USER_NOTES.ID.eq(existingNote.get(USER_NOTES.ID)))
                .execute()

            if (updated > 0) {
                getUserNote(userSession.userId, bookId)
            } else null
        } else {
            // Insert new note
            val insertedId = dslContext
                .insertInto(USER_NOTES)
                .set(USER_NOTES.USER_ID, userSession.userId)
                .set(USER_NOTES.BOOK_ID, bookId)
                .set(USER_NOTES.NOTE, note)
                .set(USER_NOTES.IS_PRIVATE, isPrivate)
                .set(USER_NOTES.CREATED_AT, OffsetDateTime.now())
                .set(USER_NOTES.UPDATED_AT, OffsetDateTime.now())
                .returningResult(USER_NOTES.ID)
                .fetchOne()
                ?.value1()

            if (insertedId != null) {
                getUserNote(userSession.userId, bookId)
            } else null
        }
    }

    context(_: TransactionContext, userSession: UserSession)
    fun deleteNote(bookId: Long): Boolean = dslContext { dslContext ->
        val deleted = dslContext
            .deleteFrom(USER_NOTES)
            .where(USER_NOTES.USER_ID.eq(userSession.userId))
            .and(USER_NOTES.BOOK_ID.eq(bookId))
            .execute()

        deleted > 0
    }

    context(_: TransactionContext)
    fun getUserNote(userId: Long, bookId: Long): UserNote? = dslContext { dslContext ->
        dslContext
            .select(
                USER_NOTES.ID,
                USER_NOTES.BOOK_ID,
                USER_NOTES.NOTE,
                USER_NOTES.IS_PRIVATE,
                USER_NOTES.CREATED_AT,
                USER_NOTES.UPDATED_AT
            )
            .from(USER_NOTES)
            .where(USER_NOTES.USER_ID.eq(userId))
            .and(USER_NOTES.BOOK_ID.eq(bookId))
            .fetchOne { record ->
                UserNote(
                    id = record.get(USER_NOTES.ID)!!,
                    bookId = record.get(USER_NOTES.BOOK_ID)!!,
                    note = record.get(USER_NOTES.NOTE)!!,
                    isPrivate = record.get(USER_NOTES.IS_PRIVATE)!!,
                    createdAt = record.get(USER_NOTES.CREATED_AT)!!.toEpochSecond(),
                    updatedAt = record.get(USER_NOTES.UPDATED_AT)!!.toEpochSecond()
                )
            }
    }

    context(_: TransactionContext, userSession: UserSession)
    fun recordDownload(
        bookId: Long,
        format: String,
    ): Boolean = dslContext { dslContext ->
        val inserted = dslContext
            .insertInto(DOWNLOADS)
            .set(DOWNLOADS.USER_ID, userSession.userId)
            .set(DOWNLOADS.BOOK_ID, bookId)
            .set(DOWNLOADS.FORMAT, format)
            .set(DOWNLOADS.CREATED_AT, OffsetDateTime.now())
            .execute()

        inserted > 0
    }

    context(_: TransactionContext)
    fun getRecentActivity(limit: Int = 20): RecentActivity = dslContext { dslContext ->
        // Get recent comments
        val comments = dslContext
            .select(
                USER_COMMENTS.ID,
                USER_COMMENTS.USER_ID,
                USER_COMMENTS.BOOK_ID,
                USER_COMMENTS.COMMENT,
                USER_COMMENTS.CREATED_AT,
                USER_COMMENTS.UPDATED_AT,
                USERS.NAME.`as`("user_name"),
                USERS.AVATAR_URL.`as`("user_avatar_url"),
                BOOKS.TITLE.`as`("book_title")
            )
            .from(USER_COMMENTS)
            .innerJoin(USERS).on(USER_COMMENTS.USER_ID.eq(USERS.ID))
            .innerJoin(BOOKS).on(USER_COMMENTS.BOOK_ID.eq(BOOKS.ID))
            .orderBy(USER_COMMENTS.CREATED_AT.desc())
            .limit(limit)
            .fetch { record ->
                UserComment(
                    id = record.get(USER_COMMENTS.ID)!!,
                    userId = record.get(USER_COMMENTS.USER_ID)!!,
                    userName = record.get("user_name", String::class.java)!!,
                    userAvatarUrl = record.get("user_avatar_url", String::class.java),
                    bookId = record.get(USER_COMMENTS.BOOK_ID)!!,
                    bookTitle = record.get("book_title", String::class.java)!!,
                    comment = record.get(USER_COMMENTS.COMMENT)!!,
                    createdAt = record.get(USER_COMMENTS.CREATED_AT)!!.toEpochSecond(),
                    updatedAt = record.get(USER_COMMENTS.UPDATED_AT)!!.toEpochSecond()
                )
            }

        // Get recent downloads
        val downloads = dslContext
            .select(
                DOWNLOADS.ID,
                DOWNLOADS.USER_ID,
                DOWNLOADS.BOOK_ID,
                DOWNLOADS.FORMAT,
                DOWNLOADS.CREATED_AT,
                USERS.NAME.`as`("user_name"),
                BOOKS.TITLE.`as`("book_title")
            )
            .from(DOWNLOADS)
            .innerJoin(USERS).on(DOWNLOADS.USER_ID.eq(USERS.ID))
            .innerJoin(BOOKS).on(DOWNLOADS.BOOK_ID.eq(BOOKS.ID))
            .orderBy(DOWNLOADS.CREATED_AT.desc())
            .limit(limit)
            .fetch { record ->
                Download(
                    id = record.get(DOWNLOADS.ID)!!,
                    userId = record.get(DOWNLOADS.USER_ID)!!,
                    userName = record.get("user_name", String::class.java)!!,
                    bookId = record.get(DOWNLOADS.BOOK_ID)!!,
                    bookTitle = record.get("book_title", String::class.java)!!,
                    format = record.get(DOWNLOADS.FORMAT)!!,
                    createdAt = record.get(DOWNLOADS.CREATED_AT)!!.toEpochSecond()
                )
            }

        RecentActivity(comments, downloads)
    }

    context(_: TransactionContext, userSession: UserSession)
    private fun getLatestComment(
        bookId: Long,
    ): UserComment? = dslContext { dslContext ->
        dslContext
            .select(
                USER_COMMENTS.ID,
                USER_COMMENTS.USER_ID,
                USER_COMMENTS.BOOK_ID,
                USER_COMMENTS.COMMENT,
                USER_COMMENTS.CREATED_AT,
                USER_COMMENTS.UPDATED_AT,
                USERS.NAME.`as`("user_name"),
                USERS.AVATAR_URL.`as`("user_avatar_url"),
                BOOKS.TITLE.`as`("book_title")
            )
            .from(USER_COMMENTS)
            .innerJoin(USERS).on(USER_COMMENTS.USER_ID.eq(USERS.ID))
            .innerJoin(BOOKS).on(USER_COMMENTS.BOOK_ID.eq(BOOKS.ID))
            .where(USER_COMMENTS.USER_ID.eq(userSession.userId))
            .and(USER_COMMENTS.BOOK_ID.eq(bookId))
            .orderBy(USER_COMMENTS.CREATED_AT.desc())
            .limit(1)
            .fetchOne { record ->
                UserComment(
                    id = record.get(USER_COMMENTS.ID)!!,
                    userId = record.get(USER_COMMENTS.USER_ID)!!,
                    userName = record.get("user_name", String::class.java)!!,
                    userAvatarUrl = record.get("user_avatar_url", String::class.java),
                    bookId = record.get(USER_COMMENTS.BOOK_ID)!!,
                    bookTitle = record.get("book_title", String::class.java)!!,
                    comment = record.get(USER_COMMENTS.COMMENT)!!,
                    createdAt = record.get(USER_COMMENTS.CREATED_AT)!!.toEpochSecond(),
                    updatedAt = record.get(USER_COMMENTS.UPDATED_AT)!!.toEpochSecond()
                )
            }
    }
}
