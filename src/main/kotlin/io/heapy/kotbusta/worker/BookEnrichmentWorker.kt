package io.heapy.kotbusta.worker

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.dao.getBookById
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.BOOKS
import io.heapy.kotbusta.jooq.tables.references.BOOK_ENRICHMENT
import io.heapy.kotbusta.model.Book
import io.heapy.kotbusta.service.AnnotationService
import io.heapy.kotbusta.service.BookSearchService
import io.heapy.kotbusta.service.EmbeddingCodec
import io.heapy.kotbusta.service.EmbeddingService
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.time.Clock
import kotlin.time.Instant

class BookEnrichmentWorker(
    private val transactionProvider: TransactionProvider,
    private val booksDataPath: Path,
    private val annotationService: AnnotationService,
    private val embeddingService: EmbeddingService,
    private val bookSearchService: BookSearchService,
    private val batchSize: Int,
    private val parallelism: Int,
    private val rebuildEvery: Int,
    private val meterRegistry: MeterRegistry? = null,
) {
    private var job: Job? = null
    private var completedSinceRebuild = 0

    fun start(
        scope: CoroutineScope,
        intervalMillis: Long,
    ) {
        job = scope.launch {
            recoverProcessing()
            while (isActive) {
                try {
                    processPass(scope)
                } catch (e: Exception) {
                    log.error("Error in book enrichment worker", e)
                }
                delay(intervalMillis)
            }
        }
        log.info("Book enrichment worker started with interval ${intervalMillis}ms")
    }

    fun stop() {
        job?.cancel()
        log.info("Book enrichment worker stopped")
    }

    suspend fun recoverProcessing(): Int {
        val recovered = transactionProvider.transaction(READ_WRITE) {
            deleteProcessingClaims()
        }
        if (recovered > 0) {
            log.warn("Recovered $recovered abandoned book enrichment claim(s)")
        }
        return recovered
    }

    suspend fun processPass(scope: CoroutineScope): Int {
        var processed = 0
        var anyProcessed = false

        while (true) {
            val claimedBookIds = transactionProvider.transaction(READ_WRITE) {
                claimBatch(batchSize)
            }
            if (claimedBookIds.isEmpty()) {
                break
            }

            val batchResults = processClaimedBatch(claimedBookIds)
            transactionProvider.transaction(READ_WRITE) {
                recordResults(batchResults)
            }

            val doneCount = batchResults.count { it is EnrichmentResult.Done }
            completedSinceRebuild += doneCount
            processed += batchResults.size
            anyProcessed = true

            if (rebuildEvery > 0 && completedSinceRebuild >= rebuildEvery) {
                completedSinceRebuild = 0
                bookSearchService.scheduleRebuild(scope)
            }
        }

        if (anyProcessed) {
            bookSearchService.scheduleRebuild(scope)
        }

        return processed
    }

    private suspend fun processClaimedBatch(bookIds: List<Int>): List<EnrichmentResult> {
        val semaphore = Semaphore(parallelism.coerceAtLeast(1))
        val prepared = coroutineScope {
            bookIds.map { bookId ->
                async {
                    semaphore.withPermit {
                        prepareBook(bookId)
                    }
                }
            }.awaitAll()
        }

        val failed = prepared.filterIsInstance<PreparedEnrichment.Failed>()
        val ready = prepared.filterIsInstance<PreparedEnrichment.Ready>()
        if (ready.isEmpty()) {
            return failed.map { EnrichmentResult.Failed(it.bookId, it.error) }
        }

        val vectors = try {
            embeddingService.embedPassages(ready.map(PreparedEnrichment.Ready::embeddingText))
        } catch (e: Exception) {
            log.error("Embedding batch failed", e)
            return prepared.map {
                when (it) {
                    is PreparedEnrichment.Ready -> EnrichmentResult.Failed(it.bookId, "Embedding failed: ${e.message}")
                    is PreparedEnrichment.Failed -> EnrichmentResult.Failed(it.bookId, it.error)
                }
            }
        }

        val done = ready.zip(vectors).map { (item, vector) ->
            EnrichmentResult.Done(
                bookId = item.bookId,
                annotation = item.annotation,
                embedding = vector,
            )
        }

        return done + failed.map { EnrichmentResult.Failed(it.bookId, it.error) }
    }

    private suspend fun prepareBook(bookId: Int): PreparedEnrichment {
        val book = transactionProvider.transaction(READ_ONLY) {
            getBookById(bookId)
        } ?: return PreparedEnrichment.Failed(bookId, "Book disappeared before enrichment")

        val annotation = try {
            extractAnnotation(book)
        } catch (e: Exception) {
            return PreparedEnrichment.Failed(bookId, "Annotation extraction failed: ${e.message}")
        }

        return PreparedEnrichment.Ready(
            bookId = book.id,
            annotation = annotation,
            embeddingText = buildEmbeddingText(book, annotation),
        )
    }

    private fun extractAnnotation(book: Book): String? {
        val archiveFile = booksDataPath.resolve("${book.archivePath}.zip")
        if (!archiveFile.exists()) {
            throw IllegalStateException("Book archive not found: ${book.archivePath}.zip")
        }

        ZipFile(archiveFile.toFile()).use { zip ->
            val entry = zip.getEntry(book.filePath)
                ?: throw IllegalStateException("FB2 entry '${book.filePath}' not found in ${book.archivePath}.zip")
            return zip.getInputStream(entry).use(annotationService::extractAnnotation)
        }
    }

    private fun buildEmbeddingText(book: Book, annotation: String?): String =
        buildList {
            add(book.title)
            add(book.authors.joinToString(", ") { it.fullName })
            book.series?.let { series ->
                add(
                    buildString {
                        append(series.name)
                        book.seriesNumber?.let { append(" #").append(it) }
                    },
                )
            }
            if (book.genres.isNotEmpty()) {
                add(book.genres.joinToString(", "))
            }
            annotation?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString("\n")

    context(_: TransactionContext)
    private fun claimBatch(limit: Int): List<Int> = useTx { dslContext ->
        val candidateIds = dslContext
            .select(BOOKS.ID)
            .from(BOOKS)
            .leftJoin(BOOK_ENRICHMENT).on(BOOKS.ID.eq(BOOK_ENRICHMENT.BOOK_ID))
            .where(BOOK_ENRICHMENT.BOOK_ID.isNull)
            .orderBy(BOOKS.ID.asc())
            .limit(limit)
            .fetch(BOOKS.ID)
            .filterNotNull()

        candidateIds.mapNotNull { bookId ->
            val inserted = dslContext
                .insertInto(BOOK_ENRICHMENT)
                .set(BOOK_ENRICHMENT.BOOK_ID, bookId)
                .set(BOOK_ENRICHMENT.STATUS, STATUS_PROCESSING)
                .onConflictDoNothing()
                .execute()

            if (inserted > 0) bookId else null
        }
    }

    context(_: TransactionContext)
    private fun deleteProcessingClaims(): Int = useTx { dslContext ->
        dslContext
            .deleteFrom(BOOK_ENRICHMENT)
            .where(BOOK_ENRICHMENT.STATUS.eq(STATUS_PROCESSING))
            .execute()
    }

    context(_: TransactionContext)
    private fun recordResults(results: List<EnrichmentResult>) = useTx { dslContext ->
        val enrichedAt = Clock.System.now()
        results.forEach { result ->
            when (result) {
                is EnrichmentResult.Done -> {
                    meterRegistry
                        ?.counter("kotbusta_enrichment_books_total", "status", "done")
                        ?.increment()
                    upsertDone(result, enrichedAt)
                }

                is EnrichmentResult.Failed -> {
                    meterRegistry
                        ?.counter("kotbusta_enrichment_books_total", "status", "failed")
                        ?.increment()
                    upsertFailed(result, enrichedAt)
                }
            }
        }
    }

    context(_: TransactionContext)
    private fun upsertDone(result: EnrichmentResult.Done, enrichedAt: Instant) = useTx { dslContext ->
        dslContext
            .insertInto(BOOK_ENRICHMENT)
            .set(BOOK_ENRICHMENT.BOOK_ID, result.bookId)
            .set(BOOK_ENRICHMENT.ANNOTATION, result.annotation)
            .set(BOOK_ENRICHMENT.EMBEDDING, EmbeddingCodec.encode(result.embedding))
            .set(BOOK_ENRICHMENT.STATUS, STATUS_DONE)
            .set(BOOK_ENRICHMENT.ENRICHED_AT, enrichedAt)
            .onConflict(BOOK_ENRICHMENT.BOOK_ID)
            .doUpdate()
            .set(BOOK_ENRICHMENT.ANNOTATION, result.annotation)
            .set(BOOK_ENRICHMENT.EMBEDDING, EmbeddingCodec.encode(result.embedding))
            .set(BOOK_ENRICHMENT.STATUS, STATUS_DONE)
            .set(BOOK_ENRICHMENT.ENRICHED_AT, enrichedAt)
            .execute()
    }

    context(_: TransactionContext)
    private fun upsertFailed(result: EnrichmentResult.Failed, enrichedAt: Instant) = useTx { dslContext ->
        dslContext
            .insertInto(BOOK_ENRICHMENT)
            .set(BOOK_ENRICHMENT.BOOK_ID, result.bookId)
            .set(BOOK_ENRICHMENT.STATUS, STATUS_FAILED)
            .set(BOOK_ENRICHMENT.ENRICHED_AT, enrichedAt)
            .onConflict(BOOK_ENRICHMENT.BOOK_ID)
            .doUpdate()
            .set(BOOK_ENRICHMENT.STATUS, STATUS_FAILED)
            .set(BOOK_ENRICHMENT.ENRICHED_AT, enrichedAt)
            .execute()
        log.warn("Book ${result.bookId} enrichment failed: ${result.error}")
    }

    private sealed interface PreparedEnrichment {
        data class Ready(
            val bookId: Int,
            val annotation: String?,
            val embeddingText: String,
        ) : PreparedEnrichment

        data class Failed(
            val bookId: Int,
            val error: String,
        ) : PreparedEnrichment
    }

    private sealed interface EnrichmentResult {
        data class Done(
            val bookId: Int,
            val annotation: String?,
            val embedding: FloatArray,
        ) : EnrichmentResult

        data class Failed(
            val bookId: Int,
            val error: String,
        ) : EnrichmentResult
    }

    private companion object : Logger() {
        private const val STATUS_PROCESSING = "PROCESSING"
        private const val STATUS_DONE = "DONE"
        private const val STATUS_FAILED = "FAILED"
    }
}
