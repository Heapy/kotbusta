package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.getKindleDevices
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getDevicesRoute() {
    get("/kindle/devices") {
        requireApprovedUser {
            val devices = getKindleDevices()
            call.respond(Success(data = devices))
        }
    }
}
