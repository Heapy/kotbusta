package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.unstarBook
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.unstarBookRoute() {
    val transactionProvider = applicationModule.transactionProvider.value

    delete("/books/{id}/star") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val success = transactionProvider.transaction(READ_WRITE) {
                unstarBook(bookId)
            }
            call.respond(Success(data = success))
        }
    }
}
