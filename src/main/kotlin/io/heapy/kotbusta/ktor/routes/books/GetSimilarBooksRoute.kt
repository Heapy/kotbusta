package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.BookId
import io.heapy.kotbusta.model.getSimilarBooks
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getSimilarBooksRoute() {
    get("/books/{id}/similar") {
        requireApprovedUser {
            val bookId = BookId(call.requiredParameter<Int>("id"))
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
            val books = getSimilarBooks(bookId, limit)
            call.respond(Success(data = books))
        }
    }
}
