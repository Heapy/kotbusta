package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.model.KindleUploadSourceFormat
import io.heapy.kotbusta.util.UNDERSCORE_RUN
import io.heapy.kotbusta.util.WHITESPACE_RUN
import io.heapy.kotbusta.util.takeCodePointSafe
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.time.Instant

class UploadTooLargeException(maxBytes: Long) :
    Exception("Uploaded file is larger than $maxBytes bytes")

class StoredUpload(
    val fileName: String,
    val sizeBytes: Int,
)

/**
 * Keeps user-uploaded books on disk between the upload request and the Kindle
 * send. Stored names are generated, never derived from the uploaded file name, so
 * an uploaded name can never escape [uploadPath] or overwrite another upload.
 */
class KindleUploadStorage(
    private val uploadPath: Path,
    val maxUploadBytes: Long,
) {
    init {
        // SIZE_BYTES is an INTEGER column read back as an Int, so a larger cap
        // would silently store a wrapped size.
        require(maxUploadBytes in 1..Int.MAX_VALUE.toLong()) {
            "maxUploadBytes must be a positive value that fits in an Int: $maxUploadBytes"
        }
    }

    fun initialize() {
        val _ = Files.createDirectories(uploadPath)
        log.info("Kindle upload directory: $uploadPath")
    }

    fun store(
        input: InputStream,
        extension: String,
    ): StoredUpload {
        val _ = Files.createDirectories(uploadPath)
        val fileName = "${UUID.randomUUID()}.$extension"
        val partFile = uploadPath.resolve("$fileName$PART_SUFFIX")

        try {
            var total = 0L
            partFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxUploadBytes) {
                        throw UploadTooLargeException(maxUploadBytes)
                    }
                    output.write(buffer, 0, read)
                }
            }
            val _ = Files.move(partFile, uploadPath.resolve(fileName), ATOMIC_MOVE)
            return StoredUpload(fileName = fileName, sizeBytes = total.toInt())
        } catch (e: Throwable) {
            val _ = partFile.deleteIfExists()
            throw e
        }
    }

    fun resolve(fileName: String): File {
        require(Path.of(fileName).name == fileName) {
            "Stored upload name must not contain a path: $fileName"
        }
        return uploadPath.resolve(fileName).toFile()
    }

    fun delete(fileName: String) {
        try {
            val _ = resolve(fileName).toPath().deleteIfExists()
        } catch (e: Exception) {
            log.warn("Failed to delete stored upload $fileName", e)
        }
    }

    /**
     * Deletes files that no queue row refers to any more. Only names this class
     * generates are eligible, so a misconfigured [uploadPath] pointing at real data
     * is harmless. [olderThan] spares a finished file whose row has not been
     * committed yet; [partialsOlderThan] is separate and far older, because a
     * `.part` file belongs to a request that may still be streaming into it.
     */
    fun sweepOrphans(
        referenced: Set<String>,
        olderThan: Instant,
        partialsOlderThan: Instant,
    ): Int {
        if (!Files.isDirectory(uploadPath)) return 0

        var deleted = 0
        Files.newDirectoryStream(uploadPath).use { entries ->
            entries.forEach { entry ->
                if (!Files.isRegularFile(entry)) return@forEach
                if (!STORED_UPLOAD_NAME.matches(entry.name)) return@forEach
                if (entry.name in referenced) return@forEach
                val modifiedAt = Instant.fromEpochMilliseconds(
                    entry.getLastModifiedTime().toMillis(),
                )
                val floor = if (entry.name.endsWith(PART_SUFFIX)) partialsOlderThan else olderThan
                if (modifiedAt >= floor) return@forEach
                if (entry.deleteIfExists()) deleted++
            }
        }

        if (deleted > 0) {
            log.info("Deleted $deleted orphaned Kindle upload file(s)")
        }
        return deleted
    }

    private companion object : Logger() {
        private const val PART_SUFFIX = ".part"

        private val STORED_UPLOAD_NAME = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" +
                "\\.(" +
                KindleUploadSourceFormat.entries.joinToString("|") { it.extension } +
                ")(\\$PART_SUFFIX)?",
        )
    }
}

private val PDF_HEADER = "%PDF-".toByteArray()
private val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private val UTF16LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
private val UTF16BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

internal const val UPLOAD_HEADER_PROBE_BYTES = 1024

/**
 * Decides which upload format a file claims to be, by extension, and rejects it
 * when the first bytes contradict that. EPUB is a zip container, so its header is
 * shared with any other zip: the header only rules out a crude mismatch, the
 * extension decides the format.
 */
internal fun detectUploadFormat(
    fileName: String,
    header: ByteArray,
): KindleUploadSourceFormat? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val format = KindleUploadSourceFormat.entries
        .find { it.extension == extension }
        ?: return null

    val matchesHeader = when (format) {
        KindleUploadSourceFormat.EPUB -> header.startsWith(ZIP_HEADER)
        KindleUploadSourceFormat.PDF -> header.startsWith(PDF_HEADER)
        KindleUploadSourceFormat.FB2 -> looksLikeFb2(header)
    }

    return format.takeIf { matchesHeader }
}

private fun looksLikeFb2(header: ByteArray): Boolean {
    // FB2 files ship in UTF-8, windows-1251 and UTF-16 alike; the byte order mark
    // is the only thing that says which one, and a wrong charset would turn valid
    // markup into bytes that match nothing.
    val text = when {
        header.startsWith(UTF16LE_BOM) ->
            String(header.drop(UTF16LE_BOM.size).toByteArray(), Charsets.UTF_16LE)

        header.startsWith(UTF16BE_BOM) ->
            String(header.drop(UTF16BE_BOM.size).toByteArray(), Charsets.UTF_16BE)

        header.startsWith(UTF8_BOM) ->
            String(header.drop(UTF8_BOM.size).toByteArray(), Charsets.UTF_8)

        else -> String(header, Charsets.UTF_8)
    }
    return text.trimStart().startsWith("<?xml") || text.contains("<FictionBook")
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private val UNSAFE_UPLOAD_NAME_CHARS = Regex("""[\p{Cntrl}/\\:*?"<>|]+""")

/**
 * Turns the name the browser sent into a name safe to put in an email header and
 * in a zip entry. It never decides where bytes are stored — that name is generated.
 * The extension survives truncation, because the route reads the upload format
 * from it.
 */
internal fun sanitizeUploadFileName(fileName: String): String {
    val name = fileName
        .substringAfterLast('/')
        .substringAfterLast('\\')
    val extension = cleanUploadNamePart(name.substringAfterLast('.', ""))
    val base = cleanUploadNamePart(
        if (extension.isEmpty()) name else name.substringBeforeLast('.'),
    )
        .takeCodePointSafe(MAX_UPLOAD_BASE_NAME_LENGTH)
        .trimEnd('.', '_')
        .ifBlank { "upload" }

    return if (extension.isEmpty()) base else "$base.$extension"
}

private const val MAX_UPLOAD_BASE_NAME_LENGTH = 120

private fun cleanUploadNamePart(part: String): String =
    part
        .replace(UNSAFE_UPLOAD_NAME_CHARS, "_")
        .replace(WHITESPACE_RUN, "_")
        .replace(UNDERSCORE_RUN, "_")
        .trim('.', '_')
