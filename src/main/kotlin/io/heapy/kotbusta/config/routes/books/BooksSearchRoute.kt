package io.heapy.kotbusta.config.routes.books

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.config.routes.requireUserSession
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.SearchQuery
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.bookSearchRoute() {
    val bookService = applicationFactory.bookService.value

    get("/books/search") {
        val query = call.request.queryParameters["q"].orEmpty()
        val genre = call.request.queryParameters["genre"]
        val language = call.request.queryParameters["language"]
        val author = call.request.queryParameters["author"]
        val limit = call.request.queryParameters["limit"]
            ?.toIntOrNull()
            ?: 20
        val offset = call.request.queryParameters["offset"]
            ?.toIntOrNull()
            ?: 0

        requireUserSession {
            val userId = contextOf<UserSession>().userId

            val searchQuery = SearchQuery(
                query,
                genre,
                language,
                author,
                limit,
                offset,
            )
            val result = bookService.searchBooks(
                query = searchQuery,
                userId = userId,
            )
            call.respond(
                Success(
                    data = result,
                ),
            )
        }
    }
}
