package io.heapy.kotbusta.service

import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.model.JobStatus
import io.heapy.kotbusta.model.JobType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.concurrent.atomics.AtomicInt

/**
 * Unit tests for JobStatsService.
 *
 * Tests in-memory job statistics storage and management.
 */
class JobStatsServiceTest {
    private lateinit var service: JobStatsService

    @BeforeEach
    fun setup() {
        service = JobStatsService()
    }

    @Test
    fun `createJob should create a new job with RUNNING status`() {
        // When: Creating a new import job
        val jobId = service.createJob(JobType.DATA_IMPORT)

        // Then: Job should be created with RUNNING status
        val job = service.getJob(jobId)
        assertNotNull(job)
        assertEquals(JobType.DATA_IMPORT, job?.jobType)
        assertEquals(JobStatus.RUNNING, job?.status)
        assertNull(job?.progress)
        assertNotNull(job?.startedAt)
        assertNull(job?.completedAt)
        assertEquals(0, job?.inpFilesProcessed)
        assertEquals(0, job?.booksAdded)
    }

    @Test
    fun `getAllJobs should return all jobs ordered by started time`() {
        // Given: Create multiple jobs
        val job1Id = service.createJob(JobType.DATA_IMPORT)
        Thread.sleep(10) // Small delay to ensure different timestamps
        val job2Id = service.createJob(JobType.COVER_EXTRACTION)
        Thread.sleep(10)
        val job3Id = service.createJob(JobType.DATA_IMPORT)

        // When: Getting all jobs
        val jobs = service.getAllJobs()

        // Then: Should return all 3 jobs ordered by started time (newest first)
        assertEquals(3, jobs.size)
        assertEquals(job3Id, jobs[0].id)
        assertEquals(job2Id, jobs[1].id)
        assertEquals(job1Id, jobs[2].id)
    }

    @Test
    fun `updateProgress should update progress message`() {
        // Given: A running job
        val jobId = service.createJob(JobType.DATA_IMPORT)

        // When: Updating progress
        service.updateProgress(jobId, "Processing file 10 of 20")

        // Then: Progress should be updated
        val job = service.getJob(jobId)
        assertEquals("Processing file 10 of 20", job?.progress)
    }

    @Test
    fun `updateStats should update all statistics`() {
        // Given: A running job
        val jobId = service.createJob(JobType.DATA_IMPORT)
        val stats = ImportStats(
            inpFilesProcessed = AtomicInt(15),
            booksAdded = AtomicInt(10),
            booksUpdated = AtomicInt(3),
            booksDeleted = AtomicInt(1),
            coversAdded = AtomicInt(8),
            bookErrors = AtomicInt(2),
            coverErrors = AtomicInt(1),
        )

        // When: Updating job stats
        service.updateStats(jobId, stats)

        // Then: All stats should be updated
        val job = service.getJob(jobId)
        assertEquals(15, job?.inpFilesProcessed)
        assertEquals(10, job?.booksAdded)
        assertEquals(3, job?.booksUpdated)
        assertEquals(1, job?.booksDeleted)
        assertEquals(8, job?.coversAdded)
        assertEquals(2, job?.bookErrors)
        assertEquals(1, job?.coverErrors)
    }

    @Test
    fun `completeJob should mark job as COMPLETED`() {
        // Given: A running job
        val jobId = service.createJob(JobType.DATA_IMPORT)

        // When: Completing the job
        service.completeJob(jobId)

        // Then: Job should be marked as COMPLETED
        val job = service.getJob(jobId)
        assertEquals(JobStatus.COMPLETED, job?.status)
        assertNotNull(job?.completedAt)
    }

    @Test
    fun `failJob should mark job as FAILED with error message`() {
        // Given: A running job
        val jobId = service.createJob(JobType.DATA_IMPORT)

        // When: Failing the job
        service.failJob(jobId, "Database connection lost")

        // Then: Job should be marked as FAILED with error message
        val job = service.getJob(jobId)
        assertEquals(JobStatus.FAILED, job?.status)
        assertEquals("Database connection lost", job?.errorMessage)
        assertNotNull(job?.completedAt)
    }

    @Test
    fun `job workflow should track progress from creation to completion`() {
        // Given: Create a new job
        val jobId = service.createJob(JobType.DATA_IMPORT)

        // When: Update progress
        service.updateProgress(jobId, "Processing...")

        // Update stats
        val stats = ImportStats(
            inpFilesProcessed = AtomicInt(5),
            booksAdded = AtomicInt(5),
            booksUpdated = AtomicInt(0),
            booksDeleted = AtomicInt(0),
            coversAdded = AtomicInt(3),
            bookErrors = AtomicInt(0),
            coverErrors = AtomicInt(0),
        )
        service.updateStats(jobId, stats)

        // Update progress again
        service.updateProgress(jobId, "All done!")

        // Complete job
        service.completeJob(jobId)

        // Then: Job should have complete history
        val job = service.getJob(jobId)
        assertNotNull(job)
        assertEquals(JobStatus.COMPLETED, job?.status)
        assertEquals("All done!", job?.progress)
        assertEquals(5, job?.booksAdded)
        assertEquals(3, job?.coversAdded)
        assertNotNull(job?.completedAt)
    }

    @Test
    fun `clearAll should remove all jobs`() {
        // Given: Create some jobs
        service.createJob(JobType.DATA_IMPORT)
        service.createJob(JobType.COVER_EXTRACTION)
        assertEquals(2, service.getAllJobs().size)

        // When: Clearing all jobs
        service.clearAll()

        // Then: No jobs should exist
        assertEquals(0, service.getAllJobs().size)
    }

    @Test
    fun `createJob should handle COVER_EXTRACTION job type`() {
        // When: Creating a cover extraction job
        val jobId = service.createJob(JobType.COVER_EXTRACTION)

        // Then: Job should be created with correct type
        val job = service.getJob(jobId)
        assertEquals(JobType.COVER_EXTRACTION, job?.jobType)
        assertEquals(JobStatus.RUNNING, job?.status)
    }

    @Test
    fun `getJob should return null for non-existent job`() {
        // When: Getting a job that doesn't exist
        val job = service.getJob(999)

        // Then: Should return null
        assertNull(job)
    }
}
