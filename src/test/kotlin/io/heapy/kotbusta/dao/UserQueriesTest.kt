package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.USERS
import io.heapy.kotbusta.test.DatabaseExtension
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Test class demonstrating database testing with fixtures.
 *
 * This test demonstrates how to:
 * 1. Use an in-memory SQLite database for testing
 * 2. Run schema migrations to create tables
 * 3. Load test fixtures from an SQL file
 * 4. Query the database using jOOQ to verify data
 *
 * The database connection is kept open and shared across all tests.
 */
@ExtendWith(DatabaseExtension::class)
class UserQueriesTest {
    @Test
    context(_: TransactionProvider)
    fun `test fixtures should load successfully`() = transaction {
        // Check total number of users
        val userCount = useTx { dslContext ->
            dslContext
                .selectCount()
                .from(USERS)
                .fetchOne(0, Int::class.java)
        }

        assertEquals(5, userCount, "Should have 5 users from fixtures")
    }

    @Test
    context(_: TransactionProvider)
    fun `should find pending users from fixtures`() = transaction {
        // When: Querying for pending users
        val pendingUsers = useTx { dslContext ->
            dslContext
                .selectFrom(USERS)
                .where(USERS.STATUS.eq("PENDING"))
                .fetch()
        }
        // Then: Should return exactly 1 pending user
        assertEquals(1, pendingUsers.size, "Should have exactly 1 pending user")

        val pendingUser = pendingUsers.first()
        assertEquals("Bob Pending", pendingUser.name)
        assertEquals("bob.pending@example.com", pendingUser.email)
        assertEquals("google_345678", pendingUser.googleId)
    }

    @Test
    context(_: TransactionProvider)
    fun `should find user by Google ID`() = transaction {
        // Given: Test fixtures with John Doe (google_123456)
        val googleId = "google_123456"

        // When: Finding user by Google ID
        val user = useTx { dslContext ->
            dslContext
                .selectFrom(USERS)
                .where(USERS.GOOGLE_ID.eq(googleId))
                .fetchOne()
        }
        // Then: Should return the user
        assertNotNull(user, "User should be found")
        assertEquals("john.doe@example.com", user?.email)
        assertEquals("John Doe", user?.name)
        assertEquals(googleId, user?.googleId)
    }

    @Test
    context(_: TransactionProvider)
    fun `should have correct user statuses in fixtures`() = transaction {
        // Verify the distribution of user statuses in fixtures
        val statusCounts = useTx { dslContext ->
            dslContext
                .select(USERS.STATUS, DSL.count())
                .from(USERS)
                .groupBy(USERS.STATUS)
                .fetch()
                .associate { it.value1() to it.value2() }
        }
        assertEquals(2, statusCounts["APPROVED"], "Should have 2 approved users")
        assertEquals(1, statusCounts["PENDING"], "Should have 1 pending user")
        assertEquals(1, statusCounts["REJECTED"], "Should have 1 rejected user")
        assertEquals(1, statusCounts["DEACTIVATED"], "Should have 1 deactivated user")
    }
}
