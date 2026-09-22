package io.heapy.kotbusta.worker

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.dao.findReferencedUploadPaths
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.service.KindleUploadStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Deletes upload files that no queue row refers to. A crash between writing the
 * file and committing its row leaves such a file behind, and deleting after the
 * commit means a crash there leaves one too. It runs on a schedule rather than
 * only at startup, because [minimumAge] makes any single pass skip the files that
 * have just leaked.
 */
class KindleUploadSweeper(
    private val transactionProvider: TransactionProvider,
    private val storage: KindleUploadStorage,
    private val minimumAge: Duration = 1.hours,
    private val partialMinimumAge: Duration = 24.hours,
) {
    private var job: Job? = null

    fun start(
        scope: CoroutineScope,
        interval: Duration = 1.hours,
    ) {
        job = scope.launch {
            while (isActive) {
                try {
                    val _ = sweep()
                } catch (e: Exception) {
                    log.error("Error while sweeping Kindle uploads", e)
                }
                delay(interval)
            }
        }
        log.info("Kindle upload sweeper started with interval $interval")
    }

    fun stop() {
        job?.cancel()
        log.info("Kindle upload sweeper stopped")
    }

    suspend fun sweep(): Int {
        val referenced = transactionProvider.transaction(READ_ONLY) {
            findReferencedUploadPaths()
        }
        val now = Clock.System.now()
        return storage.sweepOrphans(
            referenced = referenced,
            olderThan = now - minimumAge,
            partialsOlderThan = now - partialMinimumAge,
        )
    }

    private companion object : Logger()
}
