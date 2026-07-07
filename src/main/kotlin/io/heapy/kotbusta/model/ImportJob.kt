package io.heapy.kotbusta.model

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.model.ImportJob.JobStatus
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class ImportJob(
    val status: JobStatus,
    val messages: Map<Instant, String>,
    val inpFilesProcessed: Int,
    val inpFilesTotal: Int,
    val currentInpFile: String?,
    val booksAdded: Int,
    val bookErrors: Int,
    val sequentialBookErrors: Int,
    val bookDeleted: Int,
    val startedAt: Instant,
    val completedAt: Instant? = null,
) {
    enum class JobStatus {
        IDLE,
        RUNNING,
        COMPLETED,
        FAILED,
    }
}

class ImportStats {
    val status = AtomicReference(JobStatus.IDLE)
    val message = ConcurrentHashMap<Instant, String>()
    val startedAt = AtomicReference(Clock.System.now())
    val completedAt = AtomicReference<Instant?>(null)
    val inpFilesProcessed = AtomicInt(0)
    val inpFilesTotal = AtomicInt(0)
    val currentInpFile = AtomicReference<String?>(null)
    val booksAdded = AtomicInt(0)
    val booksDeleted = AtomicInt(0)
    val bookErrors = AtomicInt(0)
    val sequentialBookErrors = AtomicInt(0)

    fun addMessage(msg: String, exception: Exception? = null) {
        if (exception != null) {
            log.error(msg, exception)
        } else {
            log.info(msg)
        }

        message[Clock.System.now()] = msg
    }

    fun incInpFiles() {
        inpFilesProcessed.addAndFetch(1)
    }

    fun setInpFilesTotal(total: Int) {
        inpFilesTotal.store(total)
    }

    fun setCurrentInpFile(fileName: String?) {
        currentInpFile.store(fileName)
    }

    fun incDeletedBooks() {
        booksDeleted.addAndFetch(1)
    }

    fun incAddedBooks() {
        booksAdded.addAndFetch(1)
    }

    fun incInvalidBooks(): Int {
        bookErrors.addAndFetch(1)
        return sequentialBookErrors.addAndFetch(1)
    }

    fun resetSequentialBookErrors() {
        sequentialBookErrors.store(0)
    }

    companion object : Logger() {
        const val MAX_SEQUENTIAL_BOOK_ERRORS = 100
    }
}

fun ImportStats.toImportJob() = ImportJob(
    status = status.load(),
    messages = message.toMap(),
    inpFilesProcessed = inpFilesProcessed.load(),
    inpFilesTotal = inpFilesTotal.load(),
    currentInpFile = currentInpFile.load(),
    booksAdded = booksAdded.load(),
    bookErrors = bookErrors.load(),
    sequentialBookErrors = sequentialBookErrors.load(),
    bookDeleted = booksDeleted.load(),
    startedAt = startedAt.load(),
    completedAt = completedAt.load(),
)
