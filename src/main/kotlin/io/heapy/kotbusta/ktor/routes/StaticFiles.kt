package io.heapy.kotbusta.ktor.routes

import io.heapy.kotbusta.ApplicationFactory
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.routing.Route

data class StaticFilesConfig(
    val filesPath: String,
    val useResources: Boolean
)

context(applicationFactory: ApplicationFactory)
fun Route.staticFilesRoute() {
    val config = applicationFactory.staticFilesConfig.value
    singlePageApplication {
        filesPath = config.filesPath
        useResources = config.useResources
    }
}
