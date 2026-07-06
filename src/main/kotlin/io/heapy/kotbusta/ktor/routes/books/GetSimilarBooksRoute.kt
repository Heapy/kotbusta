package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.pagination
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getSimilarBooksRoute() {
    val bookSearchService = applicationModule.bookSearchService.value

    get("/books/{id}/similar") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val limit = call.pagination(defaultLimit = 10).limit
            val books = bookSearchService.findSimilar(bookId, limit)
            call.respond(Success(data = books))
        }
    }
}
