package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.extractCoversRoute() {
    val adminService = applicationFactory.adminService.value

    post("/extract-covers") {
        adminService.requireAdminRights {
            val jobId = adminService.startCoverExtraction()

            call.respond(
                Success(
                    data = mapOf("jobId" to jobId),
                ),
            )
        }
    }
}
