package io.heapy.kotbusta.worker

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.dao.createKindleSendEvent
import io.heapy.kotbusta.dao.findKindleDeviceByIdAndUserId
import io.heapy.kotbusta.dao.findPendingQueueItems
import io.heapy.kotbusta.dao.findQueueItemById
import io.heapy.kotbusta.dao.getBookById
import io.heapy.kotbusta.dao.incrementQueueItemAttempts
import io.heapy.kotbusta.dao.markQueueItemAsProcessing
import io.heapy.kotbusta.dao.resetStuckProcessingItems
import io.heapy.kotbusta.dao.updateQueueItemStatus
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.Book
import io.heapy.kotbusta.model.KindleSendStatus
import io.heapy.kotbusta.service.BookFileService
import io.heapy.kotbusta.service.EmailResult
import io.heapy.kotbusta.service.EmailService
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Drains the Kindle send queue. Each tick claims a batch (a short RW transaction
 * that flips items PENDING -> PROCESSING), then for each claimed item does the
 * slow work — materialize the book file and call SES — **outside** any
 * transaction, and finally records the outcome in another short RW transaction.
 *
 * This keeps the single writer connection free during network I/O and makes each
 * step atomic. Because a crash between "claim" and "record outcome" would strand
 * an item in PROCESSING, [recoverStuckItems] returns timed-out PROCESSING items to
 * PENDING on startup and defensively on each tick.
 */
class KindleSendWorker(
    private val emailService: EmailService,
    private val transactionProvider: TransactionProvider,
    private val bookFileService: BookFileService,
    private val batchSize: Int,
    private val maxRetries: Int,
    private val stuckProcessingTimeout: Duration = 15.minutes,
    private val meterRegistry: MeterRegistry? = null,
) {
    private var job: Job? = null

    fun start(
        scope: CoroutineScope,
        intervalMillis: Long = 30_000,
    ) {
        job = scope.launch {
            recoverStuckItems()
            while (isActive) {
                try {
                    processQueue()
                } catch (e: Exception) {
                    log.error("Error in Kindle send worker", e)
                }
                delay(intervalMillis)
            }
        }
        log.info("Kindle send worker started with interval ${intervalMillis}ms")
    }

    fun stop() {
        job?.cancel()
        log.info("Kindle send worker stopped")
    }

    suspend fun recoverStuckItems() {
        val cutoff = Clock.System.now() - stuckProcessingTimeout
        val reset = transactionProvider.transaction(READ_WRITE) {
            resetStuckProcessingItems(cutoff)
        }
        if (reset > 0) {
            log.warn("Recovered $reset Kindle queue item(s) stuck in PROCESSING back to PENDING")
        }
    }

    suspend fun processQueue() {
        // Recover defensively in case this process itself has been running long
        // enough to strand an item between claim and outcome.
        recoverStuckItems()

        // 1) Claim a batch atomically. No suspending work happens in this tx.
        val claimedIds = transactionProvider.transaction(READ_WRITE) {
            findPendingQueueItems(batchSize).mapNotNull { item ->
                val id = item.id!!
                if (markQueueItemAsProcessing(id)) {
                    createKindleSendEvent(id, KindleSendStatus.PROCESSING.name)
                    id
                } else {
                    null
                }
            }
        }

        if (claimedIds.isEmpty()) {
            log.debug("No pending items in Kindle send queue")
            return
        }
        log.info("Processing ${claimedIds.size} claimed Kindle send item(s)")

        // 2) Process each claimed item outside of any DB transaction.
        for (queueId in claimedIds) {
            try {
                processClaimedItem(queueId)
            } catch (e: Exception) {
                log.error("Failed to process queue item $queueId", e)
                recordOutcome(
                    queueId,
                    KindleSendStatus.FAILED,
                    "Unexpected error: ${e.message}",
                    Json.encodeToString(FailedEventDetails(reason = "Unexpected error", error = e.message)),
                )
            }
        }
    }

    private suspend fun processClaimedItem(queueId: Int) {
        // Load everything we need in one short read transaction.
        val work = transactionProvider.transaction(READ_ONLY) {
            val item = findQueueItemById(queueId) ?: return@transaction null
            val device = findKindleDeviceByIdAndUserId(item.deviceId, item.userId)
            val book = context(UserSession(userId = item.userId, email = "", name = "")) {
                getBookById(item.bookId)
            }
            Triple(item, device, book)
        }

        if (work == null) {
            log.warn("Queue item $queueId not found after claiming")
            return
        }

        val (item, device, book) = work

        if (device == null) {
            log.warn("Device ${item.deviceId} not found for queue item $queueId")
            recordOutcome(
                queueId,
                KindleSendStatus.FAILED,
                "Device not found",
                Json.encodeToString(FailedEventDetails(reason = "Device not found")),
            )
            return
        }

        if (book == null) {
            log.warn("Book ${item.bookId} not available for queue item $queueId")
            recordOutcome(
                queueId,
                KindleSendStatus.FAILED,
                "Book not available",
                Json.encodeToString(FailedEventDetails(reason = "Book not found or no longer available")),
            )
            return
        }

        // Materialize the book file and send it via SES — both outside any tx.
        val materialized = try {
            bookFileService.materialize(book, item.format)
        } catch (e: Exception) {
            log.error("Failed to prepare book file for queue item $queueId", e)
            recordOutcome(
                queueId,
                KindleSendStatus.FAILED,
                "Failed to prepare book file: ${e.message}",
                Json.encodeToString(FailedEventDetails(reason = "Book file not available", error = e.message)),
            )
            return
        }

        val result = try {
            emailService.sendBookToKindle(
                recipientEmail = device.email,
                bookFile = materialized.file,
                bookTitle = book.title,
                format = item.format,
            )
        } finally {
            materialized.cleanup()
        }

        when (result) {
            is EmailResult.Success -> {
                recordOutcome(
                    queueId,
                    KindleSendStatus.COMPLETED,
                    null,
                    Json.encodeToString(SentEventDetails(messageId = result.messageId)),
                )
                log.info("Successfully sent book ${book.id} to device ${device.id}")
            }

            is EmailResult.RetryableFailure ->
                handleRetryableFailure(queueId, item.attempts, result.error)

            is EmailResult.PermanentFailure -> {
                recordOutcome(
                    queueId,
                    KindleSendStatus.FAILED,
                    result.error,
                    Json.encodeToString(FailedEventDetails(reason = "Permanent failure", error = result.error)),
                )
                log.error("Permanent failure for queue item $queueId: ${result.error}")
            }
        }
    }

    private suspend fun handleRetryableFailure(
        queueId: Int,
        currentAttempts: Int,
        error: String,
    ) {
        val attempts = currentAttempts + 1
        transactionProvider.transaction(READ_WRITE) {
            if (attempts < maxRetries) {
                val nextRunAt = calculateNextRunTime(attempts)
                incrementQueueItemAttempts(queueId, nextRunAt)
                updateQueueItemStatus(queueId, KindleSendStatus.PENDING, error)
                createKindleSendEvent(
                    queueId,
                    KindleSendStatus.PENDING.name,
                    Json.encodeToString(
                        RetryEventDetails(
                            attempt = attempts,
                            nextRunAt = nextRunAt.toString(),
                            error = error,
                        ),
                    ),
                )
            } else {
                updateQueueItemStatus(queueId, KindleSendStatus.FAILED, "Max retries exceeded: $error")
                createKindleSendEvent(
                    queueId,
                    KindleSendStatus.FAILED.name,
                    Json.encodeToString(FailedEventDetails(reason = "Max retries exceeded", error = error)),
                )
            }
        }
        if (attempts < maxRetries) {
            log.warn("Retryable failure for queue item $queueId, attempt $attempts: $error")
        } else {
            log.error("Max retries exceeded for queue item $queueId: $error")
        }
    }

    private suspend fun recordOutcome(
        queueId: Int,
        status: KindleSendStatus,
        error: String?,
        detailsJson: String?,
    ) {
        meterRegistry
            ?.counter("kotbusta_kindle_send_total", "outcome", status.name.lowercase())
            ?.increment()
        transactionProvider.transaction(READ_WRITE) {
            updateQueueItemStatus(queueId, status, error)
            createKindleSendEvent(queueId, status.name, detailsJson)
        }
    }

    private fun calculateNextRunTime(attempts: Int): Instant {
        // Exponential backoff: 2^attempts minutes, with ±20% jitter.
        val baseDelayMinutes = 2.0.pow(attempts.toDouble()).toLong()
        val jitterFactor = 1.0 + Random.nextDouble(-0.2, 0.2)
        val delayMinutes = (baseDelayMinutes * jitterFactor).toLong()
        return Clock.System.now() + delayMinutes.minutes
    }

    private companion object : Logger()
}
