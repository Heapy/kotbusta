package io.heapy.kotbusta.dao.job

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.ImportJobs.Companion.IMPORT_JOBS
import io.heapy.kotbusta.mapper.mapUsing
import io.heapy.kotbusta.model.ImportJob

class GetAllJobsQuery {
    context(_: TransactionContext)
    fun getAllJobs(): List<ImportJob> = useTx { dslContext ->
        dslContext
            .selectFrom(IMPORT_JOBS)
            .orderBy(IMPORT_JOBS.STARTED_AT.desc())
            .fetch()
            .map { record ->
                record mapUsing ImportJobRecordMapper
            }
    }
}
