package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.importRoute() {
    val adminService = applicationFactory.adminService.value

    post("/import") {
        adminService.requireAdminRights {
            val jobId = adminService.startDataImport()
            call.respond(
                Success(
                    data = mapOf("jobId" to jobId),
                ),
            )
        }
    }
}
