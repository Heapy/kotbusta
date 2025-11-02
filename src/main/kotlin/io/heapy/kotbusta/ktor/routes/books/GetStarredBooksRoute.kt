package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getStarredBooks
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getStarredBooksRoute() {
    val transactionProvider = applicationModule.applicationState.value

    get("/books/starred") {
        requireApprovedUser {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val result = transactionProvider.transaction(READ_ONLY) {
                getStarredBooks(limit, offset)
            }
            call.respond(Success(data = result))
        }
    }
}
