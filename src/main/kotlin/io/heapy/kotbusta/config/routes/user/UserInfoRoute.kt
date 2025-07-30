package io.heapy.kotbusta.config.routes.user

import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.config.routes.requireUserSession
import io.heapy.kotbusta.model.ApiResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userInfoRoute() {
    get("/user/info") {
        requireUserSession {
            call.respond(
                ApiResponse(
                    success = true,
                    data = contextOf<UserSession>(),
                ),
            )
        }
    }
}
