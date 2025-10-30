package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.coroutines.Loom
import io.heapy.kotbusta.dao.completeJob
import io.heapy.kotbusta.dao.createImportJob
import io.heapy.kotbusta.dao.failJob
import io.heapy.kotbusta.dao.updateJobProgress
import io.heapy.kotbusta.dao.updateJobStats
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.model.JobType
import io.heapy.kotbusta.parser.Fb2Parser
import io.heapy.kotbusta.parser.InpxParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

class ImportJobService(
    private val booksDataPath: Path,
    private val fb2Parser: Fb2Parser,
    private val inpxParser: InpxParser,
) {
    context(_: TransactionContext)
    fun startImport(applicationScope: CoroutineScope) =
        applicationScope.launch(Dispatchers.Loom) {
            startDataImport()
            startCoverExtraction()
        }

    context(_: TransactionContext)
    suspend fun startDataImport() {
        val jobId = createImportJob(JobType.DATA_IMPORT, "Starting import...")
            .let(::JobId)

        val stats = ImportStats()
        try {
            updateJobProgress(jobId.value, "Parsing INPX data in parallel...")

            inpxParser.parseAndImport(booksDataPath, jobId, stats)

            updateJobStats(jobId.value, stats)
            completeJob(
                jobId.value,
                "Import completed successfully with parallel processing!",
            )
        } catch (e: Exception) {
            log.error("Error importing data", e)
            failJob(jobId.value, e.message ?: "Unknown error occurred")
        }
    }

    context(_: TransactionContext)
    suspend fun startCoverExtraction() {
        val jobId = createImportJob(JobType.COVER_EXTRACTION, "Starting cover extraction...")
            .let(::JobId)

        val stats = ImportStats()
        try {
            updateJobProgress(jobId.value, "Finding FB2 archives...")
            val archives = booksDataPath.listDirectoryEntries("*.zip")
                .filter { it.fileName.toString().contains("fb2") }
                .sorted()

            if (archives.isEmpty()) {
                failJob(jobId.value, "No FB2 archives found in $booksDataPath")
            }

            updateJobProgress(
                jobId.value,
                "Processing ${archives.size} archives in parallel...",
            )

            // Process archives in parallel
            coroutineScope {
                archives
                    .forEach { archive ->
                        launch(Dispatchers.Loom) {
                            fb2Parser.extractBookCovers(
                                archivePath = archive.toString(),
                                jobId = jobId,
                                stats = stats,
                            )
                        }
                    }
            }

            updateJobStats(jobId.value, stats)
            completeJob(
                jobId.value,
                "Cover extraction completed successfully with parallel processing!",
            )
        } catch (e: Exception) {
            log.error("Error extracting covers", e)
            failJob(jobId.value, e.message ?: "Unknown error occurred")
        }
    }

    @JvmInline
    @Serializable
    value class JobId(val value: Int)

    private companion object : Logger()
}
