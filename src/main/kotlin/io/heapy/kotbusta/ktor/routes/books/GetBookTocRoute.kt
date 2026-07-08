package io.heapy.kotbusta.ktor.routes.books

import io.heapy.komok.tech.logging.logger
import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getBookById
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.BookToc
import io.heapy.kotbusta.service.BookFileException
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val log = logger {}

context(applicationModule: ApplicationModule)
fun Route.getBookTocRoute() {
    val transactionProvider = applicationModule.transactionProvider.value
    val bookContentCacheService = applicationModule.bookContentCacheService.value

    get("/books/{id}/toc") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")

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

            call.respond(
                Success(
                    data = BookToc(
                        id = book.id,
                        totalPages = parsed.pages.size.coerceAtLeast(1),
                        entries = parsed.toc,
                        anchors = parsed.anchorPageIndex,
                    ),
                ),
            )
        }
    }
}
