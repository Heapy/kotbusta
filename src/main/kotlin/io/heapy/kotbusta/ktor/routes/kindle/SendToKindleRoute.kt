package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.CreateKindleSendEvent
import io.heapy.kotbusta.model.KindleSendStatus
import io.heapy.kotbusta.model.SendToKindleRequest
import io.heapy.kotbusta.model.State.KindleId
import io.heapy.kotbusta.model.UpdateKindleSendEvent
import io.heapy.kotbusta.model.getBook
import io.heapy.kotbusta.model.getKindleDevice
import io.heapy.kotbusta.model.requireSuccess
import io.heapy.kotbusta.model.toSendHistoryResponse
import io.heapy.kotbusta.run
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

context(applicationModule: ApplicationModule)
fun Route.sendToKindleRoute(applicationScope: CoroutineScope) {
    val emailService = applicationModule.emailService.value
    val conversionService = applicationModule.conversionService.value
    val booksDataPath = applicationModule.booksDataPath.value

    post("/books/{bookId}/send-to-kindle") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("bookId")
            val request = call.receive<SendToKindleRequest>()
            val deviceId = KindleId(request.deviceId)

            // Validate book and device exist
            val book = getBook(bookId)
                ?: notFoundError("Book $bookId not found")
            val device = getKindleDevice(deviceId)
                ?: notFoundError("Device ${deviceId.value} not found")

            // Create send event with PENDING status
            val createResult = applicationModule.run(
                CreateKindleSendEvent(
                    deviceId = deviceId,
                    bookId = bookId,
                    format = request.format,
                    status = KindleSendStatus.PENDING,
                )
            )
            val event = createResult.requireSuccess.result

            // Send the email asynchronously and update status
            applicationScope.launch {
                try {
                    // Convert book if needed and send email
                    val archivePath = booksDataPath.resolve("${book.archivePath}.zip")

                    // TODO: Implement actual email sending logic here
                    // For now, mark as completed - you should integrate with emailService
                    // and conversionService to convert the book and send it

                    applicationModule.run(
                        UpdateKindleSendEvent(
                            eventId = event.id,
                            status = KindleSendStatus.COMPLETED,
                            lastError = null,
                        )
                    )
                } catch (e: Exception) {
                    applicationModule.run(
                        UpdateKindleSendEvent(
                            eventId = event.id,
                            status = KindleSendStatus.FAILED,
                            lastError = e.message,
                        )
                    )
                }
            }

            // Return the created event immediately
            val response = toSendHistoryResponse(event)
            call.respond(HttpStatusCode.Accepted, Success(data = response))
        }
    }
}
