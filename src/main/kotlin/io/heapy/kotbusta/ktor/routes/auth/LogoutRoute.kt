package io.heapy.kotbusta.ktor.routes.auth

import io.heapy.komok.tech.logging.logger
import io.heapy.kotbusta.ktor.UserSession
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions

private val log = logger {}

fun Route.logoutRoute() {
    get("/logout") {
        log.info("Logout requested")
        call.sessions.clear<UserSession>()
        call.respondRedirect("/")
    }
}
