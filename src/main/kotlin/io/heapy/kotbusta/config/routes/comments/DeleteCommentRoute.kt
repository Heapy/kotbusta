package io.heapy.kotbusta.config.routes.comments

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.config.routes.requireUserSession
import io.heapy.kotbusta.config.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.deleteCommentRoute() {
    val userService = applicationFactory.userService.value

    delete("/comments/{id}") {
        requireUserSession {
            val commentId = call.requiredParameter<Long>("id")
            val user = contextOf<UserSession>()

            val success = userService.deleteComment(user.userId, commentId)
            call.respond(Success(data = success))
        }
    }
}
