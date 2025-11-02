package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getBookById
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getBookByIdRoute() {
    val transactionProvider = applicationModule.applicationState.value

    get("/books/{id}") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val book = transactionProvider.transaction(READ_ONLY) {
                getBookById(bookId)
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
