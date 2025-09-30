package io.heapy.kotbusta.dao.job

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.enums.JobStatusEnum
import io.heapy.kotbusta.jooq.tables.ImportJobs.Companion.IMPORT_JOBS
import io.heapy.kotbusta.mapper.mapUsing
import io.heapy.kotbusta.model.JobType
import java.time.OffsetDateTime

class CreateImportJobQuery {
    context(_: TransactionContext)
    fun createJob(
        jobType: JobType,
        progress: String,
    ): Long = useTx { dslContext ->
        dslContext
            .insertInto(IMPORT_JOBS)
            .set(IMPORT_JOBS.JOB_TYPE, jobType mapUsing JobTypeMapper)
            .set(IMPORT_JOBS.STATUS, JobStatusEnum.RUNNING)
            .set(IMPORT_JOBS.PROGRESS, progress)
            .set(IMPORT_JOBS.STARTED_AT, OffsetDateTime.now())
            .returning(IMPORT_JOBS.ID)
            .fetchOne(IMPORT_JOBS.ID)
            ?: error("Failed to create import job")
    }
}
