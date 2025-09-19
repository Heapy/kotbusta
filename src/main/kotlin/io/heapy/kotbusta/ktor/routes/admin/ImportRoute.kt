package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.importRoute() {
    val adminService = applicationFactory.adminService.value
    val importJobService = applicationFactory.importJobService.value
    val transactionProvider = applicationFactory.transactionProvider.value

    post("/import") {
        adminService.requireAdminRights {
            transactionProvider.transaction {
                importJobService.startImport(application)
            }

            call.respond(
                Success(
                    data = true,
                ),
            )
        }
    }
}
