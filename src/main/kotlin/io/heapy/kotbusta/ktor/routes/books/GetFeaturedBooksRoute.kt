package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.LIVELIB_TOP_SOURCE
import io.heapy.kotbusta.dao.getFeaturedBooks
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.routes.pagination
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getFeaturedBooksRoute() {
    val transactionProvider = applicationModule.transactionProvider.value

    get("/books/featured") {
        requireApprovedUser {
            val limit = call.pagination(defaultLimit = 20).limit
            val books = transactionProvider.transaction(READ_ONLY) {
                getFeaturedBooks(source = LIVELIB_TOP_SOURCE, limit = limit)
            }
            call.respond(Success(data = books))
        }
    }
}
