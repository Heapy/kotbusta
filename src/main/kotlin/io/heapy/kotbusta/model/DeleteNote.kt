package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession

context(userSession: UserSession)
fun DeleteNote(
    bookId: Int,
) = DeleteNote(
    bookId = bookId,
    userSession = userSession,
)

class DeleteNote(
    private val bookId: Int,
    private val userSession: UserSession,
) : DatabaseOperation<Boolean> {
    override fun process(state: ApplicationState): OperationResult<Boolean> {
        val bookNotes = state.notes[bookId] ?: return SuccessResult(state, false)

        val updatedBookNotes = bookNotes.filterNot { it.userId == userSession.userId }

        // If nothing changed, return false
        if (updatedBookNotes.size == bookNotes.size) {
            return SuccessResult(state, false)
        }

        val updatedNotes = if (updatedBookNotes.isEmpty()) {
            state.notes - bookId
        } else {
            state.notes + (bookId to updatedBookNotes)
        }

        return SuccessResult(
            state.copy(notes = updatedNotes),
            true,
        )
    }
}
