package io.heapy.kotbusta.ktor.routes.user

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.ktor.routes.requireUserSession
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userInfoRoute() {
    get("/user/info") {
        requireUserSession {
            call.respond(
                Success(
                    data = contextOf<UserSession>(),
                ),
            )
        }
    }
}
