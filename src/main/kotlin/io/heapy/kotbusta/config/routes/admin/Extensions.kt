package io.heapy.kotbusta.config.routes.admin

import io.heapy.kotbusta.config.UserSession
import io.heapy.kotbusta.model.ApiResponse
import io.heapy.kotbusta.service.AdminService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

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
            ApiResponse<Unit>(
                success = false,
                error = "Admin access required",
            ),
        )
    }
}
