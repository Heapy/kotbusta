package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State.KindleId
import io.heapy.kotbusta.model.State.KindleSendEvent
import io.heapy.kotbusta.model.State.SendEventId
import kotlin.time.Clock
import kotlin.time.Instant

context(userSession: UserSession)
fun CreateKindleSendEvent(
    deviceId: KindleId,
    bookId: Int,
    format: KindleFormat,
    status: KindleSendStatus = KindleSendStatus.PENDING,
    lastError: String? = null,
    createdAt: Instant = Clock.System.now(),
) = CreateKindleSendEvent(
    deviceId = deviceId,
    bookId = bookId,
    format = format,
    status = status,
    lastError = lastError,
    userSession = userSession,
    createdAt = createdAt,
)

class CreateKindleSendEvent(
    private val deviceId: KindleId,
    private val bookId: Int,
    private val format: KindleFormat,
    private val status: KindleSendStatus,
    private val lastError: String?,
    private val userSession: UserSession,
    private val createdAt: Instant,
) : DatabaseOperation<KindleSendEvent> {
    override fun process(state: ApplicationState): OperationResult<KindleSendEvent> {
        val user = state.users[userSession.userId]
            ?: return ErrorResult("User not found")

        // Check if device exists and belongs to user
        if (!user.kindleDevices.any { it.id == deviceId }) {
            return ErrorResult("Device not found")
        }

        // Check if book exists
        if (!state.books.containsKey(bookId)) {
            return ErrorResult("Book not found")
        }

        val sequences = state.sequences.copy(sendEventId = state.sequences.sendEventId + 1)
        val event = KindleSendEvent(
            id = SendEventId(sequences.sendEventId),
            userId = userSession.userId,
            deviceId = deviceId,
            bookId = bookId,
            format = format,
            status = status,
            lastError = lastError,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        val updatedEvents = state.sendEvents + (event.id to event)

        return SuccessResult(
            state.copy(
                sendEvents = updatedEvents,
                sequences = sequences,
            ),
            event,
        )
    }
}
