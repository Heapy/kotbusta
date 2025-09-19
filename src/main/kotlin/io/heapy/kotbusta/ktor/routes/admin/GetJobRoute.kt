package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.service.ImportJobService.JobId
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.getJobRoute() {
    val adminService = applicationFactory.adminService.value
    val importJobService = applicationFactory.importJobService.value
    val transactionProvider = applicationFactory.transactionProvider.value

    get("/jobs/{id}") {
        adminService.requireAdminRights {
            val jobId = call
                .requiredParameter<Long>("id")
                .let(::JobId)

            val job = transactionProvider.transaction(READ_ONLY) {
                importJobService
                    .getJob(jobId)
                    ?: notFoundError("Job $jobId not found")
            }

            call.respond(
                Success(
                    data = job,
                ),
            )
        }
    }
}

