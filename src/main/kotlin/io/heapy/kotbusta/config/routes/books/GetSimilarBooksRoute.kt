package io.heapy.kotbusta.config.routes.books

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.routes.requireUserSession
import io.heapy.kotbusta.config.routes.requiredParameter
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.getSimilarBooksRoute() {
    val bookService = applicationFactory.bookService.value
    val transactionProvider = applicationFactory.transactionProvider.value

    get("/books/{id}/similar") {
        requireUserSession {
            val bookId = call.requiredParameter<Long>("id")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
            val books = transactionProvider.transaction(READ_ONLY) {
                bookService.getSimilarBooks(bookId, limit)
            }
            call.respond(Success(data = books))
        }
    }
}
