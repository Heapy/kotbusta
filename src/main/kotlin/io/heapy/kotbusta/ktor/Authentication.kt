package io.heapy.kotbusta.ktor

import io.heapy.komok.tech.logging.logger
import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.validateUserSession
import io.heapy.kotbusta.model.State
import io.heapy.kotbusta.model.State.UserId
import io.heapy.kotbusta.model.UpsertUser
import io.heapy.kotbusta.model.requireSuccess
import io.heapy.kotbusta.run
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days

@Serializable
data class UserSession(
    val userId: UserId,
    val email: String,
    val name: String,
    val isAdmin: Boolean,
)

@Serializable
data class GoogleUserInfo(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("picture") val avatarUrl: String?,
)

data class SessionConfig(
    val secretEncryptKey: String,
    val secretSignKey: String,
)

context(applicationModule: ApplicationModule)
fun Application.configureAuthentication() {
    val googleOauthConfig = applicationModule.googleOauthConfig.value
    val httpClient = applicationModule.httpClient.value
    val sessionConfig = applicationModule.sessionConfig.value

    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 365.days.inWholeSeconds
            cookie.httpOnly = true
            cookie.secure = googleOauthConfig.redirectUri.startsWith("https")
            transform(
                SessionTransportTransformerEncrypt(
                    encryptionKey = hex(sessionConfig.secretEncryptKey),
                    signKey = hex(sessionConfig.secretEncryptKey),
                ),
            )
        }
    }

    install(Authentication) {
        // Always install session authentication
        session<UserSession>("auth-session") {
            validate { session ->
                // Validate session by checking if user exists in database
                val exists = validateUserSession(session.userId)
                if (exists) session else null
            }
        }

        logger {}
            .info(
                "Configuring Google OAuth with client ID: ${
                    googleOauthConfig.clientId.take(
                        10,
                    )
                }...",
            )
        oauth("google-oauth") {
            urlProvider = { googleOauthConfig.redirectUri }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://oauth2.googleapis.com/token",
                    requestMethod = HttpMethod.Post,
                    clientId = googleOauthConfig.clientId,
                    clientSecret = googleOauthConfig.clientSecret,
                    defaultScopes = listOf("profile", "email"),
                    extraAuthParameters = listOf("access_type" to "online"),
                )
            }
            client = httpClient
        }
    }
}

context(applicationModule: ApplicationModule)
suspend fun handleGoogleCallback(
    principal: OAuthAccessTokenResponse.OAuth2,
): UserSession {
    val httpClient = applicationModule.httpClient.value
    val adminEmail = applicationModule.adminEmail.value

    val googleUserInfo: GoogleUserInfo = httpClient
        .get("https://www.googleapis.com/oauth2/v1/userinfo") {
            headers {
                append(
                    HttpHeaders.Authorization,
                    "Bearer ${principal.accessToken}",
                )
            }
        }
        .body()

    // Insert or update user in database
    val user = insertOrUpdateUser(googleUserInfo)

    return UserSession(
        userId = user.id,
        email = user.email,
        name = user.name,
        isAdmin = user.email == adminEmail,
    )
}

context(applicationModule: ApplicationModule)
private suspend fun insertOrUpdateUser(
    googleUserInfo: GoogleUserInfo,
): State.User {
    return applicationModule
        .run(UpsertUser(googleUserInfo))
        .requireSuccess
        .result
}
