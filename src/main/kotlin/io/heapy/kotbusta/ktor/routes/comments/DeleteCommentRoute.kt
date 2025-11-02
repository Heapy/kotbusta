package io.heapy.kotbusta.ktor.routes.comments

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.DeleteComment
import io.heapy.kotbusta.model.State.CommentId
import io.heapy.kotbusta.model.requireSuccess
import io.heapy.kotbusta.run
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.deleteCommentRoute() {
    delete("/comments/{id}") {
        requireApprovedUser {
            val commentId = CommentId(call.requiredParameter<Int>("id"))
            val result = applicationModule.run(DeleteComment(commentId))
            call.respond(Success(data = result.requireSuccess.result))
        }
    }
}
