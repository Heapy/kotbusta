package io.heapy.kotbusta.ktor.routes.comments

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.State.CommentId
import io.heapy.kotbusta.model.UpdateComment
import io.heapy.kotbusta.model.requireSuccess
import io.heapy.kotbusta.run
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
    put("/comments/{id}") {
        requireApprovedUser {
            val commentId = CommentId(call.requiredParameter<Int>("id"))
            val request = call.receive<CommentRequest>()
            val result = applicationModule.run(UpdateComment(commentId, request.comment))
            call.respond(Success(data = result.requireSuccess.result))
        }
    }
}
