package io.heapy.kotbusta.worker

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.model.KindleSendStatus
import io.heapy.kotbusta.model.KindleSendStatus.COMPLETED
import io.heapy.kotbusta.model.KindleSendStatus.FAILED
import io.heapy.kotbusta.service.EmailResult
import io.heapy.kotbusta.service.EmailService
import io.heapy.kotbusta.worker.KindleJobLoad.Broken
import io.heapy.kotbusta.worker.KindleJobLoad.Deferred
import io.heapy.kotbusta.worker.KindleJobLoad.Ready
import io.heapy.kotbusta.worker.KindleJobLoad.Vanished
import io.heapy.kotbusta.worker.KindleSendOutcome.Failed
import io.heapy.kotbusta.worker.KindleSendOutcome.Sent
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * Drains every [KindleQueue]. Each tick claims a batch per queue (a short RW
 * transaction that flips items PENDING -> PROCESSING), then for each claimed item
 * does the slow work — materialize the attachment and call SES — **outside** any
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
    private val queues: List<KindleQueue>,
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
        queues.forEach { queue ->
            val reset = transactionProvider.transaction(READ_WRITE) {
                queue.resetStuckItems(cutoff)
            }
            if (reset > 0) {
                log.warn("Recovered $reset ${queue.name} queue item(s) stuck in PROCESSING back to PENDING")
            }
        }
    }

    suspend fun processQueue() {
        // Recover defensively in case this process itself has been running long
        // enough to strand an item between claim and outcome.
        recoverStuckItems()

        queues.forEach { queue -> drain(queue) }
    }

    private suspend fun drain(queue: KindleQueue) {
        // 1) Claim a batch atomically. No suspending work happens in this tx.
        val claimed = transactionProvider.transaction(READ_WRITE) {
            queue.claimPending(batchSize)
        }

        if (claimed.isEmpty()) {
            log.debug("No pending items in ${queue.name} Kindle queue")
            return
        }
        log.info("Processing ${claimed.size} claimed ${queue.name} Kindle item(s)")

        // 2) Process each claimed item outside of any DB transaction.
        for (item in claimed) {
            try {
                processClaimedItem(queue, item.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An error here says nothing about the item itself — a busy
                // database looks the same as a bug — and failing an item can
                // delete a user's uploaded file, so this is a retry.
                log.error("Failed to process ${queue.name} queue item ${item.id}", e)
                try {
                    handleRetryableFailure(
                        queue,
                        item.id,
                        item.attempts,
                        "Unexpected error: ${e.message}",
                    )
                } catch (secondary: Exception) {
                    // Recording the retry needs the database too, and the error
                    // above is often the database itself. Leaving the item in
                    // PROCESSING is safe — recoverStuckItems returns it to
                    // PENDING — and the rest of the batch still gets its turn.
                    log.error("Could not record a retry for ${queue.name} queue item ${item.id}", secondary)
                }
            }
        }
    }

    private suspend fun processClaimedItem(
        queue: KindleQueue,
        queueId: Int,
    ) {
        val load = transactionProvider.transaction(READ_ONLY) {
            queue.loadJob(queueId)
        }

        when (load) {
            is Vanished ->
                log.warn("${queue.name} queue item $queueId not found after claiming")

            is Broken -> {
                log.warn("${queue.name} queue item $queueId cannot be sent: ${load.failure.reason}")
                recordTerminal(queue, queueId, load.failure)
            }

            is Deferred ->
                handleRetryableFailure(queue, queueId, load.attempts, load.error)

            is Ready -> send(queue, load.job)
        }
    }

    private suspend fun send(
        queue: KindleQueue,
        job: KindleSendJob,
    ) {
        val materialized = try {
            job.materialize()
        } catch (e: CancellationException) {
            // Shutdown. Leaving the item in PROCESSING lets recoverStuckItems
            // return it to PENDING; failing it here would delete an upload.
            throw e
        } catch (e: TransientMaterializeException) {
            log.warn("Could not prepare ${queue.name} queue item ${job.id} yet", e)
            handleRetryableFailure(
                queue,
                job.id,
                job.attempts,
                e.message ?: "Failed to prepare book file",
            )
            return
        } catch (e: Exception) {
            log.error("Failed to prepare file for ${queue.name} queue item ${job.id}", e)
            recordTerminal(
                queue,
                job.id,
                Failed(
                    statusError = "Failed to prepare book file: ${e.message}",
                    reason = "Book file not available",
                    error = e.message,
                ),
            )
            return
        }

        val result = try {
            emailService.sendBookToKindle(
                recipientEmail = job.recipientEmail,
                bookFile = materialized.file,
                bookTitle = job.title,
                attachmentFileName = materialized.fileName,
            )
        } finally {
            materialized.cleanup()
        }

        when (result) {
            is EmailResult.Success -> {
                recordSent(queue, job, result.messageId)
                log.info("Successfully sent ${queue.name} queue item ${job.id}")
            }

            is EmailResult.RetryableFailure ->
                handleRetryableFailure(queue, job.id, job.attempts, result.error)

            is EmailResult.PermanentFailure -> {
                recordTerminal(
                    queue,
                    job.id,
                    Failed(
                        statusError = result.error,
                        reason = "Permanent failure",
                        error = result.error,
                    ),
                )
                log.error("Permanent failure for ${queue.name} queue item ${job.id}: ${result.error}")
            }
        }
    }

    /**
     * Writes the outcome of a send that already left for SES. The failure of that
     * write must not reach the retry path, because re-queuing the item would
     * deliver the same book again, so it is retried here instead.
     *
     * If every attempt fails the item stays in PROCESSING and [recoverStuckItems]
     * returns it to PENDING after [stuckProcessingTimeout], which does send the
     * book a second time. That window is the price of SES having no de-duplication
     * and the send status living in the database that just failed.
     */
    private suspend fun recordSent(
        queue: KindleQueue,
        job: KindleSendJob,
        messageId: String,
    ) {
        repeat(TERMINAL_WRITE_ATTEMPTS) { attempt ->
            try {
                recordTerminal(queue, job.id, Sent(messageId = messageId))
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error(
                    "Sent ${queue.name} queue item ${job.id} (messageId $messageId) " +
                        "but could not record it, attempt ${attempt + 1}",
                    e,
                )
                if (attempt < TERMINAL_WRITE_ATTEMPTS - 1) {
                    delay(TERMINAL_WRITE_RETRY_DELAY_MS)
                }
            }
        }
    }

    private suspend fun handleRetryableFailure(
        queue: KindleQueue,
        queueId: Int,
        currentAttempts: Int,
        error: String,
    ) {
        val attempts = currentAttempts + 1
        if (attempts < maxRetries) {
            val nextRunAt = calculateNextRunTime(attempts)
            transactionProvider.transaction(READ_WRITE) {
                queue.recordRetry(queueId, attempts, nextRunAt, error)
            }
            log.warn("Retryable failure for ${queue.name} queue item $queueId, attempt $attempts: $error")
        } else {
            recordTerminal(
                queue,
                queueId,
                Failed(
                    statusError = "Max retries exceeded: $error",
                    reason = "Max retries exceeded",
                    error = error,
                ),
            )
            log.error("Max retries exceeded for ${queue.name} queue item $queueId: $error")
        }
    }

    private suspend fun recordTerminal(
        queue: KindleQueue,
        queueId: Int,
        outcome: KindleSendOutcome,
    ) {
        val status = when (outcome) {
            is Sent -> COMPLETED
            is Failed -> FAILED
        }

        // Counted only once the status is committed: a failed write is retried by
        // the caller, which would otherwise count the same item twice.
        val afterCommit = transactionProvider.transaction(READ_WRITE) {
            queue.recordTerminal(queueId, outcome)
        }
        countOutcome(queue, status)
        afterCommit?.invoke()
    }

    private fun countOutcome(
        queue: KindleQueue,
        status: KindleSendStatus,
    ) {
        meterRegistry
            ?.counter(
                "kotbusta_kindle_send_total",
                "outcome",
                status.name.lowercase(),
                "queue",
                queue.name,
            )
            ?.increment()
    }

    private fun calculateNextRunTime(attempts: Int): Instant {
        // Exponential backoff: 2^attempts minutes, with ±20% jitter.
        val baseDelayMinutes = 2.0.pow(attempts.toDouble()).toLong()
        val jitterFactor = 1.0 + Random.nextDouble(-0.2, 0.2)
        val delayMinutes = (baseDelayMinutes * jitterFactor).toLong()
        return Clock.System.now() + delayMinutes.minutes
    }

    private companion object : Logger() {
        // The write only has to outlast a busy writer lock, which the JDBC
        // busy_timeout already caps at 5 seconds.
        private const val TERMINAL_WRITE_ATTEMPTS = 3
        private const val TERMINAL_WRITE_RETRY_DELAY_MS = 2_000L
    }
}
