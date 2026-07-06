package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.ApiResponse.Error
import io.heapy.kotbusta.service.AdminService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

context(applicationModule: ApplicationModule)
fun Route.adminRoutes() {
    val adminService = applicationModule.adminService.value

    route("/admin") {
        install(AdminOnlyPlugin) {
            this.adminService = adminService
        }

        statusRoute()
        importRoute()
        getJobsRoute()
        userManagementRoutes()
    }
}

private class AdminOnlyConfig {
    lateinit var adminService: AdminService
}

private val AdminOnlyPlugin = createRouteScopedPlugin("AdminOnly", ::AdminOnlyConfig) {
    onCall { call ->
        val user = call.sessions.get<UserSession>()
        if (!pluginConfig.adminService.isAdmin(user)) {
            call.respond(HttpStatusCode.Forbidden, Error(message = "Admin access required"))
        }
    }
}
