package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.SendToKindleRequest
import io.heapy.kotbusta.service.QuotaExceededException
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.sendToKindleRoute() {
    val kindleService = applicationModule.kindleService.value
    val transactionProvider = applicationModule.transactionProvider.value

    post("/books/{bookId}/send-to-kindle") {
        requireApprovedUser {
            try {
                val bookId = call.requiredParameter<Int>("bookId")

                val request = call.receive<SendToKindleRequest>()
                val result = transactionProvider.transaction(READ_WRITE) {
                    kindleService.enqueueSend(bookId, request)
                }
                call.respond(HttpStatusCode.Accepted, Success(data = result))
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, Error(e.message ?: "Resource not found"))
            } catch (e: QuotaExceededException) {
                call.respond(HttpStatusCode.TooManyRequests, Error(e.message ?: "Quota exceeded"))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, Error(e.message ?: "Invalid request"))
            }
        }
    }
}
