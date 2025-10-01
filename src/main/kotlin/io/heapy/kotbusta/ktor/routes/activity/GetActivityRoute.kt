package io.heapy.kotbusta.ktor.routes.activity

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getActivityRoute() {
    val userService = applicationModule.userService.value
    val transactionProvider = applicationModule.transactionProvider.value

    get("/activity") {
        requireApprovedUser {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val activity = transactionProvider.transaction(READ_ONLY) {
                userService.getRecentActivity(limit)
            }
            call.respond(Success(data = activity))
        }
    }
}
