package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State.KindleId
import kotlin.time.Clock
import kotlin.time.Instant

context(userSession: UserSession)
fun UpdateKindleDevice(
    deviceId: KindleId,
    name: String,
    updatedAt: Instant = Clock.System.now(),
) = UpdateKindleDevice(
    deviceId = deviceId,
    name = name,
    userSession = userSession,
    updatedAt = updatedAt,
)

class UpdateKindleDevice(
    private val deviceId: KindleId,
    private val name: String,
    private val userSession: UserSession,
    private val updatedAt: Instant,
) : DatabaseOperation<Boolean> {
    override fun process(state: ApplicationState): OperationResult<Boolean> {
        val user = state.users[userSession.userId]
            ?: return ErrorResult("User not found")

        val device = user.kindleDevices.find { it.id == deviceId }
            ?: return ErrorResult("Device not found")

        val updatedDevice = device.copy(name = name)
        val updatedDevices = user.kindleDevices.map {
            if (it.id == deviceId) updatedDevice else it
        }

        val updatedUser = user.copy(kindleDevices = updatedDevices)

        return SuccessResult(
            state.copy(users = state.users + (userSession.userId to updatedUser)),
            true,
        )
    }
}
