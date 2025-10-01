package io.heapy.kotbusta.ktor.routes.user

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.ktor.routes.requireUserSession
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.userInfoRoute() {
    val userApprovalService = applicationModule.userApprovalService.value
    val transactionProvider = applicationModule.transactionProvider.value

    get("/user/info") {
        requireUserSession {
            val userSession = contextOf<UserSession>()
            val userInfo = transactionProvider
                .transaction(READ_ONLY) {
                    userApprovalService.getUserInfo(userSession.userId)
                }
                ?: error("User not found")

            call.respond(Success(data = userInfo))
        }
    }
}
