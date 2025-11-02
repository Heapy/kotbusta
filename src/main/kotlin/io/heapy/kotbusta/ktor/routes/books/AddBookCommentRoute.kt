package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.AddComment
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.requireSuccess
import io.heapy.kotbusta.model.toUserCommentAPI
import io.heapy.kotbusta.run
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CommentRequest(
    val comment: String,
)

context(applicationModule: ApplicationModule)
fun Route.addBookCommentRoute() {
    post("/books/{id}/comments") {
        requireApprovedUser {
            val bookId = call.requiredParameter<Int>("id")
            val request = call.receive<CommentRequest>()

            val result = applicationModule.run(AddComment(bookId, request.comment))
            val stateComment = result.requireSuccess.result

            // Convert to API model
            val apiComment = toUserCommentAPI(stateComment)

            call.respond(Success(data = apiComment))
        }
    }
}
