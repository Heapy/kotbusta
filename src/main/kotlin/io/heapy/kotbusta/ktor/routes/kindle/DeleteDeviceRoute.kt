package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.DeleteKindleDevice
import io.heapy.kotbusta.model.State.KindleId
import io.heapy.kotbusta.model.requireSuccess
import io.heapy.kotbusta.run
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.deleteDeviceRoute() {
    delete("/kindle/devices/{deviceId}") {
        requireApprovedUser {
            val deviceId = KindleId(call.requiredParameter<Int>("deviceId"))

            val deleted = applicationModule
                .run(DeleteKindleDevice(deviceId))
                .requireSuccess
                .result

            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, Error("Device not found"))
            }
        }
    }
}
