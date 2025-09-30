@file:JvmName("Application")

package io.heapy.kotbusta

import io.heapy.kotbusta.coroutines.DispatchersModule
import io.heapy.kotbusta.ktor.configureAuthentication
import io.heapy.kotbusta.ktor.configureRouting
import io.heapy.kotbusta.ktor.configureSerialization
import io.heapy.kotbusta.ktor.configureStatusPages
import io.heapy.kotbusta.repository.RepositoryModule
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

fun main() {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    context(ApplicationModule(DispatchersModule(), RepositoryModule())) {
        contextOf<ApplicationModule>().initialize()
        configureSerialization()
        configureStatusPages()
        configureAuthentication()
        configureRouting()
    }
}
