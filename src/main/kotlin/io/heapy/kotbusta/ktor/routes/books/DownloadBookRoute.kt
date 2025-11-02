package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getBookById
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.ktor.badRequestError
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.service.ConversionFormat
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.exists

context(applicationModule: ApplicationModule)
fun Route.downloadBookRoute() {
    val userService = applicationModule.userService.value
    val booksDataPath = applicationModule.booksDataPath.value
    val transactionProvider = applicationModule.applicationState.value
    val conversionService = applicationModule.conversionService.value

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

            transactionProvider
                .transaction(READ_WRITE) {
                    userService.recordDownload(bookId, format)
                }

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
