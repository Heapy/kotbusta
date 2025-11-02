package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.badRequestError
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.CreateDeviceRequest
import io.heapy.kotbusta.model.CreateKindleDevice
import io.heapy.kotbusta.model.ErrorResult
import io.heapy.kotbusta.model.SuccessResult
import io.heapy.kotbusta.run
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.createDeviceRoute() {
    post("/kindle/devices") {
        requireApprovedUser {
            val request = call.receive<CreateDeviceRequest>()

            if (!request.email.endsWith("@kindle.com", ignoreCase = true)) {
                badRequestError("Email must be a Kindle email")
            }

            val response = applicationModule
                .run(CreateKindleDevice(request))

            val device = when (response) {
                is SuccessResult -> response.result
                is ErrorResult -> badRequestError(response.message)
            }
            call.respond(HttpStatusCode.Created, Success(data = device))
        }
    }
}
