package io.heapy.kotbusta.ktor.routes.comments

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CommentRequest(
    val comment: String,
)

context(applicationModule: ApplicationModule)
fun Route.updateCommentRoute() {
    val userService = applicationModule.userService.value

    put("/comments/{id}") {
        requireApprovedUser {
            val commentId = call.requiredParameter<Int>("id")
            val request = call.receive<CommentRequest>()
            val success =  userService.updateComment(
                commentId,
                request.comment,
            )
            call.respond(Success(data = success))
        }
    }
}
