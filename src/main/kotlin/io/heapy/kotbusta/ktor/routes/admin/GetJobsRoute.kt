package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getJobsRoute() {
    val adminService = applicationModule.adminService.value
    val jobStatsService = applicationModule.jobStatsService.value

    get("/jobs") {
        adminService.requireAdminRights {
            val jobs = jobStatsService.getAllJobs()

            call.respond(
                Success(
                    data = jobs,
                ),
            )
        }
    }
}
