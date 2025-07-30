package io.heapy.kotbusta.config.routes.admin

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.getJobsRoute() {
    val adminService = applicationFactory.adminService.value

    get("/jobs") {
        adminService.requireAdminRights {
            val jobs = adminService.getAllJobs()
            call.respond(
                Success(
                    data = jobs,
                ),
            )
        }
    }
}
