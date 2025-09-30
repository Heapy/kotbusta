package io.heapy.kotbusta.dao.job

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.ImportJobs.Companion.IMPORT_JOBS

class UpdateProgressQuery {
    context(_: TransactionContext)
    fun updateProgress(
        jobId: Long,
        progress: String,
    ): Int = useTx { dslContext ->
        dslContext
            .update(IMPORT_JOBS)
            .set(IMPORT_JOBS.PROGRESS, progress)
            .where(IMPORT_JOBS.ID.eq(jobId))
            .execute()
    }
}
