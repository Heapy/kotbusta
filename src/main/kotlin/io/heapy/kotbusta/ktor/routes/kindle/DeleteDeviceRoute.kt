package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Error
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.deleteDeviceRoute() {
    val kindleService = applicationModule.kindleService.value
    val transactionProvider = applicationModule.applicationState.value

    delete("/kindle/devices/{deviceId}") {
        requireApprovedUser {
            val deviceId = call.requiredParameter<Int>("deviceId")

            val deleted = transactionProvider.transaction(READ_WRITE) {
                kindleService.deleteDevice(deviceId)
            }

            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, Error("Device not found"))
            }
        }
    }
}
