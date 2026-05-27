package io.heapy.kotbusta.service

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.model.SearchQuery
import io.heapy.kotbusta.test.DatabaseExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(DatabaseExtension::class)
class LuceneBookSearchServiceTest {
    @Test
    fun `rebuild handles catalogs larger than one SQLite lookup batch`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        addBulkSearchFixtures(applicationModule, bookCount = 600)

        val searchService = applicationModule.bookSearchService.value
        searchService.rebuildNow()

        val result = searchService.search(
            query = SearchQuery(query = "bulk", limit = 600),
            userId = 1,
        )

        assertEquals(600L, result.total)
        assertEquals(600, result.books.size)
        assertTrue(result.books.all { it.title.startsWith("Bulk Search Book") })
    }

    private suspend fun addBulkSearchFixtures(
        applicationModule: ApplicationModule,
        bookCount: Int,
    ) {
        applicationModule.transactionProvider.value.transaction(READ_WRITE) {
            useTx { dslContext ->
                repeat(bookCount) { index ->
                    val bookId = BULK_BOOK_START_ID + index
                    dslContext.execute(
                        """
                        INSERT INTO BOOKS (
                            ID,
                            TITLE,
                            ANNOTATION,
                            LANGUAGE,
                            SERIES_ID,
                            SERIES_NUMBER,
                            FILE_FORMAT,
                            FILE_PATH,
                            ARCHIVE_PATH,
                            FILE_SIZE,
                            DATE_ADDED,
                            COVER_IMAGE,
                            CREATED_AT
                        ) VALUES (?, ?, NULL, ?, NULL, NULL, ?, ?, ?, ?, ?, NULL, ?)
                        """.trimIndent(),
                        bookId,
                        "Bulk Search Book $bookId",
                        "en",
                        "fb2",
                        "/books/$bookId.fb2",
                        "/archive/$bookId.zip",
                        1024,
                        BULK_BOOK_TIMESTAMP,
                        BULK_BOOK_TIMESTAMP,
                    )
                    dslContext.execute(
                        "INSERT INTO BOOK_AUTHORS (BOOK_ID, AUTHOR_ID) VALUES (?, ?)",
                        bookId,
                        BULK_AUTHOR_ID,
                    )
                    dslContext.execute(
                        "INSERT INTO BOOK_GENRES (BOOK_ID, GENRE_ID) VALUES (?, ?)",
                        bookId,
                        BULK_GENRE_ID,
                    )
                }
            }
        }
    }

    private companion object {
        private const val BULK_BOOK_START_ID = 1000
        private const val BULK_AUTHOR_ID = 7
        private const val BULK_GENRE_ID = 5
        private const val BULK_BOOK_TIMESTAMP = "2024-02-01T00:00:00Z"
    }
}
