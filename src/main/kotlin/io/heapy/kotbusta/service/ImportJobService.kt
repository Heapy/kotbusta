package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.model.ImportJob
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.parser.Fb2Parser
import io.heapy.kotbusta.parser.InpxParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.io.path.listDirectoryEntries

class ImportJobService(
    private val booksDataPath: Path,
    private val fb2Parser: Fb2Parser,
    private val inpxParser: InpxParser,
) {
    private val stats = AtomicReference(ImportStats())
    private val jobRunning = AtomicBoolean(false)

    private fun startJob(): Boolean {
        val started = jobRunning
            .compareAndSet(
                expectedValue = false,
                newValue = true,
            )

        if (started) {
            stats.store(ImportStats())
        }

        return started
    }

    private fun stopJob(): Boolean {
        return jobRunning
            .compareAndSet(
                expectedValue = true,
                newValue = false,
            )
    }

    fun stats(): ImportStats = stats.load()

    fun startImport(applicationScope: CoroutineScope): Boolean {
        return if (startJob()) {
            applicationScope.launch(Dispatchers.IO) {
                val jobStats = stats.load()
                try {
                    startDataImport(jobStats)
                    startCoverExtraction(jobStats)
                } catch (e: Exception) {
                    jobStats.status.store(ImportJob.JobStatus.FAILED)
                    jobStats.addMessage("Error importing data: ${e.message}")
                    log.error("Error during import", e)
                    stopJob()
                }
            }
            true
        } else {
            log.warn("Another job is already running")
            false
        }
    }

    suspend fun startDataImport(jobStats: ImportStats) {
        jobStats.addMessage("Parsing INPX data")
        inpxParser.parseAndImport(booksDataPath, jobStats)
        jobStats.addMessage("Parsing INPX data completed")
    }

    suspend fun startCoverExtraction(jobStats: ImportStats) {
        jobStats.addMessage("Finding FB2 archives")
        val archives = booksDataPath.listDirectoryEntries("*.zip")
            .filter { it.fileName.toString().contains("fb2") }
            .sorted()

        if (archives.isEmpty()) {
            jobStats.addMessage("No FB2 archives found in $booksDataPath")
            return
        }

        jobStats.addMessage("Processing ${archives.size} archives in parallel...")

        // Process archives in parallel
        coroutineScope {
            archives
                .forEach { archive ->
                    fb2Parser.extractBookCovers(
                        archivePath = archive.toString(),
                        stats = jobStats,
                    )
                }
        }

        jobStats.addMessage("Cover extraction completed successfully with parallel processing!")
    }

    private companion object : Logger()
}
