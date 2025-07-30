package io.heapy.kotbusta.config.routes.books

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.config.routes.requireUserSession
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.getStarredBooksRoute() {
    val bookService = applicationFactory.bookService.value

    get("/books/starred") {
        requireUserSession {
            val user = contextOf<UserSession>()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            val result = bookService.getStarredBooks(user.userId, limit, offset)
            call.respond(Success(data = result))
        }
    }
}
