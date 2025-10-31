package io.heapy.kotbusta.service

import io.heapy.kotbusta.model.ImportJob
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.model.JobStatus
import io.heapy.kotbusta.model.JobType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory storage for job statistics and progress tracking.
 * Thread-safe implementation using concurrent data structures.
 */
class JobStatsService {
    private val jobs = ConcurrentHashMap<Int, ImportJob>()
    private val jobIdCounter = AtomicInteger(0)

    /**
     * Creates a new job and returns its ID
     */
    fun createJob(jobType: JobType): Int {
        val jobId = jobIdCounter.incrementAndGet()
        val now = Clock.System.now()

        val job = ImportJob(
            id = jobId,
            jobType = jobType,
            status = JobStatus.RUNNING,
            progress = null,
            inpFilesProcessed = 0,
            booksAdded = 0,
            booksUpdated = 0,
            booksDeleted = 0,
            coversAdded = 0,
            bookErrors = 0,
            coverErrors = 0,
            errorMessage = null,
            startedAt = now,
            completedAt = null
        )

        jobs[jobId] = job
        return jobId
    }

    /**
     * Updates job progress message
     */
    fun updateProgress(jobId: Int, progress: String) {
        jobs.computeIfPresent(jobId) { _, job ->
            job.copy(progress = progress)
        }
    }

    /**
     * Updates job statistics from ImportStats
     */
    fun updateStats(jobId: Int, stats: ImportStats) {
        jobs.computeIfPresent(jobId) { _, job ->
            job.copy(
                inpFilesProcessed = stats.inpFilesProcessed.load(),
                booksAdded = stats.booksAdded.load(),
                booksUpdated = stats.booksUpdated.load(),
                booksDeleted = stats.booksDeleted.load(),
                coversAdded = stats.coversAdded.load(),
                bookErrors = stats.bookErrors.load(),
                coverErrors = stats.coverErrors.load()
            )
        }
    }

    /**
     * Marks job as completed
     */
    fun completeJob(jobId: Int) {
        jobs.computeIfPresent(jobId) { _, job ->
            job.copy(
                status = JobStatus.COMPLETED,
                completedAt = Clock.System.now()
            )
        }
    }

    /**
     * Marks job as failed with error message
     */
    fun failJob(jobId: Int, errorMessage: String) {
        jobs.computeIfPresent(jobId) { _, job ->
            job.copy(
                status = JobStatus.FAILED,
                errorMessage = errorMessage,
                completedAt = Clock.System.now()
            )
        }
    }

    /**
     * Gets a specific job by ID
     */
    fun getJob(jobId: Int): ImportJob? {
        return jobs[jobId]
    }

    /**
     * Gets all jobs ordered by started time (newest first)
     */
    fun getAllJobs(): List<ImportJob> {
        return jobs.values
            .sortedByDescending { it.startedAt }
    }

    /**
     * Clears all jobs from memory (useful for testing)
     */
    fun clearAll() {
        jobs.clear()
    }
}
