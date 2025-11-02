package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State.Download
import kotlin.time.Clock
import kotlin.time.Instant

context(userSession: UserSession)
fun RecordDownload(
    bookId: Int,
    format: String,
    createdAt: Instant = Clock.System.now(),
) = RecordDownload(
    bookId = bookId,
    format = format,
    userSession = userSession,
    createdAt = createdAt,
)

class RecordDownload(
    private val bookId: Int,
    private val format: String,
    private val userSession: UserSession,
    private val createdAt: Instant,
) : DatabaseOperation<Boolean> {
    override fun process(state: ApplicationState): OperationResult<Boolean> {
        val user = state.users[userSession.userId]
            ?: return ErrorResult("User not found")

        // Check if book exists
        if (!state.books.containsKey(bookId)) {
            return ErrorResult("Book not found")
        }

        val newDownload = Download(bookId = bookId, format = format, eventTime = createdAt)
        val updatedUser = user.copy(downloads = user.downloads + newDownload)

        return SuccessResult(
            state.copy(users = state.users + (userSession.userId to updatedUser)),
            true,
        )
    }
}
