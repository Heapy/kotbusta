package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession

context(userSession: UserSession)
fun UnstarBook(
    bookId: BookId,
) = UnstarBook(
    bookId = bookId,
    userSession = userSession,
)

class UnstarBook(
    private val bookId: BookId,
    private val userSession: UserSession,
) : DatabaseOperation<Boolean> {
    override fun process(state: ApplicationState): OperationResult<Boolean> {
        val user = state.users[userSession.userId]
            ?: return ErrorResult("User not found")

        // Filter out the star for this book
        val newStars = user.stars.filterNot { it.bookId == bookId }

        // If nothing changed, return false
        if (newStars.size == user.stars.size) {
            return SuccessResult(state, false)
        }

        val updatedUser = user.copy(stars = newStars)

        return SuccessResult(
            state.copy(users = state.users + (userSession.userId to updatedUser)),
            true,
        )
    }
}
