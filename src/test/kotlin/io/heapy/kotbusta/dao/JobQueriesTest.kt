package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.ImportJobs.Companion.IMPORT_JOBS
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.model.JobStatus
import io.heapy.kotbusta.model.JobType
import io.heapy.kotbusta.test.DatabaseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.concurrent.atomics.AtomicInt

/**
 * Integration tests for JobQueries.
 *
 * Tests database query methods that use useTx for import job-related operations.
 */
@ExtendWith(DatabaseExtension::class)
class JobQueriesTest {
    @Test
    context(_: TransactionProvider)
    fun `createImportJob should insert and return job ID`() = transaction {
        // When: Creating a new import job
        val jobId = createImportJob(
            jobType = JobType.DATA_IMPORT,
            progress = "Starting import",
        )

        // Then: Job should be created with RUNNING status
        assertNotNull(jobId)

        val job = useTx { dslContext ->
            dslContext
                .selectFrom(IMPORT_JOBS)
                .where(IMPORT_JOBS.ID.eq(jobId))
                .fetchOne()
        }

        assertNotNull(job)
        assertEquals("DATA_IMPORT", job?.jobType)
        assertEquals("RUNNING", job?.status)
        assertEquals("Starting import", job?.progress)
        assertNotNull(job?.startedAt)
        assertNull(job?.completedAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `getAllImportJobs should return all jobs ordered by started_at desc`() = transaction {
        // Given: Test fixtures contain 4 import jobs
        // When: Getting all import jobs
        val jobs = getAllImportJobs()

        // Then: Should return all jobs ordered by started_at descending
        assertEquals(4, jobs.size)

        // Verify ordering (most recent first)
        for (i in 0 until jobs.size - 1) {
            val current = jobs[i].startedAt
            val next = jobs[i + 1].startedAt
            if (current != null && next != null) {
                assert(current >= next) {
                    "Jobs should be ordered by startedAt descending"
                }
            }
        }

        // Verify job data is properly mapped
        val firstJob = jobs.first()
        assertNotNull(firstJob.id)
        assertNotNull(firstJob.jobType)
        assertNotNull(firstJob.status)
        assertNotNull(firstJob.progress)
        assertNotNull(firstJob.startedAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `getAllImportJobs should include completed jobs from fixtures`() = transaction {
        // Given: Fixtures have completed jobs
        // When: Getting all jobs
        val jobs = getAllImportJobs()

        // Then: Should include completed jobs
        val completedJobs = jobs.filter { it.status == JobStatus.COMPLETED }
        assertEquals(2, completedJobs.size)

        val job1 = completedJobs.find { it.id == 1 }
        assertNotNull(job1)
        assertEquals(JobType.DATA_IMPORT, job1?.jobType)
        assertEquals(10, job1?.booksAdded)
        assertNotNull(job1?.completedAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `updateJobProgress should update progress message`() = transaction {
        // Given: Job ID 3 is RUNNING
        // When: Updating progress
        val updated = updateJobProgress(
            jobId = 3,
            progress = "Processing file 10 of 20",
        )

        // Then: Should update successfully
        assertEquals(1, updated)

        val job = useTx { dslContext ->
            dslContext
                .selectFrom(IMPORT_JOBS)
                .where(IMPORT_JOBS.ID.eq(3))
                .fetchOne()
        }

        assertEquals("Processing file 10 of 20", job?.progress)
    }

    @Test
    context(_: TransactionProvider)
    fun `updateJobStats should update all statistics`() = transaction {
        // Given: Job ID 3 is RUNNING with some initial stats
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
        val updated = updateJobStats(jobId = 3, stats = stats)

        // Then: Should update successfully
        assertEquals(1, updated)

        val job = useTx { dslContext ->
            dslContext
                .selectFrom(IMPORT_JOBS)
                .where(IMPORT_JOBS.ID.eq(3))
                .fetchOne()
        }

        assertEquals(15, job?.inpFilesProcessed)
        assertEquals(10, job?.booksAdded)
        assertEquals(3, job?.booksUpdated)
        assertEquals(1, job?.booksDeleted)
        assertEquals(8, job?.coversAdded)
        assertEquals(2, job?.bookErrors)
        assertEquals(1, job?.coverErrors)
    }

    @Test
    context(_: TransactionProvider)
    fun `completeJob should mark job as COMPLETED`() = transaction {
        // Given: Job ID 3 is RUNNING
        // When: Completing the job
        val updated = completeJob(
            jobId = 3,
            finalMessage = "Import completed successfully",
        )

        // Then: Should update status and completion time
        assertEquals(1, updated)

        val job = useTx { dslContext ->
            dslContext
                .selectFrom(IMPORT_JOBS)
                .where(IMPORT_JOBS.ID.eq(3))
                .fetchOne()
        }

        assertEquals("COMPLETED", job?.status)
        assertEquals("Import completed successfully", job?.progress)
        assertNotNull(job?.completedAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `failJob should mark job as FAILED with error message`() = transaction {
        // Given: Job ID 3 is RUNNING
        // When: Failing the job
        val updated = failJob(
            jobId = 3,
            errorMessage = "Database connection lost",
        )

        // Then: Should update status and error message
        assertEquals(1, updated)

        val job = useTx { dslContext ->
            dslContext
                .selectFrom(IMPORT_JOBS)
                .where(IMPORT_JOBS.ID.eq(3))
                .fetchOne()
        }

        assertEquals("FAILED", job?.status)
        assertEquals("Database connection lost", job?.errorMessage)
        assertNotNull(job?.completedAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `createImportJob should handle COVER_EXTRACTION job type`() = transaction {
        // When: Creating a cover extraction job
        val jobId = createImportJob(
            jobType = JobType.COVER_EXTRACTION,
            progress = "Starting cover extraction",
        )

        // Then: Job should be created with correct type
        assertNotNull(jobId)

        val job = useTx { dslContext ->
            dslContext
                .selectFrom(IMPORT_JOBS)
                .where(IMPORT_JOBS.ID.eq(jobId))
                .fetchOne()
        }

        assertEquals("COVER_EXTRACTION", job?.jobType)
        assertEquals("RUNNING", job?.status)
    }

    @Test
    context(_: TransactionProvider)
    fun `job workflow should track progress from creation to completion`() = transaction {
        // Given: Create a new job
        val jobId = createImportJob(
            jobType = JobType.DATA_IMPORT,
            progress = "Starting",
        )

        // When: Update progress
        updateJobProgress(jobId, "Processing...")

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
        updateJobStats(jobId, stats)

        // Complete job
        completeJob(jobId, "All done!")

        // Then: Job should have complete history
        val jobs = getAllImportJobs()
        val job = jobs.find { it.id == jobId }

        assertNotNull(job)
        assertEquals(JobStatus.COMPLETED, job?.status)
        assertEquals("All done!", job?.progress)
        assertEquals(5, job?.booksAdded)
        assertEquals(3, job?.coversAdded)
        assertNotNull(job?.completedAt)
    }
}
