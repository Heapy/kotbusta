package io.heapy.kotbusta.config

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.routes.admin.adminRoutes
import io.heapy.kotbusta.config.routes.auth.googleOauthRoutes
import io.heapy.kotbusta.config.routes.auth.loginRoute
import io.heapy.kotbusta.config.routes.auth.logoutRoute
import io.heapy.kotbusta.config.routes.books.getBooksRoute
import io.heapy.kotbusta.config.routes.staticFilesRoute
import io.heapy.kotbusta.config.routes.user.userInfoRoute
import io.heapy.kotbusta.model.ApiResponse
import io.heapy.kotbusta.model.SearchQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class CommentRequest(
    val comment: String,
)

@Serializable
data class NoteRequest(
    val note: String,
    val isPrivate: Boolean = true,
)

@Serializable
data class ImportRequest(
    val extractCovers: Boolean = false,
)

context(applicationFactory: ApplicationFactory)
fun Application.configureRouting() {
    val userService = applicationFactory.userService.value
    val bookService = applicationFactory.bookService.value

    routing {
        staticFilesRoute()
        loginRoute()
        logoutRoute()
        googleOauthRoutes()

        route("/api") {
            authenticate("auth-session") {
                adminRoutes()
                userInfoRoute()
                getBooksRoute()

                get("/books/search") {
                    val query = call.request.queryParameters["q"] ?: ""
                    val genre = call.request.queryParameters["genre"]
                    val language = call.request.queryParameters["language"]
                    val author = call.request.queryParameters["author"]
                    val limit =
                        call.request.queryParameters["limit"]?.toIntOrNull()
                            ?: 20
                    val offset =
                        call.request.queryParameters["offset"]?.toIntOrNull()
                            ?: 0
                    val user = call.sessions.get<UserSession>()!!

                    val searchQuery = SearchQuery(
                        query,
                        genre,
                        language,
                        author,
                        limit,
                        offset,
                    )
                    val result =
                        bookService.searchBooks(searchQuery, user.userId)
                    call.respond(ApiResponse(success = true, data = result))
                }

                get("/books/{id}") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    if (bookId == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Invalid book ID",
                            ),
                        )
                        return@get
                    }

                    val user = call.sessions.get<UserSession>()!!
                    val book = bookService.getBookById(bookId, user.userId)

                    if (book != null) {
                        call.respond(ApiResponse(success = true, data = book))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Book not found",
                            ),
                        )
                    }
                }

                get("/books/{id}/similar") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    if (bookId == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Invalid book ID",
                            ),
                        )
                        return@get
                    }

                    val limit =
                        call.request.queryParameters["limit"]?.toIntOrNull()
                            ?: 10
                    val user = call.sessions.get<UserSession>()!!
                    val books =
                        bookService.getSimilarBooks(bookId, limit, user.userId)

                    call.respond(ApiResponse(success = true, data = books))
                }

                get("/books/{id}/cover") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    if (bookId == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }

                    val coverImage = bookService.getBookCover(bookId)
                    if (coverImage != null) {
                        call.respondBytes(coverImage, ContentType.Image.JPEG)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                get("/books/{id}/comments") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    if (bookId == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Invalid book ID",
                            ),
                        )
                        return@get
                    }

                    val limit =
                        call.request.queryParameters["limit"]?.toIntOrNull()
                            ?: 20
                    val offset =
                        call.request.queryParameters["offset"]?.toIntOrNull()
                            ?: 0
                    val comments =
                        userService.getBookComments(bookId, limit, offset)

                    call.respond(ApiResponse(success = true, data = comments))
                }

                get("/activity") {
                    val limit =
                        call.request.queryParameters["limit"]?.toIntOrNull()
                            ?: 20
                    val activity = userService.getRecentActivity(limit)
                    call.respond(ApiResponse(success = true, data = activity))
                }

                get("/books/starred") {
                    val user = call.sessions.get<UserSession>()!!
                    val limit =
                        call.request.queryParameters["limit"]?.toIntOrNull()
                            ?: 20
                    val offset =
                        call.request.queryParameters["offset"]?.toIntOrNull()
                            ?: 0

                    val result =
                        bookService.getStarredBooks(user.userId, limit, offset)
                    call.respond(ApiResponse(success = true, data = result))
                }

                post("/books/{id}/star") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()

                    if (bookId == null || user == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Invalid request",
                            ),
                        )
                        return@post
                    }

                    val success = bookService.starBook(user.userId, bookId)
                    call.respond(ApiResponse(success = success, data = success))
                }

                delete("/books/{id}/star") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()

                    if (bookId == null || user == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Invalid request",
                            ),
                        )
                        return@delete
                    }

                    val success = bookService.unstarBook(user.userId, bookId)
                    call.respond(ApiResponse(success = success, data = success))
                }

                post("/books/{id}/comments") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()

                    if (bookId == null || user == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Invalid request",
                            ),
                        )
                        return@post
                    }

                    val request = call.receive<CommentRequest>()
                    val comment = userService.addComment(
                        user.userId,
                        bookId,
                        request.comment,
                    )

                    if (comment != null) {
                        call.respond(
                            ApiResponse(
                                success = true,
                                data = comment,
                            ),
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Failed to add comment",
                            ),
                        )
                    }
                }

                put("/comments/{id}") {
                    val commentId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()

                    if (commentId == null || user == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Invalid request",
                            ),
                        )
                        return@put
                    }

                    val request = call.receive<CommentRequest>()
                    val success = userService.updateComment(
                        user.userId,
                        commentId,
                        request.comment,
                    )
                    call.respond(ApiResponse(success = success, data = success))
                }

                delete("/comments/{id}") {
                    val commentId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()

                    if (commentId == null || user == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Invalid request",
                            ),
                        )
                        return@delete
                    }

                    val success =
                        userService.deleteComment(user.userId, commentId)
                    call.respond(ApiResponse(success = success, data = success))
                }

                post("/books/{id}/notes") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()

                    if (bookId == null || user == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Invalid request",
                            ),
                        )
                        return@post
                    }

                    val request = call.receive<NoteRequest>()
                    val note = userService.addOrUpdateNote(
                        user.userId,
                        bookId,
                        request.note,
                        request.isPrivate,
                    )

                    if (note != null) {
                        call.respond(ApiResponse(success = true, data = note))
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Failed to save note",
                            ),
                        )
                    }
                }

                delete("/books/{id}/notes") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()

                    if (bookId == null || user == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Invalid request",
                            ),
                        )
                        return@delete
                    }

                    val success = userService.deleteNote(user.userId, bookId)
                    call.respond(ApiResponse(success = success, data = success))
                }

                get("/books/{id}/download/{format}") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    val format = call.parameters["format"]
                    val user = call.sessions.get<UserSession>()

                    if (bookId == null || format == null || user == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Invalid request",
                            ),
                        )
                        return@get
                    }

                    if (format !in listOf("fb2", "epub", "mobi")) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Unsupported format",
                            ),
                        )
                        return@get
                    }

                    val book = bookService.getBookById(bookId, user.userId)
                    if (book == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Unit>(
                                success = false,
                                error = "Book not found",
                            ),
                        )
                        return@get
                    }

                    // Record download
                    userService.recordDownload(user.userId, bookId, format)

                    when (format) {
                        "fb2" -> {
                            // Serve original FB2 file
                            val archiveFile = File(book.archivePath)
                            if (archiveFile.exists()) {
                                // TODO: Extract FB2 from archive and serve
                                call.respond(
                                    HttpStatusCode.NotImplemented,
                                    ApiResponse<Unit>(
                                        success = false,
                                        error = "FB2 download not implemented yet",
                                    ),
                                )
                            } else {
                                call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse<Unit>(
                                        success = false,
                                        error = "Book file not found",
                                    ),
                                )
                            }
                        }

                        "epub", "mobi" -> {
                            // TODO: Call conversion service
                            call.respond(
                                HttpStatusCode.NotImplemented,
                                ApiResponse<Unit>(
                                    success = false,
                                    error = "Conversion service not implemented yet",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
