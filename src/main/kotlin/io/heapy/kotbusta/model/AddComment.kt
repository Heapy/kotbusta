package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State.UserComment
import kotlin.time.Clock
import kotlin.time.Instant

context(userSession: UserSession)
fun AddComment(
    bookId: BookId,
    comment: String,
    createdAt: Instant = Clock.System.now(),
) = AddComment(
    bookId = bookId,
    comment = comment,
    userSession = userSession,
    createdAt = createdAt,
)

class AddComment(
    private val bookId: BookId,
    private val comment: String,
    private val userSession: UserSession,
    private val createdAt: Instant,
) : DatabaseOperation<UserComment> {
    override fun process(state: ApplicationState): OperationResult<UserComment> {
        // Check if user exists
        if (!state.users.containsKey(userSession.userId)) {
            return ErrorResult("User not found")
        }

        // Check if book exists
        if (!state.books.containsKey(bookId)) {
            return ErrorResult("Book not found")
        }

        val sequences = state.sequences.copy(commentId = state.sequences.commentId + 1)
        val newComment = UserComment(
            id = CommentId(sequences.commentId),
            userId = userSession.userId,
            bookId = bookId,
            comment = comment,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        val bookComments = state.comments[bookId] ?: emptyList()
        val updatedComments = state.comments + (bookId to (bookComments + newComment))

        return SuccessResult(
            state.copy(
                comments = updatedComments,
                sequences = sequences,
            ),
            newComment,
        )
    }
}
