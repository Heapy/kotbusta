package io.heapy.kotbusta.service

import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.parser.Fb2Parser
import io.heapy.kotbusta.parser.InpxParser
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

@Serializable
data class ImportJob(
    val id: String,
    val type: String,
    val status: String,
    val progress: String,
    val startTime: Long,
    val endTime: Long? = null,
    val errorMessage: String? = null
)

class AdminService(
    private val adminEmail: String?,
    private val booksDataPath: Path,
    private val fb2Parser: Fb2Parser,
    private val inpxParser: InpxParser,
) {
    private val jobs = mutableMapOf<String, ImportJob>()

    fun isAdmin(
        userSession: UserSession?,
    ): Boolean {
        return if (userSession != null) {
            userSession.email == adminEmail
        } else false
    }

    fun startDataImport(extractCovers: Boolean): String {
        val jobId = "import_${System.currentTimeMillis()}"
        val job = ImportJob(
            id = jobId,
            type = "data_import",
            status = "running",
            progress = "Starting import...",
            startTime = System.currentTimeMillis()
        )
        jobs[jobId] = job

        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateJobProgress(jobId, "Parsing INPX data...")
                inpxParser.parseAndImport(booksDataPath)

                if (extractCovers) {
                    updateJobProgress(jobId, "Extracting covers...")
                    val archives = booksDataPath.listDirectoryEntries("*.zip")
                        .filter { it.fileName.toString().contains("fb2") }
                        .sorted()

                    if (archives.isNotEmpty()) {
                        archives.forEachIndexed { index, archive ->
                            updateJobProgress(jobId, "Processing archive ${index + 1}/${archives.size}: ${archive.fileName}")
                            fb2Parser.extractBookCovers(archive.toString())
                        }
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
            startTime = System.currentTimeMillis()
        )
        jobs[jobId] = job

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
                    updateJobProgress(jobId, "Processing archive ${index + 1}/${archives.size}: ${archive.fileName}")
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
        return jobs[jobId]
    }

    fun getAllJobs(): List<ImportJob> {
        return jobs.values.toList().sortedByDescending { it.startTime }
    }

    private fun updateJobProgress(jobId: String, progress: String) {
        jobs[jobId] = jobs[jobId]?.copy(progress = progress) ?: return
    }

    private fun completeJob(jobId: String, finalMessage: String) {
        jobs[jobId] = jobs[jobId]?.copy(
            status = "completed",
            progress = finalMessage,
            endTime = System.currentTimeMillis()
        ) ?: return
    }

    private fun failJob(jobId: String, errorMessage: String) {
        jobs[jobId] = jobs[jobId]?.copy(
            status = "failed",
            errorMessage = errorMessage,
            endTime = System.currentTimeMillis()
        ) ?: return
    }
}
