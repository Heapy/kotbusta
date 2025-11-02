package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.getBook
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getBookCoverRoute() {
    val coverService = applicationModule.coverService.value
    val booksDataPath = applicationModule.booksDataPath.value

    get("/books/{id}/cover") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")

            // Get the archive path for the book from in-memory state
            val book = getBook(bookId)
                ?: notFoundError("Book ${bookId} not found")

            // Resolve full archive path
            val fullArchivePath = booksDataPath.resolve("${book.archivePath}.zip").toString()

            // Extract cover on-demand from the archive
            val coverImage = coverService.extractCoverForBook(fullArchivePath, bookId)

            if (coverImage != null) {
                call.respondBytes(coverImage, ContentType.Image.JPEG)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
