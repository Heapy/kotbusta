package io.heapy.kotbusta.ktor.routes.search

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.SearchQuery
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.searchBooksRoute() {
    val bookSearchService = applicationModule.bookSearchService.value

    get("/search/books") {
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
                query = query,
                genre = genre,
                language = language,
                author = author,
                limit = limit,
                offset = offset,
            )
            val result = bookSearchService.search(
                query = searchQuery,
                userId = contextOf<UserSession>().userId,
            )

            call.respond(
                Success(
                    data = result,
                ),
            )
        }
    }
}
