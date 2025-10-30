package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.CreateDeviceRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.createDeviceRoute() {
    val kindleService = applicationModule.kindleService.value
    val transactionProvider = applicationModule.transactionProvider.value

    post("/kindle/devices") {
        requireApprovedUser {
            try {
                val request = call.receive<CreateDeviceRequest>()
                val device = transactionProvider.transaction(READ_WRITE) {
                    kindleService.createDevice(request)
                }
                call.respond(HttpStatusCode.Created, Success(data = device))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, Error(e.message ?: "Invalid request"))
            }
        }
    }
}