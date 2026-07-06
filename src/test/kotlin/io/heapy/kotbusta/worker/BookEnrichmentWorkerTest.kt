package io.heapy.kotbusta.worker

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.BOOKS
import io.heapy.kotbusta.jooq.tables.references.BOOK_AUTHORS
import io.heapy.kotbusta.jooq.tables.references.BOOK_ENRICHMENT
import io.heapy.kotbusta.jooq.tables.references.BOOK_GENRES
import io.heapy.kotbusta.model.BookSummary
import io.heapy.kotbusta.model.SearchQuery
import io.heapy.kotbusta.model.SearchResult
import io.heapy.kotbusta.service.AnnotationService
import io.heapy.kotbusta.service.BookSearchService
import io.heapy.kotbusta.service.EmbeddingCodec
import io.heapy.kotbusta.test.DatabaseExtension
import io.heapy.kotbusta.test.FakeEmbeddingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Clock

@ExtendWith(DatabaseExtension::class)
class BookEnrichmentWorkerTest {
    @Test
    fun `processPass extracts annotation stores embedding and schedules index rebuild`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        val embeddingService = FakeEmbeddingService()
        val searchService = RecordingBookSearchService()
        val tx = applicationModule.transactionProvider.value
        seedBook(tx, BOOK_ID, title = "Synthetic Enrichment Book")
        writeBookArchive(applicationModule, "Synthetic annotation for indexing.")

        val processed = worker(applicationModule, embeddingService, searchService).processPass(this)

        assertEquals(1, processed)
        val row = enrichmentRow(tx, BOOK_ID)
        assertNotNull(row)
        assertEquals("DONE", row!!.status)
        assertEquals("Synthetic annotation for indexing.", row.annotation)
        assertArrayEquals(
            FakeEmbeddingService.vectorFor(embeddingService.passageBatches.single().single()),
            EmbeddingCodec.decode(row.embedding!!),
            0.0001f,
        )
        assertTrue(embeddingService.passageBatches.single().single().contains("Synthetic Enrichment Book"))
        assertTrue(searchService.rebuildSchedules >= 1)
    }

    @Test
    fun `processPass records failed enrichment when archive is missing`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        val embeddingService = FakeEmbeddingService()
        val tx = applicationModule.transactionProvider.value
        seedBook(tx, BOOK_ID, title = "Missing Archive Book")

        val processed = worker(applicationModule, embeddingService, RecordingBookSearchService()).processPass(this)

        assertEquals(1, processed)
        val row = enrichmentRow(tx, BOOK_ID)
        assertNotNull(row)
        assertEquals("FAILED", row!!.status)
        assertEquals(null, row.embedding)
        assertTrue(embeddingService.passageBatches.isEmpty())
    }

    @Test
    fun `recoverProcessing releases abandoned claims`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        seedBook(tx, BOOK_ID, title = "Claimed Book")
        tx.transaction(READ_WRITE) {
            useTx { dsl ->
                dsl.insertInto(BOOK_ENRICHMENT)
                    .set(BOOK_ENRICHMENT.BOOK_ID, BOOK_ID)
                    .set(BOOK_ENRICHMENT.STATUS, "PROCESSING")
                    .execute()
            }
        }

        val recovered = worker(applicationModule, FakeEmbeddingService(), RecordingBookSearchService()).recoverProcessing()

        assertEquals(1, recovered)
        assertEquals(null, enrichmentRow(tx, BOOK_ID))
    }

    private fun worker(
        applicationModule: ApplicationModule,
        embeddingService: FakeEmbeddingService,
        searchService: RecordingBookSearchService,
    ) = BookEnrichmentWorker(
        transactionProvider = applicationModule.transactionProvider.value,
        booksDataPath = applicationModule.booksDataPath.value,
        annotationService = AnnotationService(),
        embeddingService = embeddingService,
        bookSearchService = searchService,
        batchSize = 10,
        parallelism = 2,
        rebuildEvery = 1,
    )

    private suspend fun seedBook(
        tx: TransactionProvider,
        bookId: Int,
        title: String,
    ) {
        tx.transaction(READ_WRITE) {
            useTx { dsl ->
                val now = Clock.System.now()
                dsl.insertInto(BOOKS)
                    .set(BOOKS.ID, bookId)
                    .set(BOOKS.TITLE, title)
                    .set(BOOKS.LANGUAGE, "en")
                    .set(BOOKS.FILE_FORMAT, "fb2")
                    .set(BOOKS.FILE_PATH, "book.fb2")
                    .set(BOOKS.ARCHIVE_PATH, "synthetic/archive")
                    .set(BOOKS.DATE_ADDED, now)
                    .set(BOOKS.CREATED_AT, now)
                    .execute()
                dsl.insertInto(BOOK_AUTHORS)
                    .set(BOOK_AUTHORS.BOOK_ID, bookId)
                    .set(BOOK_AUTHORS.AUTHOR_ID, 7)
                    .execute()
                dsl.insertInto(BOOK_GENRES)
                    .set(BOOK_GENRES.BOOK_ID, bookId)
                    .set(BOOK_GENRES.GENRE_ID, 5)
                    .execute()
            }
        }
    }

    private fun writeBookArchive(
        applicationModule: ApplicationModule,
        annotation: String,
    ) {
        val archivePath = applicationModule.booksDataPath.value.resolve("synthetic/archive.zip")
        Files.createDirectories(archivePath.parent)
        ZipOutputStream(Files.newOutputStream(archivePath)).use { zip ->
            zip.putNextEntry(ZipEntry("book.fb2"))
            zip.write(
                """
                <FictionBook>
                  <description>
                    <title-info>
                      <annotation><p>$annotation</p></annotation>
                    </title-info>
                  </description>
                </FictionBook>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
    }

    private suspend fun enrichmentRow(
        tx: TransactionProvider,
        bookId: Int,
    ) = tx.transaction(READ_ONLY) {
        useTx { dsl ->
            dsl.selectFrom(BOOK_ENRICHMENT)
                .where(BOOK_ENRICHMENT.BOOK_ID.eq(bookId))
                .fetchOne()
        }
    }

    private class RecordingBookSearchService : BookSearchService {
        var rebuildSchedules = 0

        override fun initialize(scope: CoroutineScope) = Unit

        override fun scheduleRebuild(scope: CoroutineScope) {
            rebuildSchedules++
        }

        override suspend fun rebuildNow() = Unit

        override suspend fun search(query: SearchQuery): SearchResult =
            SearchResult(books = emptyList(), total = 0, hasMore = false)

        override suspend fun findSimilar(bookId: Int, limit: Int): List<BookSummary> =
            emptyList()
    }

    private companion object {
        private const val BOOK_ID = 9101
    }
}
