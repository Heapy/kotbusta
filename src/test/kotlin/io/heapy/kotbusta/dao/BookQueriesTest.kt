package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.test.DatabaseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(DatabaseExtension::class)
class BookQueriesTest {
    @Test
    context(_: TransactionProvider)
    fun `getBooks should return paginated books`() = transaction {
        val result = getBooks(limit = 5, offset = 0)

        assertEquals(5, result.books.size)
        assertEquals(10L, result.total)
        assertTrue(result.hasMore)

        val firstBook = result.books[0]
        assertNotNull(firstBook.id)
        assertNotNull(firstBook.title)
        assertNotNull(firstBook.language)
    }

    @Test
    context(_: TransactionProvider)
    fun `getBookById should return complete book details with enrichment annotation`() = transaction {
        val book = getBookById(bookId = 1)

        assertNotNull(book)
        assertEquals(1, book?.id)
        assertEquals("Harry Potter and the Philosopher's Stone", book?.title)
        assertEquals("The first book in the Harry Potter series.", book?.annotation)
        assertEquals(listOf("Fantasy"), book?.genres)
        assertEquals("en", book?.language)
        assertTrue(book?.authors?.isNotEmpty() == true)
        assertEquals("Harry Potter", book?.series?.name)
        assertEquals(1, book?.seriesNumber)
    }

    @Test
    context(_: TransactionProvider)
    fun `getBookById should return null for non-existent book`() = transaction {
        assertNull(getBookById(bookId = 999))
    }

    @Test
    context(_: TransactionProvider)
    fun `getSimilarBooks should return books by same author or genre`() = transaction {
        val similarBooks = getSimilarBooks(
            bookId = 1,
            limit = 5,
        )

        assertTrue(similarBooks.isNotEmpty())
        assertTrue(similarBooks.none { it.id == 1 })
        assertTrue(similarBooks.any { it.genres.contains("Fantasy") })
        assertTrue(similarBooks.any { it.id == 2 })
    }

    @Test
    context(_: TransactionProvider)
    fun `getSimilarBooks should return empty list when no author or genre overlaps`() = transaction {
        val similarBooks = getSimilarBooks(
            bookId = 5,
            limit = 5,
        )

        assertTrue(similarBooks.isEmpty())
    }
}
