@file:JvmName("Application")

package io.heapy.kotbusta

import io.heapy.kotbusta.ktor.configureAuthentication
import io.heapy.kotbusta.ktor.configureRouting
import io.heapy.kotbusta.ktor.configureSerialization
import io.heapy.kotbusta.ktor.configureStatusPages
import io.heapy.kotbusta.coroutines.DispatchersModule
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.embeddedServer

fun main() {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    context(ApplicationFactory(DispatchersModule())) {
        contextOf<ApplicationFactory>().initialize()
        configureSerialization()
        configureStatusPages()
        configureAuthentication()
        configureRouting()
    }
}
