package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getSendHistoryRoute() {
    val kindleService = applicationModule.kindleService.value
    val transactionProvider = applicationModule.transactionProvider.value

    get("/kindle/sends") {
        requireApprovedUser {
            val limit = call.request.queryParameters["limit"]
                ?.toIntOrNull()
                ?: 20
            val offset = call.request.queryParameters["offset"]
                ?.toIntOrNull()
                ?: 0

            val result = transactionProvider.transaction(READ_ONLY) {
                kindleService.getSendHistory(limit, offset)
            }
            call.respond(Success(data = result))
        }
    }
}