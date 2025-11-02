package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession

context(userSession: UserSession)
fun DeleteComment(
    commentId: CommentId,
) = DeleteComment(
    commentId = commentId,
    userSession = userSession,
)

class DeleteComment(
    private val commentId: CommentId,
    private val userSession: UserSession,
) : DatabaseOperation<Boolean> {
    override fun process(state: ApplicationState): OperationResult<Boolean> {
        // Find the comment across all books
        var foundBookId: BookId? = null

        for ((bookId, comments) in state.comments) {
            if (comments.any { it.id == commentId && it.userId == userSession.userId }) {
                foundBookId = bookId
                break
            }
        }

        if (foundBookId == null) {
            return ErrorResult("Comment not found or not owned by user")
        }

        val bookComments = state.comments[foundBookId]!!
        val updatedBookComments = bookComments.filterNot {
            it.id == commentId && it.userId == userSession.userId
        }

        // If nothing changed, return false
        if (updatedBookComments.size == bookComments.size) {
            return SuccessResult(state, false)
        }

        val updatedComments = state.comments + (foundBookId to updatedBookComments)

        return SuccessResult(
            state.copy(comments = updatedComments),
            true,
        )
    }
}
