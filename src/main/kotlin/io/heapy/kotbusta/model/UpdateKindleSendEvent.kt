package io.heapy.kotbusta.model

import io.heapy.kotbusta.model.State.SendEventId
import kotlin.time.Clock
import kotlin.time.Instant

class UpdateKindleSendEvent(
    private val eventId: SendEventId,
    private val status: KindleSendStatus,
    private val lastError: String?,
    private val updatedAt: Instant = Clock.System.now(),
) : DatabaseOperation<Boolean> {
    override fun process(state: ApplicationState): OperationResult<Boolean> {
        val event = state.sendEvents[eventId]
            ?: return ErrorResult("Send event not found")

        val updatedEvent = event.copy(
            status = status,
            lastError = lastError,
            updatedAt = updatedAt,
        )

        val updatedEvents = state.sendEvents + (eventId to updatedEvent)

        return SuccessResult(
            state.copy(sendEvents = updatedEvents),
            true,
        )
    }
}
