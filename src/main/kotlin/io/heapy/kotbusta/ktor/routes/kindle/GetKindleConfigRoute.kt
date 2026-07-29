package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.KindleConfigResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getKindleConfigRoute() {
    val config = KindleConfigResponse(
        senderEmail = applicationModule.kindleSenderEmail.value,
    )

    get("/kindle/config") {
        requireApprovedUser {
            call.respond(Success(data = config))
        }
    }
}
