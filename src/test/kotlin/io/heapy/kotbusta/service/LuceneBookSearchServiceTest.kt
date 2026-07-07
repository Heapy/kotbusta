package io.heapy.kotbusta.service

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.BOOK_ENRICHMENT
import io.heapy.kotbusta.model.SearchQuery
import io.heapy.kotbusta.test.DatabaseExtension
import io.heapy.kotbusta.test.FakeEmbeddingService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Files

@ExtendWith(DatabaseExtension::class)
class LuceneBookSearchServiceTest {
    @Test
    fun `rebuild handles catalogs larger than one SQLite lookup batch`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        addBulkSearchFixtures(applicationModule, bookCount = 600)

        val searchService = applicationModule.bookSearchService.value
        searchService.rebuildNow()

        // Page through results (server caps per-page size) and verify the rebuild
        // indexed the whole catalog across SQLite lookup-batch boundaries.
        val pageSize = 100
        val titles = mutableListOf<String>()
        var offset = 0
        var total = 0L
        while (true) {
            val page = searchService.search(
                query = SearchQuery(query = "bulk", limit = pageSize, offset = offset),
            )
            total = page.total
            if (page.books.isEmpty()) break
            titles += page.books.map { it.title }
            offset += pageSize
            if (offset >= total) break
        }

        assertEquals(600L, total)
        assertEquals(600, titles.size)
        assertTrue(titles.all { it.startsWith("Bulk Search Book") })
    }

    @Test
    fun `rebuild deletes the previous index backup and keeps serving`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        addBulkSearchFixtures(applicationModule, bookCount = 5)

        val indexPath = Files.createTempDirectory("test-kotbusta-lucene-swap-")
        val service = LuceneBookSearchService(
            transactionProvider = applicationModule.transactionProvider.value,
            indexPath = indexPath,
        )
        val backupPath = indexPath.resolveSibling("${indexPath.fileName}-backup")

        try {
            service.rebuildNow()
            assertTrue(service.search(SearchQuery(query = "bulk", limit = 10)).total > 0)

            // The second rebuild displaces the first index while its reader is still
            // open. The backup must be removed once the new reader is installed and
            // must not linger, and the service must keep returning results.
            service.rebuildNow()

            assertEquals(false, Files.exists(backupPath))
            assertEquals(5L, service.search(SearchQuery(query = "bulk", limit = 10)).total)
        } finally {
            service.close()
        }
    }

    @Test
    fun `semantic search ranks books by stored embeddings`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        seedEmbeddingFixtures(applicationModule)
        val searchService = semanticSearchService(applicationModule)

        try {
            searchService.rebuildNow()

            val result = searchService.search(SearchQuery(query = "wizard school", limit = 10))

            assertEquals(false, result.hasMore)
            assertTrue(result.total >= 3)
            assertEquals(1, result.books.first().id)
            assertEquals(listOf("wizard school"), searchService.fakeEmbeddingService.queryInputs)
        } finally {
            searchService.close()
        }
    }

    @Test
    fun `semantic search includes lexical matches without embeddings`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        seedEmbeddingFixtures(applicationModule)
        val searchService = semanticSearchService(applicationModule)

        try {
            searchService.rebuildNow()

            val result = searchService.search(SearchQuery(query = "mistborn", limit = 10))

            assertEquals(false, result.hasMore)
            assertEquals(10, result.books.first().id)
            assertTrue(result.books.any { it.id == 10 })
        } finally {
            searchService.close()
        }
    }

    @Test
    fun `findSimilar uses nearest vector when target embedding exists`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        seedEmbeddingFixtures(applicationModule)
        val searchService = semanticSearchService(applicationModule)

        try {
            searchService.rebuildNow()

            val similar = searchService.findSimilar(bookId = 1, limit = 1)

            assertEquals(listOf(2), similar.map { it.id })
        } finally {
            searchService.close()
        }
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
                        ) VALUES (?, ?, ?, NULL, NULL, ?, ?, ?, ?, ?, NULL, ?)
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

    private suspend fun seedEmbeddingFixtures(applicationModule: ApplicationModule) {
        applicationModule.transactionProvider.value.transaction(READ_WRITE) {
            useTx { dslContext ->
                listOf(
                    1 to FakeEmbeddingService.vectorFor("harry magic"),
                    2 to FakeEmbeddingService.vectorFor("harry wizard"),
                    5 to FakeEmbeddingService.vectorFor("overlook horror"),
                    8 to FakeEmbeddingService.vectorFor("foundation galaxy"),
                ).forEach { (bookId, embedding) ->
                    dslContext.update(BOOK_ENRICHMENT)
                        .set(BOOK_ENRICHMENT.EMBEDDING, EmbeddingCodec.encode(embedding))
                        .where(BOOK_ENRICHMENT.BOOK_ID.eq(bookId))
                        .execute()
                }
            }
        }
    }

    private fun semanticSearchService(applicationModule: ApplicationModule): SemanticSearchServiceFixture {
        val embeddingService = FakeEmbeddingService()
        return SemanticSearchServiceFixture(
            fakeEmbeddingService = embeddingService,
            service = LuceneBookSearchService(
                transactionProvider = applicationModule.transactionProvider.value,
                indexPath = Files.createTempDirectory("test-kotbusta-semantic-lucene-"),
                embeddingService = embeddingService,
            ),
        )
    }

    private class SemanticSearchServiceFixture(
        val fakeEmbeddingService: FakeEmbeddingService,
        private val service: LuceneBookSearchService,
    ) {
        suspend fun rebuildNow() = service.rebuildNow()

        suspend fun search(query: SearchQuery) = service.search(query)

        suspend fun findSimilar(bookId: Int, limit: Int) = service.findSimilar(bookId, limit)

        fun close() = service.close()
    }

    private companion object {
        private const val BULK_BOOK_START_ID = 1000
        private const val BULK_AUTHOR_ID = 7
        private const val BULK_GENRE_ID = 5
        private const val BULK_BOOK_TIMESTAMP = "2024-02-01T00:00:00Z"
    }
}
