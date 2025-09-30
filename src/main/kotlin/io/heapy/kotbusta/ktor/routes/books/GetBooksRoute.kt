package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.ktor.routes.requireUserSession
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getBooksRoute() {
    val bookService = applicationModule.bookService.value
    val transactionProvider = applicationModule.transactionProvider.value

    get("/books") {
        requireUserSession {
            val limit = call.request.queryParameters["limit"]
                ?.toIntOrNull()
                ?: 20
            val offset = call.request.queryParameters["offset"]
                ?.toIntOrNull()
                ?: 0
            val userId = contextOf<UserSession>().userId
            val result = transactionProvider.transaction(READ_ONLY) {
                bookService.getBooks(
                    limit = limit,
                    offset = offset,
                    userId = userId,
                )
            }
            call.respond(
                Success(
                    data = result,
                ),
            )
        }
    }
}
