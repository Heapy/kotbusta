package io.heapy.kotbusta.ktor.routes.books

import io.heapy.komok.tech.logging.logger
import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getBookById
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.BookContent
import io.heapy.kotbusta.service.BookFileException
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val log = logger {}

context(applicationModule: ApplicationModule)
fun Route.getBookContentRoute() {
    val transactionProvider = applicationModule.transactionProvider.value
    val bookContentService = applicationModule.bookContentService.value

    get("/books/{id}/content") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")

            val book = transactionProvider
                .transaction(READ_ONLY) {
                    getBookById(bookId)
                }
                ?: notFoundError("Book $bookId not found")

            val rendered = try {
                bookContentService.render(book)
            } catch (e: BookFileException) {
                log.warn("Book content not available for book $bookId: ${e.message}", e)
                notFoundError("Book content not available")
            }

            applicationModule.prometheusRegistry.value
                .counter("kotbusta_book_reads_total")
                .increment()

            // Book content is immutable; let the browser cache it. Private because the
            // endpoint is session-authenticated.
            call.response.header(HttpHeaders.CacheControl, "private, max-age=86400, immutable")
            call.respond(
                Success(
                    data = BookContent(
                        id = book.id,
                        title = book.title,
                        html = rendered.html,
                        hasImages = rendered.hasImages,
                        truncated = rendered.truncated,
                    ),
                ),
            )
        }
    }
}
