package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.UpdateDeviceRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.updateDeviceRoute() {
    val kindleService = applicationModule.kindleService.value
    val transactionProvider = applicationModule.applicationState.value

    put("/kindle/devices/{deviceId}") {
        requireApprovedUser {
            try {
                val deviceId = call.requiredParameter<Int>("deviceId")

                val request = call.receive<UpdateDeviceRequest>()
                val device = transactionProvider.transaction(READ_WRITE) {
                    kindleService.updateDevice(deviceId, request)
                }
                call.respond(Success(data = device))
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, Error(e.message ?: "Device not found"))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, Error(e.message ?: "Invalid request"))
            }
        }
    }
}
