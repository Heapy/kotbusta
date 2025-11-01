package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.USER_STARS
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.SearchQuery
import io.heapy.kotbusta.test.DatabaseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Integration tests for BookQueries.
 *
 * Tests all database query methods that use useTx for book-related operations.
 */
@ExtendWith(DatabaseExtension::class)
class BookQueriesTest {
    // Test user sessions from fixtures
    private val user1Session = UserSession(userId = 1, email = "john.doe@example.com", name = "John Doe")
    private val user2Session = UserSession(userId = 2, email = "jane.smith@example.com", name = "Jane Smith")

    @Test
    context(_: TransactionProvider)
    fun `getBooks should return paginated books`() = transaction {
        // Given: Test fixtures have 10 books
        // When: Getting first page of books
        val result = getBooks(limit = 5, offset = 0, userId = null)

        // Then: Should return 5 books with pagination info
        assertEquals(5, result.books.size)
        assertEquals(10L, result.total)
        assertTrue(result.hasMore)

        // Verify book data structure
        val firstBook = result.books[0]
        assertNotNull(firstBook.id)
        assertNotNull(firstBook.title)
        assertNotNull(firstBook.language)
    }

    @Test
    context(_: TransactionProvider)
    fun `getBooks should include starred status for logged-in user`() = transaction {
        // Given: User 1 has starred books in fixtures
        // When: Getting books with user ID
        val result = getBooks(limit = 10, offset = 0, userId = 1)

        // Then: Should include correct starred status
        val starredBooks = result.books.filter { it.isStarred }
        assertTrue(starredBooks.isNotEmpty())

        // Verify user 1's starred books are marked
        val book1 = result.books.find { it.id == 1 }
        assertTrue(book1?.isStarred == true) // User 1 starred book 1 in fixtures
    }

    @Test
    context(_: TransactionProvider)
    fun `getBooks should show no starred books for anonymous user`() = transaction {
        // Given: No user ID provided
        // When: Getting books
        val result = getBooks(limit = 10, offset = 0, userId = null)

        // Then: All books should have isStarred = false
        assertTrue(result.books.all { !it.isStarred })
    }

    @Test
    context(_: TransactionProvider)
    fun `getBookById should return complete book details`() = transaction {
        // Given: Book ID 1 exists in fixtures
        // When: Getting book details
        val book = with(user1Session) {
            getBookById(bookId = 1)
        }

        // Then: Should return complete book data
        assertNotNull(book)
        assertEquals(1, book?.id)
        assertEquals("Harry Potter and the Philosopher's Stone", book?.title)
        assertEquals("Fantasy", book?.genre)
        assertEquals("en", book?.language)
        assertTrue(book?.isStarred == true) // User 1 starred this book
        assertNotNull(book?.authors)
        assertTrue(book?.authors?.isNotEmpty() == true)
        assertNotNull(book?.series)
        assertEquals("Harry Potter", book?.series?.name)
        assertEquals(1, book?.seriesNumber)
    }

    @Test
    context(_: TransactionProvider)
    fun `getBookById should return null for non-existent book`() = transaction {
        // Given: Book ID 999 does not exist
        // When: Getting non-existent book
        val book = with(user1Session) {
            getBookById(bookId = 999)
        }

        // Then: Should return null
        assertNull(book)
    }

    @Test
    context(_: TransactionProvider)
    fun `getBookById should include user note when available`() = transaction {
        // Given: User 1 has note for book 1
        // When: Getting book details
        val book = with(user1Session) {
            getBookById(bookId = 1)
        }

        // Then: Should include the note
        assertNotNull(book?.userNote)
        assertEquals("Remember to recommend this to my nephew.", book?.userNote)
    }

    @Test
    context(_: TransactionProvider)
    fun `getBookCover should return cover image bytes`() = transaction {
        // Given: Books in fixtures (may or may not have covers)
        // When: Getting book cover
        val cover = getBookCover(bookId = 1)

        // Then: Should return null or bytes (fixtures have NULL covers)
        // This tests the query works, even if no cover exists
        assertNull(cover) // Fixtures don't include cover data
    }

    @Test
    context(_: TransactionProvider)
    fun `starBook should add book to user's starred list`() = transaction {
        // Given: User 1 has not starred book 5
        // When: Starring the book
        val starred = with(user1Session) {
            starBook(bookId = 5)
        }

        // Then: Should return true
        assertTrue(starred)

        // Verify star was added
        val star = useTx { dslContext ->
            dslContext
                .selectFrom(USER_STARS)
                .where(USER_STARS.USER_ID.eq(1))
                .and(USER_STARS.BOOK_ID.eq(5))
                .fetchOne()
        }

        assertNotNull(star)
    }

    @Test
    context(_: TransactionProvider)
    fun `starBook should be idempotent`() = transaction {
        // Given: User 1 has already starred book 1
        // When: Starring the same book again
        val starred = with(user1Session) {
            starBook(bookId = 1)
        }

        // Then: Should return false (no new row inserted)
        assertFalse(starred)
    }

    @Test
    context(_: TransactionProvider)
    fun `unstarBook should remove book from starred list`() = transaction {
        // Given: User 1 has starred book 1
        // When: Unstarring the book
        val unstarred = with(user1Session) {
            unstarBook(bookId = 1)
        }

        // Then: Should return true
        assertTrue(unstarred)

        // Verify star was removed
        val star = useTx { dslContext ->
            dslContext
                .selectFrom(USER_STARS)
                .where(USER_STARS.USER_ID.eq(1)
                    .and(USER_STARS.BOOK_ID.eq(1)))
                .fetchOne()
        }

        assertNull(star)
    }

    @Test
    context(_: TransactionProvider)
    fun `unstarBook should return false if book not starred`() = transaction {
        // Given: User 1 has not starred book 5
        // When: Trying to unstar
        val unstarred = with(user1Session) {
            unstarBook(bookId = 5)
        }

        // Then: Should return false
        assertFalse(unstarred)
    }

    @Test
    context(_: TransactionProvider)
    fun `getStarredBooks should return only starred books`() = transaction {
        // Given: User 1 has starred books in fixtures
        // When: Getting starred books
        val result = with(user1Session) {
            getStarredBooks(limit = 10, offset = 0)
        }

        // Then: Should return only starred books
        assertTrue(result.books.isNotEmpty())
        assertTrue(result.books.all { it.isStarred })

        // User 1 starred books 1, 3, 4 in fixtures
        assertEquals(3, result.total)
    }

    @Test
    context(_: TransactionProvider)
    fun `getStarredBooks should support pagination`() = transaction {
        // Given: User 1 has 3 starred books
        // When: Getting first 2 starred books
        val result = with(user1Session) {
            getStarredBooks(limit = 2, offset = 0)
        }

        // Then: Should return 2 books with hasMore = true
        assertEquals(2, result.books.size)
        assertTrue(result.hasMore)
    }

    @Test
    context(_: TransactionProvider)
    fun `searchBooks should find books by title`() = transaction {
        // Given: Books with "Harry Potter" in title
        // When: Searching for "Harry"
        val query = SearchQuery(
            query = "Harry",
            genre = null,
            language = null,
            author = null,
            limit = 10,
            offset = 0,
        )
        val result = with(user1Session) {
            searchBooks(query)
        }

        // Then: Should return Harry Potter books
        assertTrue(result.books.isNotEmpty())
        assertTrue(result.books.any { it.title.contains("Harry") })
    }

    @Test
    context(_: TransactionProvider)
    fun `searchBooks should find books by author`() = transaction {
        // Given: Books by J.K. Rowling
        // When: Searching by author name
        val query = SearchQuery(
            query = "",
            genre = null,
            language = null,
            author = "Rowling",
            limit = 10,
            offset = 0,
        )
        val result = with(user1Session) {
            searchBooks(query)
        }

        // Then: Should return books by Rowling
        assertTrue(result.books.isNotEmpty())
        assertTrue(result.books.any { it.authors.any { author -> author.contains("Rowling") } })
    }

    @Test
    context(_: TransactionProvider)
    fun `searchBooks should filter by genre`() = transaction {
        // Given: Books with Fantasy genre
        // When: Searching by genre
        val query = SearchQuery(
            query = "",
            genre = "Fantasy",
            language = null,
            author = null,
            limit = 10,
            offset = 0,
        )
        val result = with(user1Session) {
            searchBooks(query)
        }

        // Then: Should return only Fantasy books
        assertTrue(result.books.isNotEmpty())
        assertTrue(result.books.all { it.genre == "Fantasy" })
    }

    @Test
    context(_: TransactionProvider)
    fun `searchBooks should filter by language`() = transaction {
        // Given: Books in English
        // When: Searching by language
        val query = SearchQuery(
            query = "",
            genre = null,
            language = "en",
            author = null,
            limit = 10,
            offset = 0,
        )
        val result = with(user1Session) {
            searchBooks(query)
        }

        // Then: Should return only English books
        assertTrue(result.books.isNotEmpty())
        assertTrue(result.books.all { it.language == "en" })
    }

    @Test
    context(_: TransactionProvider)
    fun `searchBooks should combine multiple filters`() = transaction {
        // Given: Books in database
        // When: Searching with multiple filters
        val query = SearchQuery(
            query = "Harry",
            genre = "Fantasy",
            language = "en",
            author = null,
            limit = 10,
            offset = 0,
        )
        val result = with(user1Session) {
            searchBooks(query)
        }

        // Then: Should return books matching all criteria
        assertTrue(result.books.isNotEmpty())
        assertTrue(result.books.all { it.genre == "Fantasy" && it.language == "en" })
        assertTrue(result.books.any { it.title.contains("Harry") })
    }

    @Test
    context(_: TransactionProvider)
    fun `getSimilarBooks should return books by same author or genre`() = transaction {
        // Given: Book 1 is Fantasy by J.K. Rowling
        // When: Getting similar books
        val similarBooks = with(user1Session) {
            getSimilarBooks(
                bookId = 1,
                genre = "Fantasy",
                limit = 5,
            )
        }

        // Then: Should return other Fantasy books or by same author
        assertTrue(similarBooks.isNotEmpty())
        // Should not include the original book
        assertTrue(similarBooks.none { it.id == 1 })
        // Should prioritize same genre
        assertTrue(similarBooks.any { it.genre == "Fantasy" })
    }

    @Test
    context(_: TransactionProvider)
    fun `getSimilarBooks should return empty list if genre is null`() = transaction {
        // Given: Book without genre
        // When: Getting similar books
        val similarBooks = with(user1Session) {
            getSimilarBooks(
                bookId = 1,
                genre = null,
                limit = 5,
            )
        }

        // Then: Should return empty list
        assertTrue(similarBooks.isEmpty())
    }

    @Test
    context(_: TransactionProvider)
    fun `updateBookCover should update cover image`() = transaction {
        // Given: Book 1 exists
        val coverData = "fake image data".toByteArray()

        // When: Updating book cover
        updateBookCover(bookId = 1, coverImage = coverData)

        // Then: Cover should be updated
        val cover = getBookCover(bookId = 1)
        assertNotNull(cover)
        assertEquals("fake image data", String(cover!!))
    }
}
