package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.dslContext
import io.heapy.kotbusta.jooq.enums.JobStatusEnum
import io.heapy.kotbusta.jooq.enums.JobTypeEnum
import io.heapy.kotbusta.jooq.tables.ImportJobs.Companion.IMPORT_JOBS
import io.heapy.kotbusta.jooq.tables.records.ImportJobsRecord
import io.heapy.kotbusta.model.ImportJob
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.model.JobStatus
import io.heapy.kotbusta.model.JobType
import java.time.OffsetDateTime

class ImportJobDao {
    private fun JobType.toDbEnum(): JobTypeEnum = when (this) {
        JobType.DATA_IMPORT -> JobTypeEnum.DATA_IMPORT
        JobType.COVER_EXTRACTION -> JobTypeEnum.COVER_EXTRACTION
    }

    private fun JobTypeEnum.toApiEnum(): JobType = when (this) {
        JobTypeEnum.DATA_IMPORT -> JobType.DATA_IMPORT
        JobTypeEnum.COVER_EXTRACTION -> JobType.COVER_EXTRACTION
    }

    private fun JobStatus.toDbEnum(): JobStatusEnum = when (this) {
        JobStatus.RUNNING -> JobStatusEnum.RUNNING
        JobStatus.COMPLETED -> JobStatusEnum.COMPLETED
        JobStatus.FAILED -> JobStatusEnum.FAILED
    }

    private fun JobStatusEnum.toApiEnum(): JobStatus = when (this) {
        JobStatusEnum.RUNNING -> JobStatus.RUNNING
        JobStatusEnum.COMPLETED -> JobStatus.COMPLETED
        JobStatusEnum.FAILED -> JobStatus.FAILED
    }

    context(_: TransactionContext)
    fun createJob(
        jobType: JobType,
        progress: String,
    ): Long = dslContext { dslContext ->
        dslContext
            .insertInto(IMPORT_JOBS)
            .set(IMPORT_JOBS.JOB_TYPE, jobType.toDbEnum())
            .set(IMPORT_JOBS.STATUS, JobStatus.RUNNING.toDbEnum())
            .set(IMPORT_JOBS.PROGRESS, progress)
            .set(IMPORT_JOBS.STARTED_AT, OffsetDateTime.now())
            .returning(IMPORT_JOBS.ID)
            .fetchOne(IMPORT_JOBS.ID)
            ?: throw RuntimeException("Failed to create import job")
    }

    context(_: TransactionContext)
    fun updateProgress(
        jobId: Long,
        progress: String,
    ): Int = dslContext { dslContext ->
        dslContext
            .update(IMPORT_JOBS)
            .set(IMPORT_JOBS.PROGRESS, progress)
            .where(IMPORT_JOBS.ID.eq(jobId))
            .execute()
    }

    context(_: TransactionContext)
    fun updateStats(
        jobId: Long,
        stats: ImportStats,
    ): Int = dslContext { dslContext ->
        dslContext
            .update(IMPORT_JOBS)
            .set(IMPORT_JOBS.INP_FILES_PROCESSED, stats.inpFilesProcessed.load())
            .set(IMPORT_JOBS.BOOKS_ADDED, stats.booksAdded.load())
            .set(IMPORT_JOBS.BOOKS_UPDATED, stats.booksUpdated.load())
            .set(IMPORT_JOBS.BOOKS_DELETED, stats.booksDeleted.load())
            .set(IMPORT_JOBS.COVERS_ADDED, stats.coversAdded.load())
            .set(IMPORT_JOBS.BOOK_ERRORS, stats.bookErrors.load())
            .set(IMPORT_JOBS.COVER_ERRORS, stats.coverErrors.load())
            .where(IMPORT_JOBS.ID.eq(jobId))
            .execute()
    }

    context(_: TransactionContext)
    fun completeJob(
        jobId: Long,
        finalMessage: String,
    ): Int = dslContext { dslContext ->
        dslContext
            .update(IMPORT_JOBS)
            .set(IMPORT_JOBS.STATUS, JobStatus.COMPLETED.toDbEnum())
            .set(IMPORT_JOBS.PROGRESS, finalMessage)
            .set(IMPORT_JOBS.COMPLETED_AT, OffsetDateTime.now())
            .where(IMPORT_JOBS.ID.eq(jobId))
            .execute()
    }

    context(_: TransactionContext)
    fun failJob(
        jobId: Long,
        errorMessage: String,
    ): Int = dslContext { dslContext ->
        dslContext
            .update(IMPORT_JOBS)
            .set(IMPORT_JOBS.STATUS, JobStatus.FAILED.toDbEnum())
            .set(IMPORT_JOBS.ERROR_MESSAGE, errorMessage)
            .set(IMPORT_JOBS.COMPLETED_AT, OffsetDateTime.now())
            .where(IMPORT_JOBS.ID.eq(jobId))
            .execute()
    }

    context(_: TransactionContext)
    fun getAllJobs(): List<ImportJob> = dslContext { dslContext ->
        dslContext
            .selectFrom(IMPORT_JOBS)
            .orderBy(IMPORT_JOBS.STARTED_AT.desc())
            .fetch()
            .map { record ->
                record.toPojo()
            }
    }

    private fun ImportJobsRecord.toPojo(): ImportJob {
        return ImportJob(
            id = id!!,
            jobType = jobType.toApiEnum(),
            status = status.toApiEnum(),
            progress = progress,
            inpFilesProcessed = inpFilesProcessed ?: 0,
            booksAdded = booksAdded ?: 0,
            booksUpdated = booksUpdated ?: 0,
            booksDeleted = booksDeleted ?: 0,
            coversAdded = coversAdded ?: 0,
            bookErrors = bookErrors ?: 0,
            coverErrors = coverErrors ?: 0,
            errorMessage = errorMessage,
            startedAt = startedAt!!.toEpochSecond(),
            completedAt = completedAt?.toEpochSecond()
        )
    }
}
