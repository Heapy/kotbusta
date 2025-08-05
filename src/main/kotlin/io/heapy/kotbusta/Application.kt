package io.heapy.kotbusta

import io.heapy.kotbusta.ktor.configureAuthentication
import io.heapy.kotbusta.ktor.configureRouting
import io.heapy.kotbusta.ktor.configureSerialization
import io.heapy.kotbusta.ktor.configureStatusPages
import io.heapy.kotbusta.coroutines.DispatchersModule
import io.ktor.server.application.*
import io.ktor.server.cio.*

fun main() {
    EngineMain.main(arrayOf())
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
