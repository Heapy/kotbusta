package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getBookCoverRoute() {
    val bookService = applicationModule.bookService.value
    val transactionProvider = applicationModule.transactionProvider.value

    get("/books/{id}/cover") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Long>("id")
            val coverImage = transactionProvider.transaction(READ_ONLY) {
                bookService.getBookCover(bookId)
            }
            if (coverImage != null) {
                call.respondBytes(coverImage, ContentType.Image.JPEG)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
