package io.heapy.kotbusta.ktor.routes.notes

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.AddOrUpdateNote
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.BookId
import io.heapy.kotbusta.model.requireSuccess
import io.heapy.kotbusta.model.toUserNoteAPI
import io.heapy.kotbusta.run
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class NoteRequest(
    val note: String,
)

context(applicationModule: ApplicationModule)
fun Route.addOrUpdateNoteRoute() {
    post("/books/{id}/notes") {
        requireApprovedUser {
            val bookId = BookId(call.requiredParameter<Int>("id"))
            val request = call.receive<NoteRequest>()

            val result = applicationModule.run(AddOrUpdateNote(bookId, request.note))
            val stateNote = result.requireSuccess.result

            // Convert to API model
            val apiNote = toUserNoteAPI(stateNote)

            call.respond(Success(data = apiNote))
        }
    }
}
