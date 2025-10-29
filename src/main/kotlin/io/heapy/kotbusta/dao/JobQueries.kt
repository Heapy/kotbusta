package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.ImportJobs.Companion.IMPORT_JOBS
import io.heapy.kotbusta.jooq.tables.records.ImportJobsRecord
import io.heapy.kotbusta.mapper.LeftTypeMapper
import io.heapy.kotbusta.mapper.TypeMapper
import io.heapy.kotbusta.mapper.mapUsing
import io.heapy.kotbusta.model.ImportJob
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.model.JobStatus
import io.heapy.kotbusta.model.JobType
import kotlin.time.Clock
import kotlin.time.Instant

val JobTypeMapper = TypeMapper<JobType, String>(
    left = { input -> input.name },
    right = { output -> JobType.valueOf(output) },
)

val JobStatusMapper = TypeMapper<JobStatus, String>(
    left = { input -> input.name },
    right = { output -> JobStatus.valueOf(output) },
)

val ImportJobRecordMapper =
    LeftTypeMapper<ImportJobsRecord, ImportJob> { output ->
        ImportJob(
            id = output.id!!,
            jobType = output.jobType.mapUsing(JobTypeMapper),
            status = output.status.mapUsing(JobStatusMapper),
            progress = output.progress,
            inpFilesProcessed = output.inpFilesProcessed ?: 0,
            booksAdded = output.booksAdded ?: 0,
            booksUpdated = output.booksUpdated ?: 0,
            booksDeleted = output.booksDeleted ?: 0,
            coversAdded = output.coversAdded ?: 0,
            bookErrors = output.bookErrors ?: 0,
            coverErrors = output.coverErrors ?: 0,
            errorMessage = output.errorMessage,
            startedAt = output.startedAt,
            completedAt = output.completedAt,
        )
    }

context(_: TransactionContext)
fun createImportJob(
    jobType: JobType,
    progress: String,
    startedAt: Instant = Clock.System.now(),
): Int = useTx { dslContext ->
    dslContext
        .insertInto(IMPORT_JOBS)
        .set(IMPORT_JOBS.JOB_TYPE, jobType mapUsing JobTypeMapper)
        .set(IMPORT_JOBS.STATUS, JobStatus.RUNNING mapUsing JobStatusMapper)
        .set(IMPORT_JOBS.PROGRESS, progress)
        .set(IMPORT_JOBS.STARTED_AT, startedAt)
        .returning(IMPORT_JOBS.ID)
        .fetchOne(IMPORT_JOBS.ID)
        ?: error("Failed to create import job")
}

context(_: TransactionContext)
fun getAllImportJobs(): List<ImportJob> = useTx { dslContext ->
    dslContext
        .selectFrom(IMPORT_JOBS)
        .orderBy(IMPORT_JOBS.STARTED_AT.desc())
        .fetch()
        .map { record ->
            record mapUsing ImportJobRecordMapper
        }
}

context(_: TransactionContext)
fun updateJobProgress(
    jobId: Int,
    progress: String,
): Int = useTx { dslContext ->
    dslContext
        .update(IMPORT_JOBS)
        .set(IMPORT_JOBS.PROGRESS, progress)
        .where(IMPORT_JOBS.ID.eq(jobId))
        .execute()
}

context(_: TransactionContext)
fun updateJobStats(
    jobId: Int,
    stats: ImportStats,
): Int = useTx { dslContext ->
    dslContext
        .update(IMPORT_JOBS)
        .set(
            IMPORT_JOBS.INP_FILES_PROCESSED,
            stats.inpFilesProcessed.load(),
        )
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
    jobId: Int,
    finalMessage: String,
    completedAt: Instant = Clock.System.now(),
): Int = useTx { dslContext ->
    dslContext
        .update(IMPORT_JOBS)
        .set(IMPORT_JOBS.STATUS, JobStatus.COMPLETED.mapUsing(JobStatusMapper))
        .set(IMPORT_JOBS.PROGRESS, finalMessage)
        .set(IMPORT_JOBS.COMPLETED_AT, completedAt)
        .where(IMPORT_JOBS.ID.eq(jobId))
        .execute()
}

context(_: TransactionContext)
fun failJob(
    jobId: Int,
    errorMessage: String,
    completedAt: Instant = Clock.System.now(),
): Int = useTx { dslContext ->
    dslContext
        .update(IMPORT_JOBS)
        .set(IMPORT_JOBS.STATUS, JobStatus.FAILED.mapUsing(JobStatusMapper))
        .set(IMPORT_JOBS.ERROR_MESSAGE, errorMessage)
        .set(IMPORT_JOBS.COMPLETED_AT, completedAt)
        .where(IMPORT_JOBS.ID.eq(jobId))
        .execute()
}
