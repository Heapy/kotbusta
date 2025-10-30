package io.heapy.kotbusta.worker

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.dao.createKindleSendEvent
import io.heapy.kotbusta.dao.findKindleDeviceByIdAndUserId
import io.heapy.kotbusta.dao.findPendingQueueItems
import io.heapy.kotbusta.dao.findQueueItemById
import io.heapy.kotbusta.dao.incrementQueueItemAttempts
import io.heapy.kotbusta.dao.markQueueItemAsProcessing
import io.heapy.kotbusta.dao.updateQueueItemStatus
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType
import io.heapy.kotbusta.model.KindleSendStatus
import io.heapy.kotbusta.service.EmailResult
import io.heapy.kotbusta.service.EmailService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class KindleSendWorker(
    private val emailService: EmailService,
    private val transactionProvider: TransactionProvider,
    private val batchSize: Int,
    private val maxRetries: Int,
    private val getBookFile: (String, String) -> File,
) {
    private var job: Job? = null

    fun start(
        scope: CoroutineScope,
        intervalMillis: Long = 30_000,
    ) {
        job = scope.launch {
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

    suspend fun processQueue() {
        transactionProvider.transaction(TransactionType.READ_WRITE) {
            val pendingItems = findPendingQueueItems(batchSize)

            if (pendingItems.isEmpty()) {
                log.debug("No pending items in Kindle send queue")
                return@transaction
            }

            log.info("Processing ${pendingItems.size} pending items in Kindle send queue")

            for (item in pendingItems) {
                try {
                    processQueueItem(item.id!!)
                } catch (e: Exception) {
                    log.error("Failed to process queue item ${item.id}", e)
                }
            }
        }
    }

    context(_: TransactionContext)
    private suspend fun processQueueItem(queueId: Int) {
        // Mark as processing with optimistic locking
        val marked = markQueueItemAsProcessing(queueId)
        if (!marked) {
            log.debug("Queue item $queueId already being processed")
            return
        }

        createKindleSendEvent(queueId, KindleSendStatus.PROCESSING.name)

        // Fetch the specific queue item that was just locked
        val queueItem = findQueueItemById(queueId)
        if (queueItem == null) {
            log.warn("Queue item $queueId not found after marking as processing")
            return
        }

        val deviceId = queueItem.deviceId
        val userId = queueItem.userId
        val bookId = queueItem.bookId
        val format = queueItem.format

        // Fetch device, book info (we need to join these manually or add a query)
        val device = findKindleDeviceByIdAndUserId(deviceId, userId)

        if (device == null) {
            log.warn("Device $deviceId not found for queue item $queueId")
            updateQueueItemStatus(
                queueId,
                KindleSendStatus.FAILED,
                "Device not found",
            )
            createKindleSendEvent(
                queueId,
                KindleSendStatus.FAILED.name,
                Json.encodeToString(FailedEventDetails(reason = "Device not found")),
            )
            return
        }

        // Get book info from JOOQ directly for file path
        val bookFile = try {
            getBookFile(bookId.toString(), format)
        } catch (e: Exception) {
            log.error("Failed to resolve book file for queue item $queueId", e)
            updateQueueItemStatus(
                queueId,
                KindleSendStatus.FAILED,
                "Failed to resolve book file: ${e.message}",
            )
            createKindleSendEvent(
                queueId,
                KindleSendStatus.FAILED.name,
                Json.encodeToString(
                    FailedEventDetails(
                        reason = "Book file not found",
                        error = e.message,
                    ),
                ),
            )
            return
        }

        if (!bookFile.exists()) {
            log.warn("Book file not found: ${bookFile.absolutePath}")
            updateQueueItemStatus(
                queueId,
                KindleSendStatus.FAILED,
                "Book file not found",
            )
            createKindleSendEvent(
                queueId,
                KindleSendStatus.FAILED.name,
                Json.encodeToString(FailedEventDetails(reason = "Book file not found")),
            )
            return
        }

        // Send email via SES
        val result = emailService.sendBookToKindle(
            recipientEmail = device.email,
            bookFile = bookFile,
            bookTitle = "Book #$bookId", // TODO: fetch actual title
            format = format,
        )

        when (result) {
            is EmailResult.Success -> {
                updateQueueItemStatus(
                    queueId,
                    KindleSendStatus.COMPLETED,
                )
                createKindleSendEvent(
                    queueId,
                    KindleSendStatus.COMPLETED.name,
                    Json.encodeToString(SentEventDetails(messageId = result.messageId)),
                )
                log.info("Successfully sent book $bookId to device $deviceId")
            }

            is EmailResult.RetryableFailure -> {
                val attempts = queueItem.attempts + 1
                if (attempts < maxRetries) {
                    val nextRunAt = calculateNextRunTime(attempts)
                    incrementQueueItemAttempts(queueId, nextRunAt)
                    updateQueueItemStatus(
                        queueId,
                        KindleSendStatus.PENDING,
                        result.error,
                    )
                    createKindleSendEvent(
                        queueId,
                        KindleSendStatus.PENDING.name,
                        Json.encodeToString(
                            RetryEventDetails(
                                attempt = attempts,
                                nextRunAt = nextRunAt.toString(),
                                error = result.error,
                            ),
                        ),
                    )
                    log.warn("Retryable failure for queue item $queueId, attempt $attempts: ${result.error}")
                } else {
                    updateQueueItemStatus(
                        queueId,
                        KindleSendStatus.FAILED,
                        "Max retries exceeded: ${result.error}",
                    )
                    createKindleSendEvent(
                        queueId,
                        KindleSendStatus.FAILED.name,
                        Json.encodeToString(
                            FailedEventDetails(
                                reason = "Max retries exceeded",
                                error = result.error,
                            ),
                        ),
                    )
                    log.error("Max retries exceeded for queue item $queueId: ${result.error}")
                }
            }

            is EmailResult.PermanentFailure -> {
                updateQueueItemStatus(
                    queueId,
                    KindleSendStatus.FAILED,
                    result.error,
                )
                createKindleSendEvent(
                    queueId,
                    KindleSendStatus.FAILED.name,
                    Json.encodeToString(
                        FailedEventDetails(
                            reason = "Permanent failure",
                            error = result.error,
                        ),
                    ),
                )
                log.error("Permanent failure for queue item $queueId: ${result.error}")
            }
        }
    }

    private fun calculateNextRunTime(attempts: Int): kotlin.time.Instant {
        // Exponential backoff: 2^attempts minutes
        val baseDelayMinutes = 2.0.pow(attempts.toDouble()).toLong()

        // Add jitter: ±20%
        val jitterFactor = 1.0 + (Random.nextDouble(-0.2, 0.2))
        val delayMinutes = (baseDelayMinutes * jitterFactor).toLong()

        return Clock.System.now() + delayMinutes.minutes
    }

    private companion object : Logger()
}
