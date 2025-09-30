package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.statusRoute() {
    val adminService = applicationModule.adminService.value

    get("/status") {
        adminService.requireAdminRights {
            call.respond(
                Success(
                    data = mapOf("isAdmin" to true),
                ),
            )
        }
    }
}
