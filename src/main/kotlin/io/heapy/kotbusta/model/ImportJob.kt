package io.heapy.kotbusta.model

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
    val booksAdded: Int,
    val bookErrors: Int,
    val bookDeleted: Int,
    val coversAdded: Int,
    val coverErrors: Int,
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
    val booksAdded = AtomicInt(0)
    val booksDeleted = AtomicInt(0)
    val bookErrors = AtomicInt(0)
    val coversAdded = AtomicInt(0)
    val coverErrors = AtomicInt(0)

    fun addMessage(msg: String) {
        message[Clock.System.now()] = msg
    }

    fun incInpFiles() {
        inpFilesProcessed.addAndFetch(1)
    }

    fun incDeletedBooks() {
        booksDeleted.addAndFetch(1)
    }

    fun incAddedBooks() {
        booksAdded.addAndFetch(1)
    }

    fun incInvalidBooks() {
        bookErrors.addAndFetch(1)
    }

    fun incAddedCovers() {
        coversAdded.addAndFetch(1)
    }

    fun incInvalidCovers() {
        coverErrors.addAndFetch(1)
    }
}

fun ImportStats.toImportJob() = ImportJob(
    status = status.load(),
    messages = message.toMap(),
    inpFilesProcessed = inpFilesProcessed.load(),
    booksAdded = booksAdded.load(),
    bookErrors = bookErrors.load(),
    bookDeleted = booksDeleted.load(),
    coversAdded = coversAdded.load(),
    coverErrors = coverErrors.load(),
    startedAt = startedAt.load(),
    completedAt = completedAt.load(),
)
