package io.heapy.kotbusta.ktor.routes

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getUserInfo
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.UserStatus.APPROVED
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

context(routingContext: RoutingContext, applicationModule: ApplicationModule)
suspend fun requireApprovedUser(
    body: suspend context(UserSession) () -> Unit,
) {
    val userSession = routingContext.call.sessions.get<UserSession>()

    if (userSession == null) {
        routingContext.call.respond(
            HttpStatusCode.Unauthorized,
            Error(
                message = "Not authenticated",
            ),
        )
        return
    }

    val adminService = applicationModule.adminService.value
    val transactionProvider = applicationModule.transactionProvider.value

    // Check if user is admin (admins bypass approval check)
    if (adminService.isAdmin(userSession)) {
        context(userSession) {
            body()
        }
        return
    }

    // Check if user is approved
    val userInfo = transactionProvider.transaction(READ_ONLY) {
        context(userSession) {
            getUserInfo()
        }
    }

    if (userInfo?.status == APPROVED) {
        context(userSession) {
            body()
        }
    } else {
        routingContext.call.respond(
            HttpStatusCode.Forbidden,
            Error(
                message = "Account pending approval. Please wait for admin approval to access this resource.",
            ),
        )
    }
}
