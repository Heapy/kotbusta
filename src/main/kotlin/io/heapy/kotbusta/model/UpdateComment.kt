package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession
import kotlin.time.Clock
import kotlin.time.Instant

context(userSession: UserSession)
fun UpdateComment(
    commentId: CommentId,
    comment: String,
    updatedAt: Instant = Clock.System.now(),
) = UpdateComment(
    commentId = commentId,
    comment = comment,
    userSession = userSession,
    updatedAt = updatedAt,
)

class UpdateComment(
    private val commentId: CommentId,
    private val comment: String,
    private val userSession: UserSession,
    private val updatedAt: Instant,
) : DatabaseOperation<Boolean> {
    override fun process(state: ApplicationState): OperationResult<Boolean> {
        // Find the comment across all books
        var foundBookId: BookId? = null
        var foundComment: State.UserComment? = null

        for ((bookId, comments) in state.comments) {
            val comment = comments.find { it.id == commentId && it.userId == userSession.userId }
            if (comment != null) {
                foundBookId = bookId
                foundComment = comment
                break
            }
        }

        if (foundBookId == null || foundComment == null) {
            return ErrorResult("Comment not found or not owned by user")
        }

        val updatedComment = foundComment.copy(
            comment = comment,
            updatedAt = updatedAt,
        )

        val bookComments = state.comments[foundBookId]!!
        val updatedBookComments = bookComments.map {
            if (it.id == commentId) updatedComment else it
        }

        val updatedComments = state.comments + (foundBookId to updatedBookComments)

        return SuccessResult(
            state.copy(comments = updatedComments),
            true,
        )
    }
}
