package io.heapy.kotbusta.config.routes.books

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.config.routes.requireUserSession
import io.heapy.kotbusta.config.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.unstarBookRoute() {
    val bookService = applicationFactory.bookService.value

    delete("/books/{id}/star") {
        requireUserSession {
            val bookId = call.requiredParameter<Long>("id")
            val user = contextOf<UserSession>()

            val success = bookService.unstarBook(user.userId, bookId)
            call.respond(Success(data = success))
        }
    }
}
