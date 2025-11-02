package io.heapy.kotbusta.ktor.routes.admin

import io.heapy.kotbusta.ApplicationModule
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Route.adminRoutes() {
    route("/admin") {
        userManagementRoutes()
    }
}
