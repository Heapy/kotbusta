package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getDevicesRoute() {
    val kindleService = applicationModule.kindleService.value
    val transactionProvider = applicationModule.transactionProvider.value

    get("/kindle/devices") {
        requireApprovedUser {
            val devices = transactionProvider.transaction(READ_ONLY) {
                kindleService.getUserDevices()
            }
            call.respond(Success(data = devices))
        }
    }
}