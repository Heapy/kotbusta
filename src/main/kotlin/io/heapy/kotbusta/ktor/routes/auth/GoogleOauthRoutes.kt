package io.heapy.kotbusta.ktor.routes.auth

import io.heapy.komok.tech.logging.logger
import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.handleGoogleCallback
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

private val log = logger {}

context(applicationModule: ApplicationModule)
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
                    log.error("Error handling OAuth callback", e)
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
