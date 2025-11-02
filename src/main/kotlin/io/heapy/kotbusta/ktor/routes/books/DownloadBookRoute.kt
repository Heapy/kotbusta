package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.badRequestError
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.BookId
import io.heapy.kotbusta.model.RecordDownload
import io.heapy.kotbusta.model.getBook
import io.heapy.kotbusta.model.toBook
import io.heapy.kotbusta.run
import io.heapy.kotbusta.service.ConversionFormat
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.exists

context(applicationModule: ApplicationModule)
fun Route.downloadBookRoute() {
    val booksDataPath = applicationModule.booksDataPath.value
    val conversionService = applicationModule.conversionService.value

    get("/books/{id}/download/{format}") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val format = call.requiredParameter<String>("format")

            val supportedFormats = listOf("fb2") + conversionService.getSupportedFormats()
            if (format !in supportedFormats) {
                badRequestError("Unsupported format: $format. Supported formats: ${supportedFormats.joinToString(", ")}")
            }

            val parsedBook = getBook(BookId(bookId))
                ?: notFoundError("Book $bookId not found")

            // Record the download
            applicationModule.run(RecordDownload(BookId(bookId)))

            val book = toBook(parsedBook)

            when (format) {
                "fb2" -> {
                    val archiveFile = booksDataPath.resolve("${book.archivePath}.zip")
                    if (archiveFile.exists()) {
                        ZipFile(archiveFile.toFile()).use { zipFile ->
                            val fb2File = zipFile.entries().asSequence()
                                .find { it.name == book.filePath }
                            if (fb2File != null) {
                                val fileBytes =
                                    zipFile.getInputStream(fb2File).readBytes()
                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Attachment.withParameter(
                                        ContentDisposition.Parameters.FileName,
                                        book.filePath,
                                    ).toString(),
                                )
                                call.respond(fileBytes)
                            } else {
                                notFoundError("FB2 file not found in archive $book")
                            }
                        }
                    } else {
                        notFoundError("Book file not found $book")
                    }
                }

                else -> {
                    val archiveFile = booksDataPath.resolve("${book.archivePath}.zip")
                    if (!archiveFile.exists()) {
                        notFoundError("Book file not found $book")
                    }

                    ZipFile(archiveFile.toFile()).use { zipFile ->
                        val fb2Entry = zipFile.entries().asSequence()
                            .find { it.name == book.filePath }

                        if (fb2Entry == null) {
                            notFoundError("FB2 file not found in archive $book")
                        }

                        val tempDir = File(System.getProperty("java.io.tmpdir"), "kotbusta-conversions")
                        tempDir.mkdirs()

                        val tempFb2File = File(tempDir, "${bookId}_${book.filePath}")
                        tempFb2File.writeBytes(zipFile.getInputStream(fb2Entry).readBytes())

                        // Sanitize filename: handle empty titles, limit length, remove unsafe characters
                        val sanitizedTitle = book.title
                            .ifBlank { "book_${bookId}" }
                            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                            .take(100)
                        val outputFileName = "${sanitizedTitle}.${format}"
                        val outputFile = File(tempDir, "${bookId}_${outputFileName}")

                        try {
                            val conversionResult = conversionService.convertFb2(
                                inputFile = tempFb2File,
                                outputFormat = format,
                                outputFile = outputFile
                            )

                            if (conversionResult.success && conversionResult.outputFile != null) {
                                val convertedBytes = conversionResult.outputFile.readBytes()
                                val conversionFormat = ConversionFormat.values()
                                    .find { it.extension.equals(format, ignoreCase = true) }

                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Attachment.withParameter(
                                        ContentDisposition.Parameters.FileName,
                                        outputFileName
                                    ).toString()
                                )

                                if (conversionFormat != null) {
                                    call.response.header(HttpHeaders.ContentType, conversionFormat.mimeType)
                                }

                                call.respond(convertedBytes)
                            } else {
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    Error(message = "Conversion failed: ${conversionResult.errorMessage}")
                                )
                            }
                        } finally {
                            tempFb2File.delete()
                            outputFile.delete()
                        }
                    }
                }
            }
        }
    }
}
