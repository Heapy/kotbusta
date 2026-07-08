package io.heapy.kotbusta.ktor.routes.books

import io.heapy.komok.tech.logging.logger
import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getBookById
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.BookSearchResult
import io.heapy.kotbusta.model.PageMatchCount
import io.heapy.kotbusta.service.BookFileException
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val log = logger {}
private const val MIN_QUERY_LENGTH = 2

/**
 * Full-text search within a single already-parsed book, distinct from the catalog-level
 * `searchBooksRoute` (which indexes metadata across all books via Lucene). Reports only
 * which pages contain the term and how many times — no snippets/offsets, since the
 * frontend cycles through matches on whatever page is currently loaded and only needs to
 * know which page to jump to next.
 */
context(applicationModule: ApplicationModule)
fun Route.searchBookContentRoute() {
    val transactionProvider = applicationModule.transactionProvider.value
    val bookContentCacheService = applicationModule.bookContentCacheService.value

    get("/books/{id}/search") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val query = call.request.queryParameters["q"].orEmpty().trim()

            if (query.length < MIN_QUERY_LENGTH) {
                call.respond(
                    Success(
                        data = BookSearchResult(
                            id = bookId,
                            query = query,
                            totalMatches = 0,
                            pages = emptyList(),
                        ),
                    ),
                )
                return@requireApprovedUser
            }

            val book = transactionProvider
                .transaction(READ_ONLY) {
                    getBookById(bookId)
                }
                ?: notFoundError("Book $bookId not found")

            val parsed = try {
                bookContentCacheService.get(book)
            } catch (e: BookFileException) {
                log.warn("Book content not available for book $bookId: ${e.message}", e)
                notFoundError("Book content not available")
            }

            val pattern = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
            val matches = parsed.pages.mapIndexedNotNull { index, page ->
                val count = pattern.findAll(page.plainText).count()
                if (count > 0) PageMatchCount(page = index + 1, count = count) else null
            }

            call.respond(
                Success(
                    data = BookSearchResult(
                        id = book.id,
                        query = query,
                        totalMatches = matches.sumOf { it.count },
                        pages = matches,
                    ),
                ),
            )
        }
    }
}
