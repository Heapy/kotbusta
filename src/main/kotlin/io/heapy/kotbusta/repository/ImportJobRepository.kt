package io.heapy.kotbusta.repository

import io.heapy.kotbusta.dao.job.CompleteJobQuery
import io.heapy.kotbusta.dao.job.CreateImportJobQuery
import io.heapy.kotbusta.dao.job.FailJobQuery
import io.heapy.kotbusta.dao.job.GetAllJobsQuery
import io.heapy.kotbusta.dao.job.UpdateProgressQuery
import io.heapy.kotbusta.dao.job.UpdateStatsQuery
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.model.ImportJob
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.model.JobType

class ImportJobRepository(
    private val createImportJobQuery: CreateImportJobQuery,
    private val updateProgressQuery: UpdateProgressQuery,
    private val updateStatsQuery: UpdateStatsQuery,
    private val completeJobQuery: CompleteJobQuery,
    private val failJobQuery: FailJobQuery,
    private val getAllJobsQuery: GetAllJobsQuery,
) {
    context(_: TransactionContext)
    fun create(jobType: JobType, progress: String): Long =
        createImportJobQuery.createJob(jobType, progress)

    context(_: TransactionContext)
    fun updateProgress(jobId: Long, progress: String) {
        updateProgressQuery.updateProgress(jobId, progress)
    }

    context(_: TransactionContext)
    fun updateStats(jobId: Long, stats: ImportStats) {
        updateStatsQuery.updateStats(jobId, stats)
    }

    context(_: TransactionContext)
    fun complete(jobId: Long, finalMessage: String) {
        completeJobQuery.completeJob(jobId, finalMessage)
    }

    context(_: TransactionContext)
    fun fail(jobId: Long, errorMessage: String) {
        failJobQuery.failJob(jobId, errorMessage)
    }

    context(_: TransactionContext)
    fun getAll(): List<ImportJob> =
        getAllJobsQuery.getAllJobs()
}