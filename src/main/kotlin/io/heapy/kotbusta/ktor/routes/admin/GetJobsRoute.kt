package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getAllImportJobs
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getJobsRoute() {
    val adminService = applicationModule.adminService.value
    val transactionProvider = applicationModule.transactionProvider.value

    get("/jobs") {
        adminService.requireAdminRights {
            val jobs = transactionProvider.transaction(READ_ONLY) {
                getAllImportJobs()
            }

            call.respond(
                Success(
                    data = jobs,
                ),
            )
        }
    }
}
