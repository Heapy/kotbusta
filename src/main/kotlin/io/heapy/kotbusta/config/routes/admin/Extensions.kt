package io.heapy.kotbusta.config.routes.admin

import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.service.AdminService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

context(routingContext: RoutingContext)
suspend fun AdminService.requireAdminRights(
    body: suspend context(UserSession?) () -> Unit,
) {
    val user = routingContext.call.sessions.get<UserSession>()

    if (isAdmin(user)) {
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
