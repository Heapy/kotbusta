package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.userManagementRoutes() {
    val adminService = applicationModule.adminService.value
    val userApprovalService = applicationModule.userApprovalService.value
    val transactionProvider = applicationModule.transactionProvider.value

    get("/users/pending") {
        adminService.requireAdminRights {
            val limit = call.requiredParameter<Int>("limit")
            val offset = call.requiredParameter<Int>("offset")

            val response = transactionProvider.transaction(READ_ONLY) {
                userApprovalService.listPending(limit, offset)
            }

            call.respond(Success(data = response))
        }
    }

    post("/users/{userId}/approve") {
        adminService.requireAdminRights {
            val userId = call.requiredParameter<Long>("userId")

            val success = transactionProvider.transaction(READ_WRITE) {
                userApprovalService.approve(userId)
            }

            if (success) {
                call.respond(
                    Success(
                        data = mapOf(
                            "message" to "User approved successfully",
                        ),
                    ),
                )
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    Error(message = "User not found"),
                )
            }
        }
    }

    post("/users/{userId}/reject") {
        adminService.requireAdminRights {
            val userId = call.requiredParameter<Long>("userId")

            val success = transactionProvider.transaction(READ_WRITE) {
                userApprovalService.reject(userId)
            }

            if (success) {
                call.respond(
                    Success(
                        data = mapOf(
                            "message" to "User rejected successfully",
                        ),
                    ),
                )
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    Error(message = "User not found"),
                )
            }
        }
    }

    post("/users/{userId}/deactivate") {
        adminService.requireAdminRights {
            val userId = call.requiredParameter<Long>("userId")

            val success = transactionProvider.transaction(READ_WRITE) {
                userApprovalService.deactivate(userId)
            }

            if (success) {
                call.respond(
                    Success(
                        data = mapOf(
                            "message" to "User deactivated successfully",
                        ),
                    ),
                )
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    Error(message = "User not found"),
                )
            }
        }
    }
}
