package io.heapy.kotbusta.ktor.routes.user

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getUserInfo
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.routes.requireUserSession
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.userInfoRoute() {
    val transactionProvider = applicationModule.transactionProvider.value

    get("/me") {
        requireUserSession {
            val userInfo = transactionProvider
                .transaction(READ_ONLY) {
                    getUserInfo()
                }
                ?: error("User not found")

            call.respond(Success(data = userInfo))
        }
    }
}
