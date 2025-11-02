package io.heapy.kotbusta.ktor.routes.activity

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.requireApprovedUser
import io.heapy.kotbusta.model.ApiResponse.Success
import io.heapy.kotbusta.model.getRecentActivity
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.getActivityRoute() {
    get("/activity") {
        requireApprovedUser {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val activity = getRecentActivity(limit)
            call.respond(Success(data = activity))
        }
    }
}
