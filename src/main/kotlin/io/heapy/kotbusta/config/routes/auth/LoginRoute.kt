package io.heapy.kotbusta.config.routes.auth

import io.heapy.komok.tech.logging.logger
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val log = logger {}

fun Route.loginRoute() {
    get("/login") {
        log.info("Login requested, redirecting to OAuth")
        call.respondRedirect("/oauth/google")
    }
}
