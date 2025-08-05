package io.heapy.kotbusta.ktor.routes.auth

import io.heapy.komok.tech.logging.logger
import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.ktor.handleGoogleCallback
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

private val log = logger {}

context(applicationFactory: ApplicationFactory)
fun Route.googleOauthRoutes() {
    authenticate("google-oauth") {
        get("/oauth/google") {
            // Redirects to Google OAuth
        }

        get("/callback") {
            val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
            if (principal != null) {
                try {
                    val userSession = handleGoogleCallback(principal)
                    call.sessions.set(userSession)
                    call.respondRedirect("/")
                } catch (e: Exception) {
                    log.error("Error handling OAuth callback", e,)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Error processing authentication",
                    )
                }
            } else {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed",
                )
            }
        }
    }
}
