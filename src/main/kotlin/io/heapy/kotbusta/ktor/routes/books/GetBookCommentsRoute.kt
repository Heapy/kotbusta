package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.BookId
import io.heapy.kotbusta.model.getBookComments
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getBookCommentsRoute() {
    get("/books/{id}/comments") {
        requireApprovedUser {
            val bookId = BookId(call.requiredParameter<Int>("id"))
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val comments = getBookComments(bookId, limit, offset)
            call.respond(Success(data = comments))
        }
    }
}
