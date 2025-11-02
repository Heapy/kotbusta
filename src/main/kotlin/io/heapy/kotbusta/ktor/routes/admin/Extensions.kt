package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.ApiResponse.Error
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

context(routingContext: RoutingContext)
suspend fun requireAdminRights(
    body: suspend context(UserSession) () -> Unit,
) {
    val user = routingContext.call.sessions.get<UserSession>()

    if (user?.isAdmin == true) {
        context(user) {
            body()
        }
    } else {
        routingContext.call.respond(
            HttpStatusCode.Forbidden,
            Error(
                message = "Admin access required",
            ),
        )
    }
}
