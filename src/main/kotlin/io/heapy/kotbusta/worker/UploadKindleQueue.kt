package io.heapy.kotbusta.worker

import io.heapy.kotbusta.dao.clearUploadItemStoredPath
import io.heapy.kotbusta.dao.findKindleDeviceByIdAndUserId
import io.heapy.kotbusta.dao.findPendingUploadItems
import io.heapy.kotbusta.dao.findUploadItemById
import io.heapy.kotbusta.dao.incrementUploadItemAttempts
import io.heapy.kotbusta.dao.markUploadItemAsProcessing
import io.heapy.kotbusta.dao.resetStuckProcessingUploadItems
import io.heapy.kotbusta.dao.updateUploadItemStatus
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.jooq.tables.records.KindleUploadQueueRecord
import io.heapy.kotbusta.model.KindleSendStatus.COMPLETED
import io.heapy.kotbusta.model.KindleSendStatus.FAILED
import io.heapy.kotbusta.model.KindleSendStatus.PENDING
import io.heapy.kotbusta.model.KindleUploadSourceFormat
import io.heapy.kotbusta.service.ConversionService
import io.heapy.kotbusta.service.KindleUploadStorage
import io.heapy.kotbusta.service.MaterializedBook
import io.heapy.kotbusta.worker.KindleJobLoad.Broken
import io.heapy.kotbusta.worker.KindleJobLoad.Deferred
import io.heapy.kotbusta.worker.KindleJobLoad.Ready
import io.heapy.kotbusta.worker.KindleJobLoad.Vanished
import io.heapy.kotbusta.worker.KindleSendOutcome.Failed
import io.heapy.kotbusta.worker.KindleSendOutcome.Sent
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import java.nio.file.Files
import kotlin.time.Instant

class UploadKindleQueue(
    private val storage: KindleUploadStorage,
    private val conversionService: ConversionService,
) : KindleQueue {
    override val name = "upload"

    context(_: TransactionContext)
    override fun resetStuckItems(cutoff: Instant): Int =
        resetStuckProcessingUploadItems(cutoff)

    context(_: TransactionContext)
    override fun claimPending(batchSize: Int): List<ClaimedKindleItem> =
        findPendingUploadItems(batchSize).mapNotNull { item ->
            val id = item.id!!
            if (markUploadItemAsProcessing(id)) {
                ClaimedKindleItem(id = id, attempts = item.attempts)
            } else {
                null
            }
        }

    context(_: TransactionContext)
    override fun loadJob(id: Int): KindleJobLoad {
        val item = findUploadItemById(id)
            ?: return Vanished
        val device = findKindleDeviceByIdAndUserId(item.deviceId, item.userId)
            ?: return Broken(Failed("Device not found", "Device not found"))
        val storedPath = item.storedPath
            ?: return Broken(Failed("Uploaded file is no longer available", "Uploaded file released"))

        val file = storage.resolve(storedPath)
        if (!file.isFile) {
            // The row still owns a file, so the storage is at fault rather than the
            // upload: a volume mounted a moment late would otherwise fail every
            // queued upload at once. The retries span about half an hour; a longer
            // outage still ends in a failed item.
            return Deferred(item.attempts, "Uploaded file is not readable: $storedPath")
        }

        return Ready(
            KindleSendJob(
                id = id,
                attempts = item.attempts,
                title = item.fileName,
                recipientEmail = device.email,
                materialize = { materialize(item, file) },
            ),
        )
    }

    context(_: TransactionContext)
    override fun recordTerminal(
        id: Int,
        outcome: KindleSendOutcome,
    ): (() -> Unit)? {
        val storedPath = findUploadItemById(id)?.storedPath

        when (outcome) {
            is Sent -> {
                val _ = updateUploadItemStatus(id, COMPLETED, null)
            }

            is Failed -> {
                val _ = updateUploadItemStatus(id, FAILED, outcome.statusError)
            }
        }
        val _ = clearUploadItemStoredPath(id)

        return storedPath?.let { path -> { storage.delete(path) } }
    }

    context(_: TransactionContext)
    override fun recordRetry(
        id: Int,
        attempt: Int,
        nextRunAt: Instant,
        error: String,
    ) {
        val _ = incrementUploadItemAttempts(id, nextRunAt)
        val _ = updateUploadItemStatus(id, PENDING, error)
    }

    private suspend fun materialize(
        item: KindleUploadQueueRecord,
        file: File,
    ): MaterializedBook {
        val sourceFormat = KindleUploadSourceFormat.valueOf(item.sourceFormat)
        val baseName = item.fileName.substringBeforeLast('.', item.fileName)

        // Failing an upload deletes the only copy the user has, so every failure
        // here is reported as transient and the worker retries it. A file that is
        // really broken still ends up failed, after the retries run out.
        val tempDir = try {
            Files.createTempDirectory("kotbusta-upload-").toFile()
        } catch (e: IOException) {
            throw TransientMaterializeException("No temporary directory available: ${e.message}", e)
        }

        return try {
            when (sourceFormat) {
                KindleUploadSourceFormat.FB2 -> convertToEpub(file, baseName, tempDir)

                KindleUploadSourceFormat.EPUB,
                KindleUploadSourceFormat.PDF,
                    ->
                    // The stored upload is sent as it is, and must survive
                    // cleanup() so a retry can read it again: only the (empty)
                    // temp directory is handed over as owned.
                    MaterializedBook(
                        file = file,
                        fileName = item.fileName,
                        format = sourceFormat.extension,
                        tempDir = tempDir,
                    )
            }
        } catch (e: CancellationException) {
            // Shutdown, not a problem with this item.
            val _ = tempDir.deleteRecursively()
            throw e
        } catch (e: TransientMaterializeException) {
            val _ = tempDir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            val _ = tempDir.deleteRecursively()
            throw TransientMaterializeException("Could not prepare the uploaded file: ${e.message}", e)
        } catch (e: Throwable) {
            val _ = tempDir.deleteRecursively()
            throw e
        }
    }

    private suspend fun convertToEpub(
        file: File,
        baseName: String,
        tempDir: File,
    ): MaterializedBook {
        // Pandoc runs with its input file's directory as the working directory and
        // writes extracted media and its own log there, so it must never be given
        // the upload directory: it would fill it with files no sweep reclaims.
        val input = File(tempDir, "source.fb2")
        val outputFile = File(tempDir, "$baseName.epub")

        val result = try {
            val _ = file.copyTo(input, overwrite = true)
            conversionService.convertFb2(
                inputFile = input,
                outputFormat = "epub",
                outputFile = outputFile,
            )
        } catch (e: IOException) {
            throw TransientMaterializeException("Could not read the uploaded file: ${e.message}", e)
        }

        if (!result.success || result.outputFile == null) {
            // A missing pandoc and a conversion timeout look the same as a broken
            // file here, and failing an upload deletes it, so this is a retry.
            throw TransientMaterializeException("Conversion to epub failed: ${result.errorMessage}")
        }

        return MaterializedBook(
            file = result.outputFile,
            fileName = "$baseName.epub",
            format = "epub",
            tempDir = tempDir,
        )
    }
}
