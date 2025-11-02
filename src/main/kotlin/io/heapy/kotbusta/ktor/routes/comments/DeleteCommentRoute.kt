package io.heapy.kotbusta.ktor.routes.comments

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.deleteCommentRoute() {
    val userService = applicationModule.userService.value

    delete("/comments/{id}") {
        requireApprovedUser {
            val commentId = call.requiredParameter<Int>("id")
            val success = userService.deleteComment(commentId)
            call.respond(Success(data = success))
        }
    }
}
