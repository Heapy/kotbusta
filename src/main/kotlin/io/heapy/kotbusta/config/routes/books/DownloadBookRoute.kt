package io.heapy.kotbusta.config.routes.books

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.config.badRequestError
import io.heapy.kotbusta.config.notFoundError
import io.heapy.kotbusta.config.routes.requireUserSession
import io.heapy.kotbusta.config.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Error
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.exists

context(applicationFactory: ApplicationFactory)
fun Route.downloadBookRoute() {
    val bookService = applicationFactory.bookService.value
    val userService = applicationFactory.userService.value
    val booksDataPath = applicationFactory.booksDataPath.value

    get("/books/{id}/download/{format}") {
        requireUserSession {
            val bookId = call.requiredParameter<Long>("id")
            val format = call.requiredParameter<String>("format")
            val user = contextOf<UserSession>()

            if (format !in listOf("fb2", "epub", "mobi")) {
                badRequestError("Unsupported format: $format")
            }

            val book = bookService.getBookById(bookId, user.userId)
                ?: notFoundError("Book $bookId not found")

            // Record download
            userService.recordDownload(user.userId, bookId, format)

            when (format) {
                "fb2" -> {
                    val archiveFile = booksDataPath.resolve("${book.archivePath}.zip")
                    if (archiveFile.exists()) {
                        // CLAUDE, implement this as a service
                        val zipFile = ZipFile(archiveFile.toFile())
                        val fb2File = zipFile.entries().asSequence().find { it.name == book.filePath }
                        if (fb2File != null) {
                            val fileBytes = zipFile.getInputStream(fb2File).readBytes()
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(
                                    ContentDisposition.Parameters.FileName,
                                    book.filePath
                                ).toString()
                            )
                            call.respond(fileBytes)
                        } else {
                            call.respond(
                                HttpStatusCode.NotImplemented,
                                Error(
                                    message =  "FB2 download not implemented yet",
                                ),
                            )
                        }
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            Error(
                                message =  "Book file not found",
                            ),
                        )
                    }
                }

                "epub", "mobi" -> {
                    // TODO: Call conversion service
                    call.respond(
                        HttpStatusCode.NotImplemented,
                        Error(
                            message = "Conversion service not implemented yet",
                        ),
                    )
                }
            }
        }
    }
}
