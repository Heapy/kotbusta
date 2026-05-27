@file:JvmName("Application")

package io.heapy.kotbusta

import io.heapy.kotbusta.ktor.configureAuthentication
import io.heapy.kotbusta.ktor.configureRouting
import io.heapy.kotbusta.ktor.configureSerialization
import io.heapy.kotbusta.ktor.configureStatusPages
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import org.slf4j.bridge.SLF4JBridgeHandler

fun main() {
    // Route java.util.logging (used by some libraries, e.g. AWS SDK) through SLF4J/Logback.
    // Must run before any JUL logging happens so existing handlers are replaced.
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()

    embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(applicationModule: ApplicationModule = ApplicationModule()) {
    context(applicationModule) {
        contextOf<ApplicationModule>().initialize()
        configureSerialization()
        configureStatusPages()
        configureAuthentication()
        configureRouting()
    }
}
