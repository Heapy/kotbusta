package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getBookById
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.badRequestError
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.service.BookFileException
import io.heapy.kotbusta.service.ConversionFormat
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.downloadBookRoute() {
    val transactionProvider = applicationModule.transactionProvider.value
    val conversionService = applicationModule.conversionService.value
    val bookFileService = applicationModule.bookFileService.value

    get("/books/{id}/download/{format}") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val format = call.requiredParameter<String>("format")

            val supportedFormats = listOf("fb2") + conversionService.getSupportedFormats()
            if (format !in supportedFormats) {
                badRequestError("Unsupported format: $format. Supported formats: ${supportedFormats.joinToString(", ")}")
            }

            val book = transactionProvider
                .transaction(READ_ONLY) {
                    getBookById(bookId)
                }
                ?: notFoundError("Book $bookId not found")

            val materialized = try {
                bookFileService.materialize(book, format)
            } catch (e: BookFileException) {
                notFoundError(e.message ?: "Book file not available")
            }

            try {
                applicationModule.prometheusRegistry.value
                    .counter("kotbusta_downloads_total", "format", materialized.format.lowercase())
                    .increment()
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        materialized.fileName,
                    ).toString(),
                )
                ConversionFormat.entries
                    .find { it.extension.equals(materialized.format, ignoreCase = true) }
                    ?.let { call.response.header(HttpHeaders.ContentType, it.mimeType) }

                call.respond(materialized.file.readBytes())
            } finally {
                materialized.cleanup()
            }
        }
    }
}
