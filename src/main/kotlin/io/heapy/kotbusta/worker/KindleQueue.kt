package io.heapy.kotbusta.worker

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.service.MaterializedBook
import kotlin.time.Instant

class ClaimedKindleItem(
    val id: Int,
    val attempts: Int,
)

class KindleSendJob(
    val id: Int,
    val attempts: Int,
    val title: String,
    val recipientEmail: String,
    val materialize: suspend () -> MaterializedBook,
)

sealed interface KindleJobLoad {
    data object Vanished : KindleJobLoad

    /** The source is gone for good; the item fails now. */
    data class Broken(val failure: KindleSendOutcome.Failed) : KindleJobLoad

    /** The source may come back, for example a volume that is not mounted yet. */
    data class Deferred(val attempts: Int, val error: String) : KindleJobLoad

    class Ready(val job: KindleSendJob) : KindleJobLoad
}

/**
 * Thrown by [KindleQueue.materialize] when the attachment could not be produced
 * for a reason that may pass: the worker retries such an item instead of failing
 * it, which matters when failing it would delete the only copy of a user's file.
 */
class TransientMaterializeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

sealed interface KindleSendOutcome {
    data class Sent(val messageId: String) : KindleSendOutcome

    data class Failed(
        val statusError: String,
        val reason: String,
        val error: String? = null,
    ) : KindleSendOutcome
}

/**
 * One queue of pending Kindle sends. [KindleSendWorker] owns the drain loop,
 * retry backoff and metrics; an implementation owns where the rows live, how the
 * attachment is produced, and what has to be released once a row is done.
 */
interface KindleQueue {
    val name: String

    context(_: TransactionContext)
    fun resetStuckItems(cutoff: Instant): Int

    context(_: TransactionContext)
    fun claimPending(batchSize: Int): List<ClaimedKindleItem>

    context(_: TransactionContext)
    fun loadJob(id: Int): KindleJobLoad

    /**
     * Writes the terminal status. Returns work that must run only once the
     * transaction has committed — deleting the uploaded file — or null when the
     * queue owns no file.
     */
    context(_: TransactionContext)
    fun recordTerminal(id: Int, outcome: KindleSendOutcome): (() -> Unit)?

    context(_: TransactionContext)
    fun recordRetry(id: Int, attempt: Int, nextRunAt: Instant, error: String)
}
