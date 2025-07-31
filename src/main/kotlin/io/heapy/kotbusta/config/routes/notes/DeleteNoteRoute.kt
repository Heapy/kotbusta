package io.heapy.kotbusta.config.routes.notes

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.routes.requireUserSession
import io.heapy.kotbusta.config.routes.requiredParameter
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.deleteNoteRoute() {
    val userService = applicationFactory.userService.value
    val transactionProvider = applicationFactory.transactionProvider.value

    delete("/books/{id}/notes") {
        requireUserSession {
            val bookId = call.requiredParameter<Long>("id")

            val success = transactionProvider.transaction(READ_WRITE) {
                userService.deleteNote(bookId)
            }
            call.respond(Success(data = success))
        }
    }
}
