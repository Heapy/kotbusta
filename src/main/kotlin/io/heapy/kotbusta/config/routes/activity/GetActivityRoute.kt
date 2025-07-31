package io.heapy.kotbusta.config.routes.activity

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.getActivityRoute() {
    val userService = applicationFactory.userService.value
    val transactionProvider = applicationFactory.transactionProvider.value

    get("/activity") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val activity = transactionProvider.transaction(READ_ONLY) {
            userService.getRecentActivity(limit)
        }
        call.respond(Success(data = activity))
    }
}
