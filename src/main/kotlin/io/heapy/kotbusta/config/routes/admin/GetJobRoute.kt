package io.heapy.kotbusta.config.routes.admin

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.notFoundError
import io.heapy.kotbusta.config.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.getJobRoute() {
    val adminService = applicationFactory.adminService.value

    get("/jobs/{id}") {
        adminService.requireAdminRights {
            val jobId = call.requiredParameter<String>("id")

            val job = adminService.getJob(jobId)
                ?: notFoundError("Job $jobId not found")

            call.respond(
                Success(
                    data = job,
                ),
            )
        }
    }
}

