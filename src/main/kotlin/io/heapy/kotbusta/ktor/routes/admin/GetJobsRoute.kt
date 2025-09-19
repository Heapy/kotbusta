package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.getJobsRoute() {
    val adminService = applicationFactory.adminService.value
    val importJobService = applicationFactory.importJobService.value
    val transactionProvider = applicationFactory.transactionProvider.value

    get("/jobs") {
        adminService.requireAdminRights {
            val jobs = transactionProvider.transaction(READ_ONLY) {
                importJobService.getAllJobs()
            }

            call.respond(
                Success(
                    data = jobs,
                ),
            )
        }
    }
}
