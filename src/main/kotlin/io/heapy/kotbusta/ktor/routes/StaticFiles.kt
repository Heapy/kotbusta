package io.heapy.kotbusta.ktor.routes

import io.heapy.kotbusta.ApplicationModule
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.routing.Route

data class StaticFilesConfig(
    val filesPath: String,
    val useResources: Boolean
)

context(applicationModule: ApplicationModule)
fun Route.staticFilesRoute() {
    val config = applicationModule.staticFilesConfig.value
    singlePageApplication {
        filesPath = config.filesPath
        useResources = config.useResources
    }
}
