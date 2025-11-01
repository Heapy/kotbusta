package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.BOOKS
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getBookCoverRoute() {
    val transactionProvider = applicationModule.transactionProvider.value
    val coverService = applicationModule.coverService.value

    get("/books/{id}/cover") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")

            // Get the archive path for the book from database
            val archivePath = transactionProvider.transaction(READ_ONLY) {
                useTx { dslContext ->
                    dslContext
                        .select(BOOKS.ARCHIVE_PATH)
                        .from(BOOKS)
                        .where(BOOKS.ID.eq(bookId))
                        .fetchOne(BOOKS.ARCHIVE_PATH)
                }
            }

            if (archivePath == null) {
                notFoundError("Book $bookId not found")
            }

            // Extract cover on-demand from the archive
            val coverImage = coverService.extractCoverForBook(archivePath, bookId)

            if (coverImage != null) {
                call.respondBytes(coverImage, ContentType.Image.JPEG)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
