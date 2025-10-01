package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.SearchQuery
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.bookSearchRoute() {
    val bookService = applicationModule.bookService.value
    val transactionProvider = applicationModule.transactionProvider.value

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

        requireApprovedUser {
            val searchQuery = SearchQuery(
                query,
                genre,
                language,
                author,
                limit,
                offset,
            )
            val result = transactionProvider.transaction(READ_ONLY) {
                bookService.searchBooks(
                    query = searchQuery,
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
