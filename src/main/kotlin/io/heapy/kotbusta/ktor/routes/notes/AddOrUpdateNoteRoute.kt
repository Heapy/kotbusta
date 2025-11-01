package io.heapy.kotbusta.ktor.routes.notes

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class NoteRequest(
    val note: String,
)

context(applicationModule: ApplicationModule)
fun Route.addOrUpdateNoteRoute() {
    val userService = applicationModule.userService.value
    val transactionProvider = applicationModule.transactionProvider.value

    post("/books/{id}/notes") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val request = call.receive<NoteRequest>()
            val note = transactionProvider.transaction(READ_WRITE) {
                userService.addOrUpdateNote(
                    bookId = bookId,
                    note = request.note,
                )
            }

            if (note != null) {
                call.respond(Success(data = note))
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    Error(
                        message = "Failed to save note",
                    ),
                )
            }
        }
    }
}
