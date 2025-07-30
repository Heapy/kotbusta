package io.heapy.kotbusta.config.routes

import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.model.ApiResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

context(routingContext: RoutingContext)
suspend fun requireUserSession(
    body: suspend context(UserSession) () -> Unit,
) {
    val user = routingContext.call.sessions.get<UserSession>()

    if (user != null) {
        context(user) {
            body()
        }
    } else {
        routingContext.call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse<Unit>(
                success = false,
                error = "Not authenticated",
            ),
        )
    }
}
