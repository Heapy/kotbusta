package io.heapy.kotbusta.dao.job

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.enums.JobStatusEnum
import io.heapy.kotbusta.jooq.tables.ImportJobs.Companion.IMPORT_JOBS
import java.time.OffsetDateTime

class CompleteJobQuery {
    context(_: TransactionContext)
    fun completeJob(
        jobId: Long,
        finalMessage: String,
    ): Int = useTx { dslContext ->
        dslContext
            .update(IMPORT_JOBS)
            .set(IMPORT_JOBS.STATUS, JobStatusEnum.COMPLETED)
            .set(IMPORT_JOBS.PROGRESS, finalMessage)
            .set(IMPORT_JOBS.COMPLETED_AT, OffsetDateTime.now())
            .where(IMPORT_JOBS.ID.eq(jobId))
            .execute()
    }
}
