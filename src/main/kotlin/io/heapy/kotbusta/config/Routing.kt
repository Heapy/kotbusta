package io.heapy.kotbusta.config

import io.heapy.kotbusta.model.*
import io.heapy.kotbusta.service.BookService
import io.heapy.kotbusta.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class CommentRequest(
    val comment: String
)

@Serializable
data class NoteRequest(
    val note: String,
    val isPrivate: Boolean = true
)

fun Application.configureRouting() {
    val bookService = BookService()
    val userService = UserService()
    val oauthConfigured = environment.config.propertyOrNull("kotbusta.google.clientId")?.getString() != null &&
                         environment.config.propertyOrNull("kotbusta.google.clientSecret")?.getString() != null
    
    routing {
        // Static files
        singlePageApplication {
            filesPath = "static"
            useResources = true
        }

        // Authentication routes
        get("/login") {
            if (oauthConfigured) {
                call.application.environment.log.info("Login requested, redirecting to OAuth")
                call.respondRedirect("/oauth/google")
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, "OAuth not configured. Please set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET environment variables.")
            }
        }
        
        if (oauthConfigured) {
            authenticate("google-oauth") {
                get("/oauth/google") {
                    // Redirects to Google OAuth
                }
                
                get("/callback") {
                    // The OAuth plugin handles the callback and sets the principal
                    val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                    if (principal != null) {
                        try {
                            val userSession = handleGoogleCallback(principal)
                            call.sessions.set(userSession)
                            call.respondRedirect("/")
                        } catch (e: Exception) {
                            call.application.environment.log.error("Error handling OAuth callback", e)
                            call.respond(HttpStatusCode.InternalServerError, "Error processing authentication")
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, "Authentication failed")
                    }
                }
            }
        }
        
        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/")
        }
        
        // API routes - all require authentication
        route("/api") {
            // Check user session for all API routes
            authenticate("auth-session") {
                get("/user/info") {
                    val user = call.sessions.get<UserSession>()
                    if (user != null) {
                        call.respond(ApiResponse(success = true, data = user))
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(success = false, error = "Not authenticated"))
                    }
                }
                
                get("/books") {
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val user = call.sessions.get<UserSession>()!!
                    
                    val result = bookService.getBooks(limit, offset, user.userId)
                    call.respond(ApiResponse(success = true, data = result))
                }
                
                get("/books/search") {
                    val query = call.request.queryParameters["q"] ?: ""
                    val genre = call.request.queryParameters["genre"]
                    val language = call.request.queryParameters["language"]
                    val author = call.request.queryParameters["author"]
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val user = call.sessions.get<UserSession>()!!
                    
                    val searchQuery = SearchQuery(query, genre, language, author, limit, offset)
                    val result = bookService.searchBooks(searchQuery, user.userId)
                    call.respond(ApiResponse(success = true, data = result))
                }
                
                get("/books/{id}") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    if (bookId == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid book ID"))
                        return@get
                    }
                    
                    val user = call.sessions.get<UserSession>()!!
                    val book = bookService.getBookById(bookId, user.userId)
                    
                    if (book != null) {
                        call.respond(ApiResponse(success = true, data = book))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, error = "Book not found"))
                    }
                }
                
                get("/books/{id}/similar") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    if (bookId == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid book ID"))
                        return@get
                    }
                    
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                    val user = call.sessions.get<UserSession>()!!
                    val books = bookService.getSimilarBooks(bookId, limit, user.userId)
                    
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
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid book ID"))
                        return@get
                    }
                    
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val comments = userService.getBookComments(bookId, limit, offset)
                    
                    call.respond(ApiResponse(success = true, data = comments))
                }
                
                get("/activity") {
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                    val activity = userService.getRecentActivity(limit)
                    call.respond(ApiResponse(success = true, data = activity))
                }
                
                get("/books/starred") {
                    val user = call.sessions.get<UserSession>()!!
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    
                    val result = bookService.getStarredBooks(user.userId, limit, offset)
                    call.respond(ApiResponse(success = true, data = result))
                }
                
                post("/books/{id}/star") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()
                    
                    if (bookId == null || user == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid request"))
                        return@post
                    }
                    
                    val success = bookService.starBook(user.userId, bookId)
                    call.respond(ApiResponse(success = success, data = success))
                }
                
                delete("/books/{id}/star") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()
                    
                    if (bookId == null || user == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid request"))
                        return@delete
                    }
                    
                    val success = bookService.unstarBook(user.userId, bookId)
                    call.respond(ApiResponse(success = success, data = success))
                }
                
                post("/books/{id}/comments") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()
                    
                    if (bookId == null || user == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid request"))
                        return@post
                    }
                    
                    val request = call.receive<CommentRequest>()
                    val comment = userService.addComment(user.userId, bookId, request.comment)
                    
                    if (comment != null) {
                        call.respond(ApiResponse(success = true, data = comment))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Failed to add comment"))
                    }
                }
                
                put("/comments/{id}") {
                    val commentId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()
                    
                    if (commentId == null || user == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid request"))
                        return@put
                    }
                    
                    val request = call.receive<CommentRequest>()
                    val success = userService.updateComment(user.userId, commentId, request.comment)
                    call.respond(ApiResponse(success = success, data = success))
                }
                
                delete("/comments/{id}") {
                    val commentId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()
                    
                    if (commentId == null || user == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid request"))
                        return@delete
                    }
                    
                    val success = userService.deleteComment(user.userId, commentId)
                    call.respond(ApiResponse(success = success, data = success))
                }
                
                post("/books/{id}/notes") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()
                    
                    if (bookId == null || user == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid request"))
                        return@post
                    }
                    
                    val request = call.receive<NoteRequest>()
                    val note = userService.addOrUpdateNote(user.userId, bookId, request.note, request.isPrivate)
                    
                    if (note != null) {
                        call.respond(ApiResponse(success = true, data = note))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Failed to save note"))
                    }
                }
                
                delete("/books/{id}/notes") {
                    val bookId = call.parameters["id"]?.toLongOrNull()
                    val user = call.sessions.get<UserSession>()
                    
                    if (bookId == null || user == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid request"))
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
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid request"))
                        return@get
                    }
                    
                    if (format !in listOf("fb2", "epub", "mobi")) {
                        call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Unsupported format"))
                        return@get
                    }
                    
                    val book = bookService.getBookById(bookId, user.userId)
                    if (book == null) {
                        call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, error = "Book not found"))
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
                                call.respond(HttpStatusCode.NotImplemented, ApiResponse<Unit>(success = false, error = "FB2 download not implemented yet"))
                            } else {
                                call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, error = "Book file not found"))
                            }
                        }
                        "epub", "mobi" -> {
                            // TODO: Call conversion service
                            call.respond(HttpStatusCode.NotImplemented, ApiResponse<Unit>(success = false, error = "Conversion service not implemented yet"))
                        }
                    }
                }
            }
        }
//
//        // Catch-all route for SPA
//        get("/{...}") {
//            val indexFile = this::class.java.classLoader.getResource("static/index.html")
//            if (indexFile != null) {
//                call.respondText(indexFile.readText(), ContentType.Text.Html)
//            } else {
//                call.respond(HttpStatusCode.NotFound)
//            }
//        }
    }
}