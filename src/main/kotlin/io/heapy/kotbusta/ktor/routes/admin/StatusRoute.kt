package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.statusRoute() {
    val adminService = applicationFactory.adminService.value

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
