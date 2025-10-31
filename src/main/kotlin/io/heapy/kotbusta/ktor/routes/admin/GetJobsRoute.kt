package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.toImportJob
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getJobsRoute() {
    val adminService = applicationModule.adminService.value
    val importJobService = applicationModule.importJobService.value

    get("/jobs") {
        adminService.requireAdminRights {
            call.respond(
                Success(
                    data = importJobService
                        .stats()
                        .toImportJob(),
                ),
            )
        }
    }
}
