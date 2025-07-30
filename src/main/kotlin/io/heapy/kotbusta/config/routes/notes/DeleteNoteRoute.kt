package io.heapy.kotbusta.config.routes.notes

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.config.routes.requireUserSession
import io.heapy.kotbusta.config.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.deleteNoteRoute() {
    val userService = applicationFactory.userService.value

    delete("/books/{id}/notes") {
        requireUserSession {
            val bookId = call.requiredParameter<Long>("id")
            val user = contextOf<UserSession>()

            val success = userService.deleteNote(user.userId, bookId)
            call.respond(Success(data = success))
        }
    }
}
