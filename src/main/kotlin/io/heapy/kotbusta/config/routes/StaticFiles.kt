package io.heapy.kotbusta.config.routes

import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.routing.Route

fun Route.staticFilesRoute() {
    singlePageApplication {
        filesPath = "static"
        useResources = true
    }
}
