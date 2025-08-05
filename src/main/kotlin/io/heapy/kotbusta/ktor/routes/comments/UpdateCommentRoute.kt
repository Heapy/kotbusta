package io.heapy.kotbusta.ktor.routes.comments

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.ktor.routes.requireUserSession
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CommentRequest(
    val comment: String,
)

context(applicationFactory: ApplicationFactory)
fun Route.updateCommentRoute() {
    val userService = applicationFactory.userService.value
    val transactionProvider = applicationFactory.transactionProvider.value

    put("/comments/{id}") {
        requireUserSession {
            val commentId = call.requiredParameter<Long>("id")
            val request = call.receive<CommentRequest>()
            val success = transactionProvider.transaction(READ_WRITE) {
                userService.updateComment(
                    commentId,
                    request.comment,
                )
            }
            call.respond(Success(data = success))
        }
    }
}
