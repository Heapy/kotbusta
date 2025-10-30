package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.jooq.tables.references.AUTHORS
import io.heapy.kotbusta.model.Author
import io.heapy.kotbusta.test.DatabaseExtension
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Integration tests for AuthorsQueries.
 *
 * Tests database query methods that use useTx for author-related operations.
 */
@ExtendWith(DatabaseExtension::class)
class AuthorsQueriesTest {
    @Test
    context(_: TransactionProvider)
    fun `insertOrGetAuthor should return existing author ID when author exists`(
        dslContext: DSLContext,
    ) = transaction {
        // Given: J.K. Rowling exists in test fixtures with ID 1
        val author = Author(
            id = 0,
            firstName = "J.K.",
            lastName = "Rowling",
            fullName = "J.K. Rowling",
        )

        // When: Trying to insert existing author
        val authorId = insertOrGetAuthor(author)

        // Then: Should return existing ID
        assertEquals(1, authorId)

        // Verify no duplicate was created
        val count = dslContext
            .selectCount()
            .from(AUTHORS)
            .where(AUTHORS.FULL_NAME.eq("J.K. Rowling"))
            .fetchOne(0, Int::class.java)
        assertEquals(1, count)
    }

    @Test
    context(_: TransactionProvider)
    fun `insertOrGetAuthor should insert new author and return ID`(
        dslContext: DSLContext,
    ) = transaction {
        // Given: New author not in fixtures
        val author = Author(
            id = 0,
            firstName = "Patrick",
            lastName = "Rothfuss",
            fullName = "Patrick Rothfuss",
        )

        // When: Inserting new author
        val authorId = insertOrGetAuthor(author)

        // Then: Should return new ID
        assertNotNull(authorId)

        // Verify author was inserted
        val savedAuthor = dslContext
            .selectFrom(AUTHORS)
            .where(AUTHORS.ID.eq(authorId))
            .fetchOne()

        assertNotNull(savedAuthor)
        assertEquals("Patrick", savedAuthor?.firstName)
        assertEquals("Rothfuss", savedAuthor?.lastName)
        assertEquals("Patrick Rothfuss", savedAuthor?.fullName)
    }

    @Test
    context(_: TransactionProvider)
    fun `insertOrGetAuthor should handle author with null first name`() = transaction {
        // Given: Test fixtures has Leo Tolstoy with null first name
        val author = Author(
            id = 0,
            firstName = null,
            lastName = "Tolstoy",
            fullName = "Leo Tolstoy",
        )

        // When: Getting existing author with null first name
        val authorId = insertOrGetAuthor(author)

        // Then: Should return existing ID (6)
        assertEquals(6, authorId)
    }

    @Test
    context(_: TransactionProvider)
    fun `insertOrGetAuthor should be idempotent`(
        dslContext: DSLContext,
    ) = transaction {
        // Given: A new author
        val author = Author(
            id = 0,
            firstName = "Terry",
            lastName = "Pratchett",
            fullName = "Terry Pratchett",
        )

        // When: Inserting the same author twice
        val firstId = insertOrGetAuthor(author)
        val secondId = insertOrGetAuthor(author)

        // Then: Should return the same ID both times
        assertEquals(firstId, secondId)

        // Verify only one record exists
        val count = dslContext
            .selectCount()
            .from(AUTHORS)
            .where(AUTHORS.FULL_NAME.eq("Terry Pratchett"))
            .fetchOne(0, Int::class.java)
        assertEquals(1, count)
    }

    @Test
    context(_: TransactionProvider)
    fun `insertOrGetAuthor should handle multiple authors with same last name`(
        dslContext: DSLContext,
    ) = transaction {
        // Given: Two different authors with same last name
        val author1 = Author(
            id = 0,
            firstName = "Stephen",
            lastName = "King",
            fullName = "Stephen King",
        )
        val author2 = Author(
            id = 0,
            firstName = "Owen",
            lastName = "King",
            fullName = "Owen King",
        )

        // When: Inserting both authors
        val id1 = insertOrGetAuthor(author1)
        val id2 = insertOrGetAuthor(author2)

        // Then: Should have different IDs
        assertEquals(4, id1) // Stephen King exists in fixtures with ID 4
        assertNotNull(id2)
        // Verify Owen King was inserted as new author
        val owenKing = dslContext
            .selectFrom(AUTHORS)
            .where(AUTHORS.ID.eq(id2))
            .fetchOne()
        assertEquals("Owen King", owenKing?.fullName)
    }
}
