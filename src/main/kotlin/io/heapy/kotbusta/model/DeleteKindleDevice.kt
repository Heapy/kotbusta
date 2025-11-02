package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State.KindleId

context(userSession: UserSession)
fun DeleteKindleDevice(
    deviceId: KindleId,
) = DeleteKindleDevice(
    deviceId = deviceId,
    userSession = userSession,
)

class DeleteKindleDevice(
    private val deviceId: KindleId,
    private val userSession: UserSession,
) : DatabaseOperation<Boolean> {
    override fun process(state: ApplicationState): OperationResult<Boolean> {
        val user = state.users[userSession.userId]
            ?: return ErrorResult("User not found")

        val updatedDevices = user.kindleDevices.filterNot { it.id == deviceId }

        // If nothing changed, return false
        if (updatedDevices.size == user.kindleDevices.size) {
            return SuccessResult(state, false)
        }

        val updatedUser = user.copy(kindleDevices = updatedDevices)

        return SuccessResult(
            state.copy(users = state.users + (userSession.userId to updatedUser)),
            true,
        )
    }
}
