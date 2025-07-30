package io.heapy.kotbusta.config.routes.admin

import io.heapy.kotbusta.ApplicationFactory
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
fun Route.adminRoutes() {
    route("/admin") {
        statusRoute()
        importRoute()
        extractCoversRoute()
        getJobsRoute()
        getJobRoute()
    }
}
