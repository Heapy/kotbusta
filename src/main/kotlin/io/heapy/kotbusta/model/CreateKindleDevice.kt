package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State.KindleDevice
import io.heapy.kotbusta.model.State.KindleId

context(userSession: UserSession)
fun CreateKindleDevice(
    request: CreateDeviceRequest,
) = CreateKindleDevice(
    request = request,
    userSession = userSession,
)

class CreateKindleDevice(
    private val request: CreateDeviceRequest,
    private val userSession: UserSession,
) : DatabaseOperation<KindleDevice> {
    override fun process(state: ApplicationState): OperationResult<KindleDevice> {
        val user = state.users[userSession.userId]!!

        val existing = user.kindleDevices.find { it.email == request.email }

        return if (existing == null) {
            val sequences =
                state.sequences.copy(kindleId = state.sequences.kindleId + 1)
            val device = KindleDevice(
                id = KindleId(sequences.kindleId),
                email = request.email,
                name = request.name,
            )
            val updatedUser =
                user.copy(kindleDevices = user.kindleDevices + device)
            SuccessResult(
                state.copy(
                    users = state.users + (userSession.userId to updatedUser),
                    sequences = sequences,
                ),
                device,
            )
        } else {
            ErrorResult("A device with this email already exists")
        }
    }
}
