package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.ktor.routes.requireUserSession
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CommentRequest(
    val comment: String,
)

context(applicationFactory: ApplicationFactory)
fun Route.addBookCommentRoute() {
    val userService = applicationFactory.userService.value
    val transactionProvider = applicationFactory.transactionProvider.value

    post("/books/{id}/comments") {
        requireUserSession {
            val bookId = call.requiredParameter<Long>("id")

            val request = call.receive<CommentRequest>()
            val comment = transactionProvider.transaction(READ_WRITE) {
                userService.addComment(
                    bookId,
                    request.comment,
                )
            }

            if (comment != null) {
                call.respond(
                    Success(
                        data = comment,
                    ),
                )
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    Error(
                        message = "Failed to add comment",
                    ),
                )
            }
        }
    }
}
