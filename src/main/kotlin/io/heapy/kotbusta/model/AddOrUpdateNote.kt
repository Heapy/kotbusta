package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State.UserNote
import kotlin.time.Clock
import kotlin.time.Instant

context(userSession: UserSession)
fun AddOrUpdateNote(
    bookId: Int,
    note: String,
    updatedAt: Instant = Clock.System.now(),
) = AddOrUpdateNote(
    bookId = bookId,
    note = note,
    userSession = userSession,
    updatedAt = updatedAt,
)

class AddOrUpdateNote(
    private val bookId: Int,
    private val note: String,
    private val userSession: UserSession,
    private val updatedAt: Instant,
) : DatabaseOperation<UserNote> {
    override fun process(state: ApplicationState): OperationResult<UserNote> {
        // Check if user exists
        if (!state.users.containsKey(userSession.userId)) {
            return ErrorResult("User not found")
        }

        // Check if book exists
        if (!state.books.containsKey(bookId)) {
            return ErrorResult("Book not found")
        }

        val bookNotes = state.notes[bookId] ?: emptyList()
        val existingNote = bookNotes.find { it.userId == userSession.userId }

        val updatedNote = if (existingNote != null) {
            // Update existing note
            existingNote.copy(
                note = note,
                updatedAt = updatedAt,
            )
        } else {
            // Create new note
            UserNote(
                userId = userSession.userId,
                bookId = bookId,
                note = note,
                createdAt = updatedAt,
                updatedAt = updatedAt,
            )
        }

        val updatedBookNotes = if (existingNote != null) {
            bookNotes.map { if (it.userId == userSession.userId) updatedNote else it }
        } else {
            bookNotes + updatedNote
        }

        val updatedNotes = state.notes + (bookId to updatedBookNotes)

        return SuccessResult(
            state.copy(notes = updatedNotes),
            updatedNote,
        )
    }
}
