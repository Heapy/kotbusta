package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.ktor.routes.requiredParameter
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.BookId
import io.heapy.kotbusta.model.UnstarBook
import io.heapy.kotbusta.model.requireSuccess
import io.heapy.kotbusta.run
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.unstarBookRoute() {
    delete("/books/{id}/star") {
        requireApprovedUser {
            val bookId = BookId(call.requiredParameter<Int>("id"))
            val result = applicationModule.run(UnstarBook(bookId))
            call.respond(Success(data = result.requireSuccess.result))
        }
    }
}
