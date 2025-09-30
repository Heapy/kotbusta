package io.heapy.kotbusta.dao.job

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.ImportJobs.Companion.IMPORT_JOBS
import io.heapy.kotbusta.model.ImportStats

class UpdateStatsQuery {
    context(_: TransactionContext)
    fun updateStats(
        jobId: Long,
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
}
