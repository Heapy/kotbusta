package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.notFoundError
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.ChangeUserStatus
import io.heapy.kotbusta.model.ErrorResult
import io.heapy.kotbusta.model.PendingUsersResponse
import io.heapy.kotbusta.model.State.UserId
import io.heapy.kotbusta.model.SuccessResult
import io.heapy.kotbusta.model.UserStatus
import io.heapy.kotbusta.model.getUsers
import io.heapy.kotbusta.model.toUserInfo
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.userManagementRoutes() {
    val database = applicationModule.database.value

    get("/users") {
        requireAdminRights {
            val response = getUsers().map { (_, user) ->
                user.toUserInfo()
            }

            call.respond(Success(data = response))
        }
    }

    get("/users/pending") {
        requireAdminRights {
            val users = getUsers()
                .values
                .filter { it.status == UserStatus.PENDING }
                .map { user -> user.id }

            val response = PendingUsersResponse(
                users = users,
            )

            call.respond(Success(data = response))
        }
    }

    suspend fun RoutingContext.updateUserStatus(
        newStatus: UserStatus,
    ) {
        requireAdminRights {
            val userId = call.requiredParameter<Int>("userId")
                .let(::UserId)

            val operation = ChangeUserStatus(
                userId = userId,
                newStatus = newStatus,
            )

            when (database.run(operation)) {
                is SuccessResult<*> -> call.respond(
                    Success(
                        data = mapOf(
                            "message" to "User updated successfully",
                        ),
                    ),
                )

                is ErrorResult -> notFoundError("User $userId not found")
            }
        }
    }

    post("/users/{userId}/approve") {
        updateUserStatus(UserStatus.APPROVED)
    }

    post("/users/{userId}/reject") {
        updateUserStatus(UserStatus.REJECTED)
    }

    post("/users/{userId}/deactivate") {
        updateUserStatus(UserStatus.DEACTIVATED)
    }
}
