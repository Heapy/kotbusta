package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.searchBooks
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.bookSearchRoute() {
    get("/books/search") {
        val query = call.request.queryParameters["q"]
        val genre = call.request.queryParameters["genre"]
        val language = call.request.queryParameters["language"]
        val author = call.request.queryParameters["author"]
        val limit = call.request.queryParameters["limit"]
            ?.toIntOrNull()
            ?: 20
        val offset = call.request.queryParameters["offset"]
            ?.toIntOrNull()
            ?: 0

        requireApprovedUser {
            val result = searchBooks(
                query = query,
                genre = genre,
                language = language,
                author = author,
                limit = limit,
                offset = offset,
            )
            call.respond(
                Success(
                    data = result,
                ),
            )
        }
    }
}
