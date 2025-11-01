package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.model.ImportJob.JobStatus.COMPLETED
import io.heapy.kotbusta.model.ImportJob.JobStatus.FAILED
import io.heapy.kotbusta.model.ImportJob.JobStatus.RUNNING
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.parser.InpxParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock

class ImportJobService(
    private val booksDataPath: Path,
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
                jobStats.status.store(RUNNING)
                try {
                    startDataImport(jobStats)
                    jobStats.status.store(COMPLETED)
                    jobStats.completedAt.store(Clock.System.now())
                    jobStats.addMessage("Import completed successfully!")
                    stopJob()
                } catch (e: Exception) {
                    jobStats.status.store(FAILED)
                    jobStats.addMessage("Error importing data: ${e.message}", e)
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
        inpxParser.parseAndImport(booksDataPath, jobStats)
    }

    private companion object : Logger()
}
