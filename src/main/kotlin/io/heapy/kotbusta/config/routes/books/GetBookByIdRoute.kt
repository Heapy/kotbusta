package io.heapy.kotbusta.config.routes.books

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.routes.requireUserSession
import io.heapy.kotbusta.config.routes.requiredParameter
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.getBookByIdRoute() {
    val bookService = applicationFactory.bookService.value
    val transactionProvider = applicationFactory.transactionProvider.value

    get("/books/{id}") {
        requireUserSession {
            val bookId = call.requiredParameter<Long>("id")
            val book = transactionProvider.transaction(READ_ONLY) {
                bookService.getBookById(bookId)
            }
            if (book != null) {
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
