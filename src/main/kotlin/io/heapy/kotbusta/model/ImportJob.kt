package io.heapy.kotbusta.model

import kotlinx.serialization.Serializable
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.fetchAndIncrement

@Serializable
data class ImportJob(
    val id: Long,
    val jobType: JobType,
    val status: JobStatus,
    val progress: String?,
    val inpFilesProcessed: Int = 0,
    val booksAdded: Int = 0,
    val booksUpdated: Int = 0,
    val booksDeleted: Int = 0,
    val coversAdded: Int = 0,
    val bookErrors: Int = 0,
    val coverErrors: Int = 0,
    val errorMessage: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null,
)

data class ImportStats(
    val inpFilesProcessed: AtomicInt = AtomicInt(0),
    val booksAdded: AtomicInt = AtomicInt(0),
    val booksUpdated: AtomicInt = AtomicInt(0),
    val booksDeleted: AtomicInt = AtomicInt(0),
    val coversAdded: AtomicInt = AtomicInt(0),
    val bookErrors: AtomicInt = AtomicInt(0),
    val coverErrors: AtomicInt = AtomicInt(0),
) {
    fun incrementInpFiles() = inpFilesProcessed.fetchAndIncrement()
    fun incrementBooksAdded() = booksAdded.fetchAndIncrement()
    fun incrementBooksUpdated() = booksUpdated.fetchAndIncrement()
    fun incrementBooksDeleted() = booksDeleted.fetchAndIncrement()
    fun incrementCoversAdded() = coversAdded.fetchAndIncrement()
    fun incrementBookErrors() = bookErrors.fetchAndIncrement()
    fun incrementCoverErrors() = coverErrors.fetchAndIncrement()
}

enum class JobStatus {
    RUNNING,
    COMPLETED,
    FAILED;
}

enum class JobType {
    DATA_IMPORT,
    COVER_EXTRACTION;
}
