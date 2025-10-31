package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.importRoute() {
    val adminService = applicationModule.adminService.value
    val importJobService = applicationModule.importJobService.value

    post("/import") {
        adminService.requireAdminRights {
            importJobService.startImport(application)

            call.respond(
                Success(
                    data = true,
                ),
            )
        }
    }
}
