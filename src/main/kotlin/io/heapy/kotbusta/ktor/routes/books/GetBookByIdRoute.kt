package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.getBook
import io.heapy.kotbusta.model.toBook
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getBookByIdRoute() {
    get("/books/{id}") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val parsedBook = getBook(bookId)

            if (parsedBook != null) {
                val book = toBook(parsedBook)
                call.respond(Success(data = book))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    Error(
                        message = "Book not found",
                    ),
                )
            }
        }
    }
}
