package io.heapy.kotbusta.ktor.routes.notes

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.DeleteNote
import io.heapy.kotbusta.model.requireSuccess
import io.heapy.kotbusta.run
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.deleteNoteRoute() {
    delete("/books/{id}/notes") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val result = applicationModule.run(DeleteNote(bookId))
            call.respond(Success(data = result.requireSuccess.result))
        }
    }
}
