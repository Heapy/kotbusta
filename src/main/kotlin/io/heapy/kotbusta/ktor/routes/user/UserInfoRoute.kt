package io.heapy.kotbusta.ktor.routes.user

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.getUserInfo
import io.heapy.kotbusta.ktor.routes.requireUserSession
import io.heapy.kotbusta.model.ApiResponse.Success
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.userInfoRoute() {
    get("/me") {
        requireUserSession {
            val userInfo = getUserInfo()
            call.respond(Success(data = userInfo))
        }
    }
}
