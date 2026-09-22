package io.heapy.kotbusta.worker

import io.heapy.kotbusta.dao.createKindleSendEvent
import io.heapy.kotbusta.dao.findKindleDeviceByIdAndUserId
import io.heapy.kotbusta.dao.findPendingQueueItems
import io.heapy.kotbusta.dao.findQueueItemById
import io.heapy.kotbusta.dao.getBookById
import io.heapy.kotbusta.dao.incrementQueueItemAttempts
import io.heapy.kotbusta.dao.markQueueItemAsProcessing
import io.heapy.kotbusta.dao.resetStuckProcessingItems
import io.heapy.kotbusta.dao.updateQueueItemStatus
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.model.KindleSendStatus.COMPLETED
import io.heapy.kotbusta.model.KindleSendStatus.FAILED
import io.heapy.kotbusta.model.KindleSendStatus.PENDING
import io.heapy.kotbusta.model.KindleSendStatus.PROCESSING
import io.heapy.kotbusta.service.BookFileService
import io.heapy.kotbusta.worker.KindleJobLoad.Broken
import io.heapy.kotbusta.worker.KindleJobLoad.Ready
import io.heapy.kotbusta.worker.KindleJobLoad.Vanished
import io.heapy.kotbusta.worker.KindleSendOutcome.Failed
import io.heapy.kotbusta.worker.KindleSendOutcome.Sent
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

class CatalogKindleQueue(
    private val bookFileService: BookFileService,
) : KindleQueue {
    override val name = "catalog"

    context(_: TransactionContext)
    override fun resetStuckItems(cutoff: Instant): Int =
        resetStuckProcessingItems(cutoff)

    context(_: TransactionContext)
    override fun claimPending(batchSize: Int): List<ClaimedKindleItem> =
        findPendingQueueItems(batchSize).mapNotNull { item ->
            val id = item.id!!
            if (markQueueItemAsProcessing(id)) {
                val _ = createKindleSendEvent(id, PROCESSING.name)
                ClaimedKindleItem(id = id, attempts = item.attempts)
            } else {
                null
            }
        }

    context(_: TransactionContext)
    override fun loadJob(id: Int): KindleJobLoad {
        val item = findQueueItemById(id)
            ?: return Vanished
        val device = findKindleDeviceByIdAndUserId(item.deviceId, item.userId)
            ?: return Broken(Failed("Device not found", "Device not found"))
        val book = getBookById(item.bookId)
            ?: return Broken(Failed("Book not available", "Book not found or no longer available"))

        return Ready(
            KindleSendJob(
                id = id,
                attempts = item.attempts,
                title = book.title,
                recipientEmail = device.email,
                materialize = {
                    try {
                        bookFileService.materialize(book, item.format)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // An unmounted books volume and a broken archive entry look
                        // the same here. Retrying rides out the first and only
                        // delays the second, instead of failing every pending send
                        // in one tick.
                        throw TransientMaterializeException(
                            "Could not prepare book ${book.id}: ${e.message}",
                            e,
                        )
                    }
                },
            ),
        )
    }

    context(_: TransactionContext)
    override fun recordTerminal(
        id: Int,
        outcome: KindleSendOutcome,
    ): (() -> Unit)? {
        when (outcome) {
            is Sent -> {
                val _ = updateQueueItemStatus(id, COMPLETED, null)
                val _ = createKindleSendEvent(
                    id,
                    COMPLETED.name,
                    Json.encodeToString(SentEventDetails(messageId = outcome.messageId)),
                )
            }

            is Failed -> {
                val _ = updateQueueItemStatus(id, FAILED, outcome.statusError)
                val _ = createKindleSendEvent(
                    id,
                    FAILED.name,
                    Json.encodeToString(FailedEventDetails(reason = outcome.reason, error = outcome.error)),
                )
            }
        }
        return null
    }

    context(_: TransactionContext)
    override fun recordRetry(
        id: Int,
        attempt: Int,
        nextRunAt: Instant,
        error: String,
    ) {
        val _ = incrementQueueItemAttempts(id, nextRunAt)
        val _ = updateQueueItemStatus(id, PENDING, error)
        val _ = createKindleSendEvent(
            id,
            PENDING.name,
            Json.encodeToString(
                RetryEventDetails(
                    attempt = attempt,
                    nextRunAt = nextRunAt.toString(),
                    error = error,
                ),
            ),
        )
    }
}
