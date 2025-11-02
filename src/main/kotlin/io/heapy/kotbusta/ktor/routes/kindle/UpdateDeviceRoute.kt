package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.State.KindleId
import io.heapy.kotbusta.model.UpdateDeviceRequest
import io.heapy.kotbusta.model.UpdateKindleDevice
import io.heapy.kotbusta.model.getKindleDevice
import io.heapy.kotbusta.model.requireSuccess
import io.heapy.kotbusta.model.toDeviceResponse
import io.heapy.kotbusta.run
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.updateDeviceRoute() {
    put("/kindle/devices/{deviceId}") {
        requireApprovedUser {
            val deviceId = KindleId(call.requiredParameter<Int>("deviceId"))
            val request = call.receive<UpdateDeviceRequest>()

            applicationModule.run(UpdateKindleDevice(deviceId, request.name))
                .requireSuccess

            // Get updated device to return
            val updatedDevice = getKindleDevice(deviceId)
                ?: error("Device not found after update")

            call.respond(Success(data = toDeviceResponse(updatedDevice)))
        }
    }
}
