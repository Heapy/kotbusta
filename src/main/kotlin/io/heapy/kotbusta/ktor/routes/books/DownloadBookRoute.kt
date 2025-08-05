package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.ktor.badRequestError
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requireUserSession
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.service.ConversionFormat
import io.heapy.kotbusta.service.PandocConversionService
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
    val transactionProvider = applicationFactory.transactionProvider.value
    val conversionService = PandocConversionService()

    get("/books/{id}/download/{format}") {
        requireUserSession {
            val bookId = call.requiredParameter<Long>("id")
            val format = call.requiredParameter<String>("format")

            val supportedFormats = listOf("fb2") + conversionService.getSupportedFormats()
            if (format !in supportedFormats) {
                badRequestError("Unsupported format: $format. Supported formats: ${supportedFormats.joinToString(", ")}")
            }

            val book = transactionProvider
                .transaction(READ_ONLY) {
                    bookService.getBookById(bookId)
                }
                ?: notFoundError("Book $bookId not found")

            transactionProvider
                .transaction(READ_WRITE) {
                    userService.recordDownload(bookId, format)
                }

            when (format) {
                "fb2" -> {
                    val archiveFile =
                        booksDataPath.resolve("${book.archivePath}.zip")
                    if (archiveFile.exists()) {
                        val zipFile = ZipFile(archiveFile.toFile())
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
                            call.respond(
                                HttpStatusCode.NotImplemented,
                                Error(
                                    message = "FB2 download not implemented yet",
                                ),
                            )
                        }
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            Error(
                                message = "Book file not found",
                            ),
                        )
                    }
                }

                else -> {
                    val archiveFile = booksDataPath.resolve("${book.archivePath}.zip")
                    if (!archiveFile.exists()) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            Error(message = "Book file not found")
                        )
                        return@requireUserSession
                    }

                    val zipFile = ZipFile(archiveFile.toFile())
                    val fb2Entry = zipFile.entries().asSequence()
                        .find { it.name == book.filePath }

                    if (fb2Entry == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            Error(message = "FB2 file not found in archive")
                        )
                        return@requireUserSession
                    }

                    val tempDir = File(System.getProperty("java.io.tmpdir"), "kotbusta-conversions")
                    tempDir.mkdirs()

                    val tempFb2File = File(tempDir, "${bookId}_${book.filePath}")
                    tempFb2File.writeBytes(zipFile.getInputStream(fb2Entry).readBytes())

                    val outputFileName = "${book.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.${format}"
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
