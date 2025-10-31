package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.coroutines.Loom
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
    private val jobStatsService: JobStatsService,
) {
    fun startImport(applicationScope: CoroutineScope) =
        applicationScope.launch(Dispatchers.Loom) {
            startDataImport()
            startCoverExtraction()
        }

    suspend fun startDataImport() {
        val jobId = jobStatsService.createJob(JobType.DATA_IMPORT)
            .let(::JobId)

        val stats = ImportStats()
        try {
            jobStatsService.updateProgress(jobId.value, "Parsing INPX data in parallel...")

            inpxParser.parseAndImport(booksDataPath, jobId, stats)

            jobStatsService.updateStats(jobId.value, stats)
            jobStatsService.updateProgress(
                jobId.value,
                "Import completed successfully with parallel processing!",
            )
            jobStatsService.completeJob(jobId.value)
        } catch (e: Exception) {
            log.error("Error importing data", e)
            jobStatsService.failJob(jobId.value, e.message ?: "Unknown error occurred")
        }
    }

    suspend fun startCoverExtraction() {
        val jobId = jobStatsService.createJob(JobType.COVER_EXTRACTION)
            .let(::JobId)

        val stats = ImportStats()
        try {
            jobStatsService.updateProgress(jobId.value, "Finding FB2 archives...")
            val archives = booksDataPath.listDirectoryEntries("*.zip")
                .filter { it.fileName.toString().contains("fb2") }
                .sorted()

            if (archives.isEmpty()) {
                jobStatsService.failJob(jobId.value, "No FB2 archives found in $booksDataPath")
                return
            }

            jobStatsService.updateProgress(
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

            jobStatsService.updateStats(jobId.value, stats)
            jobStatsService.updateProgress(
                jobId.value,
                "Cover extraction completed successfully with parallel processing!",
            )
            jobStatsService.completeJob(jobId.value)
        } catch (e: Exception) {
            log.error("Error extracting covers", e)
            jobStatsService.failJob(jobId.value, e.message ?: "Unknown error occurred")
        }
    }

    @JvmInline
    @Serializable
    value class JobId(val value: Int)

    private companion object : Logger()
}
