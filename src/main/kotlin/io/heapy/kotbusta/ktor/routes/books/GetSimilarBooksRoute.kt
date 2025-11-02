package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getSimilarBooks
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getSimilarBooksRoute() {
    val transactionProvider = applicationModule.applicationState.value

    get("/books/{id}/similar") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
            val books = transactionProvider.transaction(READ_ONLY) {
                getSimilarBooks(bookId, limit)
            }
            call.respond(Success(data = books))
        }
    }
}
