package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getBooks
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.routes.pagination
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getBooksRoute() {
    val transactionProvider = applicationModule.transactionProvider.value

    get("/books") {
        requireApprovedUser {
            val (limit, offset) = call.pagination()
            val result = transactionProvider.transaction(READ_ONLY) {
                getBooks(
                    limit = limit,
                    offset = offset,
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
