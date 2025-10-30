package io.heapy.kotbusta.service

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.jooq.tables.references.DOWNLOADS
import io.heapy.kotbusta.jooq.tables.references.USER_COMMENTS
import io.heapy.kotbusta.jooq.tables.references.USER_NOTES
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.test.DatabaseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Integration tests for UserService.
 *
 * Tests all database query methods that use useTx for user-related operations.
 */
@ExtendWith(DatabaseExtension::class)
class UserServiceTest {
    private val userService = UserService()

    // Test user sessions from fixtures
    private val user1Session = UserSession(userId = 1, googleId = "google_123456")
    private val user2Session = UserSession(userId = 2, googleId = "google_789012")

    // Comment Tests

    @Test
    context(_: TransactionProvider)
    fun `addComment should insert and return new comment`() = transaction {
        // When: Adding a comment as user 1
        val comment = with(user1Session) {
            userService.addComment(
                bookId = 5,
                comment = "This is a test comment",
            )
        }

        // Then: Comment should be created
        assertNotNull(comment)
        assertEquals(1, comment?.userId)
        assertEquals(5, comment?.bookId)
        assertEquals("This is a test comment", comment?.comment)
        assertEquals("John Doe", comment?.userName)
        assertNotNull(comment?.createdAt)
        assertNotNull(comment?.updatedAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `updateComment should update existing comment`() = transaction {
        // Given: User 1 has comment ID 1 in fixtures
        // When: Updating the comment
        val updated = with(user1Session) {
            userService.updateComment(
                commentId = 1,
                comment = "Updated comment text",
            )
        }

        // Then: Should return true
        assertTrue(updated)

        // Verify comment was updated
        val savedComment = dslContext
            .selectFrom(USER_COMMENTS)
            .where(USER_COMMENTS.ID.eq(1))
            .fetchOne()

        assertEquals("Updated comment text", savedComment?.comment)
    }

    @Test
    context(_: TransactionProvider)
    fun `updateComment should return false for comment from different user`() = transaction {
        // Given: Comment ID 1 belongs to user 1
        // When: User 2 tries to update it
        val updated = with(user2Session) {
            userService.updateComment(
                commentId = 1,
                comment = "Hacker's comment",
            )
        }

        // Then: Should return false
        assertFalse(updated)

        // Verify comment was not updated
        val savedComment = dslContext
            .selectFrom(USER_COMMENTS)
            .where(USER_COMMENTS.ID.eq(1))
            .fetchOne()

        assertEquals("Amazing start to the series!", savedComment?.comment)
    }

    @Test
    context(_: TransactionProvider)
    fun `deleteComment should delete user's comment`() = transaction {
        // Given: User 1 has comment ID 1
        // When: Deleting the comment
        val deleted = with(user1Session) {
            userService.deleteComment(commentId = 1)
        }

        // Then: Should return true
        assertTrue(deleted)

        // Verify comment was deleted
        val savedComment = dslContext
            .selectFrom(USER_COMMENTS)
            .where(USER_COMMENTS.ID.eq(1))
            .fetchOne()

        assertNull(savedComment)
    }

    @Test
    context(_: TransactionProvider)
    fun `deleteComment should return false for comment from different user`() = transaction {
        // Given: Comment ID 1 belongs to user 1
        // When: User 2 tries to delete it
        val deleted = with(user2Session) {
            userService.deleteComment(commentId = 1)
        }

        // Then: Should return false
        assertFalse(deleted)

        // Verify comment still exists
        val savedComment = dslContext
            .selectFrom(USER_COMMENTS)
            .where(USER_COMMENTS.ID.eq(1))
            .fetchOne()

        assertNotNull(savedComment)
    }

    @Test
    context(_: TransactionProvider)
    fun `getBookComments should return all comments for a book`() = transaction {
        // Given: Book ID 1 has 2 comments in fixtures
        // When: Getting comments for book 1
        val comments = userService.getBookComments(
            bookId = 1,
            limit = 20,
            offset = 0,
        )

        // Then: Should return 2 comments with complete data
        assertEquals(2, comments.size)

        val firstComment = comments[0]
        assertNotNull(firstComment.id)
        assertNotNull(firstComment.userId)
        assertNotNull(firstComment.userName)
        assertNotNull(firstComment.bookId)
        assertNotNull(firstComment.bookTitle)
        assertNotNull(firstComment.comment)
        assertNotNull(firstComment.createdAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `getBookComments should support pagination`() = transaction {
        // Given: Book ID 1 has 2 comments
        // When: Getting first comment only
        val comments = userService.getBookComments(
            bookId = 1,
            limit = 1,
            offset = 0,
        )

        // Then: Should return only 1 comment
        assertEquals(1, comments.size)
    }

    // Note Tests

    @Test
    context(_: TransactionProvider)
    fun `addOrUpdateNote should insert new note`() = transaction {
        // When: Adding a note as user 1 for book 5 (no existing note)
        val note = with(user1Session) {
            userService.addOrUpdateNote(
                bookId = 5,
                note = "This is a new note",
                isPrivate = true,
            )
        }

        // Then: Note should be created
        assertNotNull(note)
        assertEquals(5, note?.bookId)
        assertEquals("This is a new note", note?.note)
        assertTrue(note?.isPrivate == true)
    }

    @Test
    context(_: TransactionProvider)
    fun `addOrUpdateNote should update existing note`() = transaction {
        // Given: User 1 has note ID 1 for book 1
        // When: Updating the note
        val note = with(user1Session) {
            userService.addOrUpdateNote(
                bookId = 1,
                note = "Updated note text",
                isPrivate = false,
            )
        }

        // Then: Note should be updated
        assertNotNull(note)
        assertEquals("Updated note text", note?.note)
        assertFalse(note?.isPrivate == true)
    }

    @Test
    context(_: TransactionProvider)
    fun `getUserNote should return user's note for book`() = transaction {
        // Given: User 1 has note for book 1
        // When: Getting the note
        val note = userService.getUserNote(userId = 1, bookId = 1)

        // Then: Should return the note
        assertNotNull(note)
        assertEquals(1, note?.id)
        assertEquals(1, note?.bookId)
        assertEquals("Remember to recommend this to my nephew.", note?.note)
        assertTrue(note?.isPrivate == true)
    }

    @Test
    context(_: TransactionProvider)
    fun `getUserNote should return null when no note exists`() = transaction {
        // Given: User 1 has no note for book 10
        // When: Getting the note
        val note = userService.getUserNote(userId = 1, bookId = 10)

        // Then: Should return null
        assertNull(note)
    }

    @Test
    context(_: TransactionProvider)
    fun `deleteNote should delete user's note`() = transaction {
        // Given: User 1 has note for book 1
        // When: Deleting the note
        val deleted = with(user1Session) {
            userService.deleteNote(bookId = 1)
        }

        // Then: Should return true
        assertTrue(deleted)

        // Verify note was deleted
        val savedNote = dslContext
            .selectFrom(USER_NOTES)
            .where(USER_NOTES.USER_ID.eq(1))
            .and(USER_NOTES.BOOK_ID.eq(1))
            .fetchOne()

        assertNull(savedNote)
    }

    @Test
    context(_: TransactionProvider)
    fun `deleteNote should return false when no note exists`() = transaction {
        // Given: User 1 has no note for book 10
        // When: Trying to delete non-existent note
        val deleted = with(user1Session) {
            userService.deleteNote(bookId = 10)
        }

        // Then: Should return false
        assertFalse(deleted)
    }

    // Download Tests

    @Test
    context(_: TransactionProvider)
    fun `recordDownload should insert download record`() = transaction {
        // When: Recording a download as user 1
        val recorded = with(user1Session) {
            userService.recordDownload(
                bookId = 7,
                format = "PDF",
            )
        }

        // Then: Should return true
        assertTrue(recorded)

        // Verify download was recorded
        val download = dslContext
            .selectFrom(DOWNLOADS)
            .where(DOWNLOADS.USER_ID.eq(1))
            .and(DOWNLOADS.BOOK_ID.eq(7))
            .and(DOWNLOADS.FORMAT.eq("PDF"))
            .orderBy(DOWNLOADS.CREATED_AT.desc())
            .limit(1)
            .fetchOne()

        assertNotNull(download)
        assertEquals(1, download?.userId)
        assertEquals(7, download?.bookId)
        assertEquals("PDF", download?.format)
    }

    @Test
    context(_: TransactionProvider)
    fun `recordDownload should allow multiple downloads of same book`() = transaction {
        // Given: User 1 has already downloaded book 1
        // When: Recording another download of same book
        val recorded = with(user1Session) {
            userService.recordDownload(
                bookId = 1,
                format = "MOBI",
            )
        }

        // Then: Should return true (allows duplicates)
        assertTrue(recorded)

        // Verify both downloads exist
        val downloads = dslContext
            .selectCount()
            .from(DOWNLOADS)
            .where(DOWNLOADS.USER_ID.eq(1))
            .and(DOWNLOADS.BOOK_ID.eq(1))
            .fetchOne(0, Int::class.java)

        assertTrue(downloads!! >= 2)
    }

    // Activity Tests

    @Test
    context(_: TransactionProvider)
    fun `getRecentActivity should return recent comments and downloads`() = transaction {
        // Given: Test fixtures have comments and downloads
        // When: Getting recent activity
        val activity = userService.getRecentActivity(limit = 20)

        // Then: Should return activity data
        assertNotNull(activity.comments)
        assertNotNull(activity.downloads)

        // Verify comments have required data
        if (activity.comments.isNotEmpty()) {
            val comment = activity.comments.first()
            assertNotNull(comment.id)
            assertNotNull(comment.userName)
            assertNotNull(comment.bookTitle)
            assertNotNull(comment.comment)
        }

        // Verify downloads have required data
        if (activity.downloads.isNotEmpty()) {
            val download = activity.downloads.first()
            assertNotNull(download.id)
            assertNotNull(download.userName)
            assertNotNull(download.bookTitle)
            assertNotNull(download.format)
        }
    }

    @Test
    context(_: TransactionProvider)
    fun `getRecentActivity should respect limit parameter`() = transaction {
        // Given: Test fixtures have multiple comments and downloads
        // When: Getting recent activity with limit of 2
        val activity = userService.getRecentActivity(limit = 2)

        // Then: Should return at most 2 items per category
        assertTrue(activity.comments.size <= 2)
        assertTrue(activity.downloads.size <= 2)
    }

    @Test
    context(_: TransactionProvider)
    fun `getRecentActivity should order by created_at descending`() = transaction {
        // Given: Test fixtures have activity data
        // When: Getting recent activity
        val activity = userService.getRecentActivity(limit = 20)

        // Then: Comments should be ordered by creation time (most recent first)
        for (i in 0 until activity.comments.size - 1) {
            val current = activity.comments[i].createdAt
            val next = activity.comments[i + 1].createdAt
            assertTrue(current >= next) {
                "Comments should be ordered by createdAt descending"
            }
        }

        // Downloads should be ordered by creation time (most recent first)
        for (i in 0 until activity.downloads.size - 1) {
            val current = activity.downloads[i].createdAt
            val next = activity.downloads[i + 1].createdAt
            assertTrue(current >= next) {
                "Downloads should be ordered by createdAt descending"
            }
        }
    }
}
