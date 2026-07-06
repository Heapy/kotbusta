package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.importRoute() {
    val importJobService = applicationModule.importJobService.value

    post("/import") {
        val started = importJobService.startImport(application)

        if (started) {
            call.respond(
                Success(
                    data = true,
                ),
            )
        } else {
            call.respond(
                HttpStatusCode.Conflict,
                Error(
                    message = "Cannot start import: another job is already running",
                ),
            )
        }
    }
}
