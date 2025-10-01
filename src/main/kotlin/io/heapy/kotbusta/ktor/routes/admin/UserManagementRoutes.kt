package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationModule
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
        val ctx = this
        context(ctx) {
            adminService.requireAdminRights {
                val routingCall = contextOf<RoutingContext>().call
                val limit = routingCall.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = routingCall.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val response = transactionProvider.transaction {
                    userApprovalService.listPending(limit, offset)
                }

                routingCall.respond(Success(data = response))
            }
        }
    }

    post("/users/{userId}/approve") {
        val ctx = this
        context(ctx) {
            adminService.requireAdminRights {
                val routingCall = contextOf<RoutingContext>().call
                val userId = routingCall.requiredParameter<Long>("userId")

                val success = transactionProvider.transaction {
                    userApprovalService.approve(userId)
                }

                if (success) {
                    routingCall.respond(Success(data = mapOf("message" to "User approved successfully")))
                } else {
                    routingCall.respond(
                        HttpStatusCode.NotFound,
                        Error(message = "User not found")
                    )
                }
            }
        }
    }

    post("/users/{userId}/reject") {
        val ctx = this
        context(ctx) {
            adminService.requireAdminRights {
                val routingCall = contextOf<RoutingContext>().call
                val userId = routingCall.requiredParameter<Long>("userId")

                val success = transactionProvider.transaction {
                    userApprovalService.reject(userId)
                }

                if (success) {
                    routingCall.respond(Success(data = mapOf("message" to "User rejected successfully")))
                } else {
                    routingCall.respond(
                        HttpStatusCode.NotFound,
                        Error(message = "User not found")
                    )
                }
            }
        }
    }

    post("/users/{userId}/deactivate") {
        val ctx = this
        context(ctx) {
            adminService.requireAdminRights {
                val routingCall = contextOf<RoutingContext>().call
                val userId = routingCall.requiredParameter<Long>("userId")

                val success = transactionProvider.transaction {
                    userApprovalService.deactivate(userId)
                }

                if (success) {
                    routingCall.respond(Success(data = mapOf("message" to "User deactivated successfully")))
                } else {
                    routingCall.respond(
                        HttpStatusCode.NotFound,
                        Error(message = "User not found")
                    )
                }
            }
        }
    }
}