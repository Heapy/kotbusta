package io.heapy.kotbusta.config.routes.comments

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.config.routes.requireUserSession
import io.heapy.kotbusta.config.routes.requiredParameter
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

    put("/comments/{id}") {
        requireUserSession {
            val commentId = call.requiredParameter<Long>("id")
            val user = contextOf<UserSession>()

            val request = call.receive<CommentRequest>()

            val success = userService.updateComment(
                user.userId,
                commentId,
                request.comment,
            )

            call.respond(Success(data = success))
        }
    }
}
