package io.heapy.kotbusta.config.routes.admin

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.ImportRequest
import io.heapy.kotbusta.model.ApiResponse
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.importRoute() {
    val adminService = applicationFactory.adminService.value

    post("/import") {
        adminService.requireAdminRights {
            val request = call.receive<ImportRequest>()
            val jobId = adminService.startDataImport(
                request.extractCovers,
            )
            call.respond(
                ApiResponse(
                    success = true,
                    data = mapOf("jobId" to jobId),
                ),
            )
        }
    }
}
