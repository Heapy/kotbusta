package io.heapy.kotbusta.service

import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.parser.Fb2Parser
import io.heapy.kotbusta.parser.InpxParser
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.listDirectoryEntries

@Serializable
data class ImportJob(
    val id: String,
    val type: String,
    val status: String,
    val progress: String,
    val startTime: Long,
    val endTime: Long? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalAtomicApi::class)
class AdminService(
    private val adminEmail: String?,
    private val booksDataPath: Path,
    private val fb2Parser: Fb2Parser,
    private val inpxParser: InpxParser,
) {
    private val importJob = AtomicReference<ImportJob?>(null)

    fun isAdmin(
        userSession: UserSession?,
    ): Boolean {
        return if (userSession != null) {
            userSession.email == adminEmail
        } else false
    }

    fun startDataImport(): String {
        val jobId = "import_${System.currentTimeMillis()}"
        val job = ImportJob(
            id = jobId,
            type = "data_import",
            status = "running",
            progress = "Starting import...",
            startTime = System.currentTimeMillis(),
        )
        importJob.store(job)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateJobProgress(jobId, "Parsing INPX data...")
                inpxParser.parseAndImport(booksDataPath)

                updateJobProgress(jobId, "Extracting covers...")
                val archives = booksDataPath.listDirectoryEntries("*.zip")
                    .filter { it.fileName.toString().contains("fb2") }
                    .sorted()

                if (archives.isNotEmpty()) {
                    archives.forEachIndexed { index, archive ->
                        updateJobProgress(
                            jobId,
                            "Processing archive ${index + 1}/${archives.size}: ${archive.fileName}",
                        )
                        fb2Parser.extractBookCovers(archive.toString())
                    }
                }

                completeJob(jobId, "Import completed successfully!")
            } catch (e: Exception) {
                failJob(jobId, e.message ?: "Unknown error occurred")
            }
        }

        return jobId
    }

    fun startCoverExtraction(): String {
        val jobId = "covers_${System.currentTimeMillis()}"
        val job = ImportJob(
            id = jobId,
            type = "cover_extraction",
            status = "running",
            progress = "Starting cover extraction...",
            startTime = System.currentTimeMillis(),
        )
        importJob.store(job)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateJobProgress(jobId, "Finding FB2 archives...")
                val archives = booksDataPath.listDirectoryEntries("*.zip")
                    .filter { it.fileName.toString().contains("fb2") }
                    .sorted()

                if (archives.isEmpty()) {
                    failJob(jobId, "No FB2 archives found in $booksDataPath")
                    return@launch
                }

                archives.forEachIndexed { index, archive ->
                    updateJobProgress(
                        jobId,
                        "Processing archive ${index + 1}/${archives.size}: ${archive.fileName}",
                    )
                    fb2Parser.extractBookCovers(archive.toString())
                }

                completeJob(jobId, "Cover extraction completed successfully!")
            } catch (e: Exception) {
                failJob(jobId, e.message ?: "Unknown error occurred")
            }
        }

        return jobId
    }

    fun getJob(jobId: String): ImportJob? {
        return importJob.load()
    }

    fun getAllJobs(): List<ImportJob> {
        return importJob.load()?.let { listOf(it) } ?: emptyList()
    }

    private fun updateJobProgress(jobId: String, progress: String) {
        importJob.store(importJob.load()?.copy(progress = progress))
    }

    private fun completeJob(jobId: String, finalMessage: String) {
        importJob.store(
            importJob.load()?.copy(
                status = "completed",
                progress = finalMessage,
                endTime = System.currentTimeMillis(),
            ),
        )
    }

    private fun failJob(jobId: String, errorMessage: String) {
        importJob.store(
            importJob.load()?.copy(
                status = "failed",
                errorMessage = errorMessage,
                endTime = System.currentTimeMillis(),
            ),
        )
    }
}
