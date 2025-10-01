package io.heapy.kotbusta.ktor.routes.user

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.ktor.routes.requireUserSession
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.UserInfo
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.userInfoRoute() {
    val userApprovalService = applicationModule.userApprovalService.value
    val transactionProvider = applicationModule.transactionProvider.value

    get("/user/info") {
        requireUserSession {
            val userSession = contextOf<UserSession>()
            val status = transactionProvider.transaction {
                userApprovalService.getUserStatus(userSession.userId)
            } ?: io.heapy.kotbusta.model.UserStatus.PENDING

            call.respond(
                Success(
                    data = UserInfo(
                        userId = userSession.userId,
                        email = userSession.email,
                        name = userSession.name,
                        avatarUrl = null,
                        status = status
                    ),
                ),
            )
        }
    }
}
