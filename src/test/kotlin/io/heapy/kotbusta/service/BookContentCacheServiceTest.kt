package io.heapy.kotbusta.service

import io.heapy.kotbusta.model.Book
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

class BookContentCacheServiceTest {

    @Test
    fun `concurrent callers for the same uncached book share one parse`() {
        val parser = FakeBookContentParser(delayMs = 50)
        val cache = BookContentCacheService(parser = parser, maxEntries = 10)
        try {
            runBlocking {
                coroutineScope {
                    repeat(8) {
                        launch { cache.get(book(1)) }
                    }
                }
            }
            assertEquals(1, parser.callCountFor(1))
        } finally {
            cache.close()
        }
    }

    @Test
    fun `a failed parse is not cached`() {
        val parser = FakeBookContentParser(failFirstNCallsPerBook = 1)
        val cache = BookContentCacheService(parser = parser, maxEntries = 10)
        try {
            assertThrows(BookFileException::class.java) {
                runBlocking { cache.get(book(1)) }
            }

            val result = runBlocking { cache.get(book(1)) }

            assertTrue(result.pages.isEmpty())
            assertEquals(2, parser.callCountFor(1))
        } finally {
            cache.close()
        }
    }

    @Test
    fun `eviction is LRU, not insertion-order FIFO`() {
        val parser = FakeBookContentParser()
        val cache = BookContentCacheService(parser = parser, maxEntries = 2)
        try {
            runBlocking {
                cache.get(book(1))
                cache.get(book(2))
                cache.get(book(1)) // touch 1 so 2 becomes the least-recently-used entry
                cache.get(book(3)) // over budget: should evict 2, not 1

                cache.get(book(1))
                assertEquals(1, parser.callCountFor(1), "book 1 should still be cached")

                cache.get(book(2))
                assertEquals(2, parser.callCountFor(2), "book 2 should have been evicted and re-parsed")
            }
        } finally {
            cache.close()
        }
    }

    private fun book(id: Int) = Book(
        id = id,
        title = "T",
        annotation = null,
        genres = emptyList(),
        language = "ru",
        authors = emptyList(),
        series = null,
        seriesNumber = null,
        filePath = "$id.fb2",
        archivePath = "archive",
        fileSize = null,
        dateAdded = Instant.fromEpochSeconds(0),
        coverImageUrl = null,
    )

    private class FakeBookContentParser(
        private val delayMs: Long = 0,
        private val failFirstNCallsPerBook: Int = 0,
    ) : BookContentParser {
        private val callCounts = ConcurrentHashMap<Int, AtomicInteger>()

        fun callCountFor(bookId: Int): Int = callCounts[bookId]?.get() ?: 0

        override suspend fun render(book: Book): ParsedBook {
            val callNumber = callCounts.computeIfAbsent(book.id) { AtomicInteger(0) }.incrementAndGet()
            if (delayMs > 0) delay(delayMs)
            if (callNumber <= failFirstNCallsPerBook) {
                throw BookFileException("simulated failure #$callNumber for book ${book.id}")
            }
            return ParsedBook(pages = emptyList(), toc = emptyList(), anchorPageIndex = emptyMap(), hasImages = false)
        }
    }
}
