package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.starBook
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.starBookRoute() {
    val transactionProvider = applicationModule.transactionProvider.value

    post("/books/{id}/star") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val success = transactionProvider.transaction(READ_WRITE) {
                starBook(bookId)
            }
            call.respond(Success(data = success))
        }
    }
}


