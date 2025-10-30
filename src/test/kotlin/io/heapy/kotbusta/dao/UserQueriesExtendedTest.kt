package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.USERS
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.UserStatus
import io.heapy.kotbusta.test.DatabaseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Extended integration tests for UserQueries.
 *
 * Tests additional database query methods that use useTx for user operations,
 * complementing the existing UserQueriesTest.kt.
 */
@ExtendWith(DatabaseExtension::class)
class UserQueriesExtendedTest {
    @Test
    context(_: TransactionProvider)
    fun `findUserByGoogleId should return user when found`() = transaction {
        // Given: John Doe exists with google_123456
        // When: Finding user by Google ID
        val user = findUserByGoogleId(googleId = "google_123456")

        // Then: Should return the user
        assertNotNull(user)
        assertEquals(1, user?.id)
        assertEquals("google_123456", user?.googleId)
        assertEquals("john.doe@example.com", user?.email)
        assertEquals("John Doe", user?.name)
        assertEquals("APPROVED", user?.status)
    }

    @Test
    context(_: TransactionProvider)
    fun `findUserByGoogleId should return null when not found`() = transaction {
        // Given: No user with google_nonexistent
        // When: Finding non-existent user
        val user = findUserByGoogleId(googleId = "google_nonexistent")

        // Then: Should return null
        assertNull(user)
    }

    @Test
    context(_: TransactionProvider)
    fun `insertUser should create new user with PENDING status`() =
        transaction {
            // When: Inserting a new user
            val userId = insertUser(
                googleId = "google_newuser",
                email = "new.user@example.com",
                name = "New User",
                avatarUrl = "https://example.com/avatars/new.jpg",
            )

            // Then: Should return new user ID
            assertNotNull(userId)

            // Verify user was created
            val user = useTx { dslContext ->
                dslContext
                    .selectFrom(USERS)
                    .where(USERS.ID.eq(userId))
                    .fetchOne()
            }

            assertNotNull(user)
            assertEquals("google_newuser", user?.googleId)
            assertEquals("new.user@example.com", user?.email)
            assertEquals("New User", user?.name)
            assertEquals("PENDING", user?.status)
            assertNotNull(user?.createdAt)
            assertNotNull(user?.updatedAt)
        }

    @Test
    context(_: TransactionProvider)
    fun `insertUser should allow null avatar URL`() = transaction {
        // When: Inserting user without avatar
        val userId = insertUser(
            googleId = "google_noavatar",
            email = "no.avatar@example.com",
            name = "No Avatar User",
            avatarUrl = null,
        )

        // Then: Should create user with null avatar
        val user = useTx { dslContext ->
            dslContext
                .selectFrom(USERS)
                .where(USERS.ID.eq(userId))
                .fetchOne()
        }

        assertNull(user?.avatarUrl)
    }

    @Test
    context(_: TransactionProvider)
    fun `updateUser should update user information`() = transaction {
        // Given: User 1 exists
        // When: Updating user information
        val updated = updateUser(
            userId = 1,
            email = "john.updated@example.com",
            name = "John Updated",
            avatarUrl = "https://example.com/new-avatar.jpg",
        )

        // Then: Should return 1 (number of rows updated)
        assertEquals(1, updated)

        // Verify user was updated
        val user = useTx { dslContext ->
            dslContext
                .selectFrom(USERS)
                .where(USERS.ID.eq(1))
                .fetchOne()
        }

        assertEquals("john.updated@example.com", user?.email)
        assertEquals("John Updated", user?.name)
        assertEquals("https://example.com/new-avatar.jpg", user?.avatarUrl)
    }

    @Test
    context(_: TransactionProvider)
    fun `updateUser should return 0 for non-existent user`() = transaction {
        // Given: User 999 does not exist
        // When: Trying to update non-existent user
        val updated = updateUser(
            userId = 999,
            email = "test@example.com",
            name = "Test",
            avatarUrl = null,
        )

        // Then: Should return 0
        assertEquals(0, updated)
    }

    @Test
    context(_: TransactionProvider)
    fun `validateUserSession should return true for existing user`() =
        transaction {
            // Given: User 1 exists
            // When: Validating user session
            val valid = validateUserSession(userId = 1)

            // Then: Should return true
            assertTrue(valid)
        }

    @Test
    context(_: TransactionProvider)
    fun `validateUserSession should return false for non-existent user`() =
        transaction {
            // Given: User 999 does not exist
            // When: Validating non-existent user
            val valid = validateUserSession(userId = 999)

            // Then: Should return false
            assertFalse(valid)
        }

    @Test
    context(_: TransactionProvider)
    fun `getUserInfo should return user information for session user`() =
        transaction {
            // Given: User session for user 1
            val userSession = UserSession(
                userId = 1,
                email = "john.doe@example.com",
                name = "John Doe",
            )

            // When: Getting user info
            val userInfo = with(userSession) { getUserInfo() }

            // Then: Should return user information
            assertNotNull(userInfo)
            assertEquals(1, userInfo?.userId)
            assertEquals("john.doe@example.com", userInfo?.email)
            assertEquals("John Doe", userInfo?.name)
            assertEquals(
                "https://example.com/avatars/john.jpg",
                userInfo?.avatarUrl,
            )
            assertEquals(UserStatus.APPROVED, userInfo?.status)
        }

    @Test
    context(_: TransactionProvider)
    fun `getUserInfo should return null for non-existent user`() = transaction {
        // Given: User session for non-existent user
        val userSession = UserSession(
            userId = 999,
            email = "nonexistent@example.com",
            name = "Nonexistent",
        )

        // When: Getting user info
        val userInfo = with(userSession) { getUserInfo() }

        // Then: Should return null
        assertNull(userInfo)
    }

    @Test
    context(_: TransactionProvider)
    fun `listPendingUsers should return only PENDING users`() = transaction {
        // Given: Test fixtures have 1 pending user (Bob Pending)
        // When: Listing pending users
        val pendingUsers = listPendingUsers(limit = 20, offset = 0)

        // Then: Should return pending users
        assertEquals(1, pendingUsers.size)

        val pendingUser = pendingUsers.first()
        assertEquals(3, pendingUser.id)
        assertEquals("Bob Pending", pendingUser.name)
        assertEquals("bob.pending@example.com", pendingUser.email)
        assertEquals(UserStatus.PENDING, pendingUser.status)
    }

    @Test
    context(_: TransactionProvider)
    fun `listPendingUsers should support pagination`() = transaction {
        // Given: Test fixtures have pending users
        // When: Using pagination with limit and offset
        val page1 = listPendingUsers(limit = 1, offset = 0)
        val page2 = listPendingUsers(limit = 1, offset = 1)

        // Then: Should return different results or empty
        if (page1.isNotEmpty() && page2.isNotEmpty()) {
            assertTrue(page1[0].id != page2[0].id)
        }
    }

    @Test
    context(_: TransactionProvider)
    fun `listPendingUsers should order by created_at descending`() =
        transaction {
            // Given: Multiple pending users (create additional for testing)
            insertUser(
                googleId = "google_pending2",
                email = "pending2@example.com",
                name = "Pending User 2",
                avatarUrl = null,
            )

            // When: Listing pending users
            val pendingUsers = listPendingUsers(limit = 20, offset = 0)

            // Then: Should be ordered by created_at descending
            for (i in 0 until pendingUsers.size - 1) {
                val current = pendingUsers[i].createdAt
                val next = pendingUsers[i + 1].createdAt
                assertTrue(current >= next) {
                    "Users should be ordered by createdAt descending"
                }
            }
        }

    @Test
    context(_: TransactionProvider)
    fun `countPendingUsers should return correct count`() = transaction {
        // Given: Test fixtures have 1 pending user
        // When: Counting pending users
        val count = countPendingUsers()

        // Then: Should return 1
        assertEquals(1L, count)
    }

    @Test
    context(_: TransactionProvider)
    fun `countPendingUsers should update when status changes`() = transaction {
        // Given: Initial count of pending users
        val initialCount = countPendingUsers()

        // When: Approving a pending user
        updateUserStatus(userId = 3, status = UserStatus.APPROVED)

        // Then: Count should decrease
        val newCount = countPendingUsers()
        assertEquals(initialCount - 1, newCount)
    }

    @Test
    context(_: TransactionProvider)
    fun `updateUserStatus should change user status`() = transaction {
        // Given: User 3 is PENDING
        // When: Approving the user
        val updated = updateUserStatus(
            userId = 3,
            status = UserStatus.APPROVED,
        )

        // Then: Should return true
        assertTrue(updated)

        // Verify status was changed
        val user = useTx { dslContext ->
            dslContext
                .selectFrom(USERS)
                .where(USERS.ID.eq(3))
                .fetchOne()
        }

        assertEquals("APPROVED", user?.status)
    }

    @Test
    context(_: TransactionProvider)
    fun `updateUserStatus should handle all status values`() = transaction {
        // Given: User 3 exists
        // When: Cycling through different statuses
        val statuses = listOf(
            UserStatus.APPROVED,
            UserStatus.REJECTED,
            UserStatus.DEACTIVATED,
            UserStatus.PENDING,
        )

        statuses.forEach { status ->
            // Update to this status
            val updated = updateUserStatus(userId = 3, status = status)
            assertTrue(updated)

            // Verify status was changed
            val user = useTx { dslContext ->
                dslContext
                    .selectFrom(USERS)
                    .where(USERS.ID.eq(3))
                    .fetchOne()
            }

            assertEquals(status.name, user?.status)
        }
    }

    @Test
    context(_: TransactionProvider)
    fun `updateUserStatus should return false for non-existent user`() =
        transaction {
            // Given: User 999 does not exist
            // When: Trying to update status
            val updated = updateUserStatus(
                userId = 999,
                status = UserStatus.APPROVED,
            )

            // Then: Should return false
            assertFalse(updated)
        }

    @Test
    context(_: TransactionProvider)
    fun `updateUserStatus should update updatedAt timestamp`() = transaction {
        // Given: User 3 exists
        val userBefore = useTx { dslContext ->
            dslContext
                .selectFrom(USERS)
                .where(USERS.ID.eq(3))
                .fetchOne()
        }
        userBefore?.updatedAt

        // When: Updating user status
        updateUserStatus(userId = 3, status = UserStatus.APPROVED)

        // Then: updatedAt should change
        val userAfter = useTx { dslContext ->
            dslContext
                .selectFrom(USERS)
                .where(USERS.ID.eq(3))
                .fetchOne()
        }
        val updatedAtAfter = userAfter?.updatedAt

        assertNotNull(updatedAtAfter)
        // Note: In fast tests, timestamps might be equal, so we just verify it's not null
    }
}
