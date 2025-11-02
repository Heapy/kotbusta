package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State.Star
import kotlin.time.Clock
import kotlin.time.Instant

context(userSession: UserSession)
fun StarBook(
    bookId: BookId,
    createdAt: Instant = Clock.System.now(),
) = StarBook(
    bookId = bookId,
    userSession = userSession,
    createdAt = createdAt,
)

class StarBook(
    private val bookId: BookId,
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

        // Check if already starred
        if (user.stars.any { it.bookId == bookId }) {
            return SuccessResult(state, false)
        }

        val newStar = Star(bookId = bookId, eventTime = createdAt)
        val updatedUser = user.copy(stars = user.stars + newStar)

        return SuccessResult(
            state.copy(users = state.users + (userSession.userId to updatedUser)),
            true,
        )
    }
}
