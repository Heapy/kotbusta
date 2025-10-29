package io.heapy.kotbusta.model

import kotlinx.serialization.Serializable
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.time.Instant

@Serializable
data class ImportJob(
    val id: Int,
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
    val startedAt: Instant,
    val completedAt: Instant? = null,
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
    fun incInpFiles() = inpFilesProcessed.fetchAndIncrement()
    fun incBookAdded() = booksAdded.fetchAndIncrement()
    fun incBooksUpdated() = booksUpdated.fetchAndIncrement()
    fun incInvalidBookId() = booksDeleted.fetchAndIncrement()
    fun incBooksDeleted() = booksDeleted.fetchAndIncrement()
    fun incInvalidInpLine() = bookErrors.fetchAndIncrement()
    fun incBookNoAuthors() = bookErrors.fetchAndIncrement()
    fun incBookErrors() = bookErrors.fetchAndIncrement()
    fun incCoversAdded() = coversAdded.fetchAndIncrement()
    fun incCoverErrors() = coverErrors.fetchAndIncrement()
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
