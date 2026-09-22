package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.KindleUploadSourceFormat
import io.heapy.kotbusta.service.KindleUploadStorage
import io.heapy.kotbusta.service.QuotaExceededException
import io.heapy.kotbusta.service.StoredUpload
import io.heapy.kotbusta.service.UPLOAD_HEADER_PROBE_BYTES
import io.heapy.kotbusta.service.UploadTooLargeException
import io.heapy.kotbusta.service.detectUploadFormat
import io.heapy.kotbusta.service.sanitizeUploadFileName
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val FILE_PART_NAME = "file"

context(applicationModule: ApplicationModule)
fun Route.uploadToKindleRoute() {
    val kindleService = applicationModule.kindleService.value
    val transactionProvider = applicationModule.transactionProvider.value
    val storage = applicationModule.kindleUploadStorage.value
    val uploadEnabled = applicationModule.kindleUploadEnabled.value

    post("/kindle/uploads") {
        requireApprovedUser {
            if (!uploadEnabled) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    Error("Send to Kindle is not configured on this server"),
                )
                return@requireApprovedUser
            }

            try {
                val deviceId = call.requiredParameter<Int>("deviceId")

                // Device and quota are checked before the body is read, so a
                // rejected request never writes bytes to disk.
                transactionProvider.transaction(READ_ONLY) {
                    kindleService.checkUploadAllowed(deviceId)
                }

                val received = receiveUploadedBook(call.receiveMultipart(), storage)
                if (received == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        Error("A multipart part named '$FILE_PART_NAME' is required"),
                    )
                    return@requireApprovedUser
                }

                val result = try {
                    transactionProvider.transaction(READ_WRITE) {
                        kindleService.enqueueUpload(
                            deviceId = deviceId,
                            fileName = received.fileName,
                            storedPath = received.stored.fileName,
                            sizeBytes = received.stored.sizeBytes,
                            sourceFormat = received.format,
                        )
                    }
                } catch (e: CancellationException) {
                    // The commit may have gone through before the client left, so
                    // the row can already own this file. An unreferenced one is
                    // collected by the sweeper instead.
                    throw e
                } catch (e: Throwable) {
                    storage.delete(received.stored.fileName)
                    throw e
                }

                call.respond(HttpStatusCode.Accepted, Success(data = result))
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, Error(e.message ?: "Resource not found"))
            } catch (e: QuotaExceededException) {
                call.respond(HttpStatusCode.TooManyRequests, Error(e.message ?: "Quota exceeded"))
            } catch (e: UploadTooLargeException) {
                call.respond(HttpStatusCode.PayloadTooLarge, Error(e.message ?: "File too large"))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, Error(e.message ?: "Invalid request"))
            }
        }
    }
}

private class ReceivedUpload(
    val stored: StoredUpload,
    val fileName: String,
    val format: KindleUploadSourceFormat,
)

private suspend fun receiveUploadedBook(
    multipart: MultiPartData,
    storage: KindleUploadStorage,
): ReceivedUpload? {
    var received: ReceivedUpload? = null

    multipart.forEachPart { part ->
        try {
            if (received == null && part is PartData.FileItem && part.name == FILE_PART_NAME) {
                received = storeFilePart(part, storage)
            }
        } finally {
            part.dispose()
        }
    }

    return received
}

private suspend fun storeFilePart(
    part: PartData.FileItem,
    storage: KindleUploadStorage,
): ReceivedUpload {
    val fileName = sanitizeUploadFileName(part.originalFileName.orEmpty())
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val declaredFormat = KindleUploadSourceFormat.entries.find { it.extension == extension }
        ?: throw IllegalArgumentException("Only EPUB, PDF and FB2 files can be sent to Kindle")

    val stored = withContext(Dispatchers.IO) {
        part.provider().toInputStream().use { input ->
            storage.store(input, declaredFormat.extension)
        }
    }

    val header = withContext(Dispatchers.IO) {
        storage.resolve(stored.fileName).inputStream().use { input ->
            input.readNBytes(UPLOAD_HEADER_PROBE_BYTES)
        }
    }

    if (detectUploadFormat(fileName, header) != declaredFormat) {
        storage.delete(stored.fileName)
        throw IllegalArgumentException(
            "The file content does not look like ${declaredFormat.name}",
        )
    }

    return ReceivedUpload(
        stored = stored,
        fileName = fileName,
        format = declaredFormat,
    )
}
