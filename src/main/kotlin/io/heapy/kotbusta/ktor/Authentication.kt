package io.heapy.kotbusta.ktor

import io.heapy.komok.tech.logging.logger
import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.model.User
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.days

@Serializable
data class UserSession(
    val userId: Long,
    val email: String,
    val name: String
)

@Serializable
data class GoogleUserInfo(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("picture") val avatarUrl: String?
)

data class SessionConfig(
    val secretEncryptKey: String,
    val secretSignKey: String,
)

context(applicationFactory: ApplicationFactory)
fun Application.configureAuthentication() {
    val googleOauthConfig = applicationFactory.googleOauthConfig.value
    val httpClient = applicationFactory.httpClient.value
    val transactionProvider = applicationFactory.transactionProvider.value
    val sessionConfig = applicationFactory.sessionConfig.value
    val validateUserSessionDao = applicationFactory.validateUserSessionQuery.value

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
                )
            )
        }
    }

    install(Authentication) {
        // Always install session authentication
        session<UserSession>("auth-session") {
            validate { session ->
                // Validate session by checking if user exists in database
                transactionProvider.transaction(READ_ONLY) {
                    val exists = validateUserSessionDao.userExists(session.userId)
                    if (exists) session else null
                }
            }
        }

        logger {}
            .info("Configuring Google OAuth with client ID: ${googleOauthConfig.clientId.take(10)}...")
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
                    extraAuthParameters = listOf("access_type" to "online")
                )
            }
            client = httpClient
        }
    }
}

context(applicationFactory: ApplicationFactory)
suspend fun handleGoogleCallback(
    principal: OAuthAccessTokenResponse.OAuth2,
): UserSession {
    val httpClient = applicationFactory.httpClient.value

    val userInfo: GoogleUserInfo = httpClient
        .get("https://www.googleapis.com/oauth2/v1/userinfo") {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${principal.accessToken}")
            }
        }
        .body()

    // Insert or update user in database
    val user = insertOrUpdateUser(userInfo)

    return UserSession(
        userId = user.id,
        email = user.email,
        name = user.name
    )
}

context(applicationFactory: ApplicationFactory)
private suspend fun insertOrUpdateUser(userInfo: GoogleUserInfo): User {
    val transactionProvider = applicationFactory.transactionProvider.value
    val findUserByGoogleIdDao = applicationFactory.findUserByGoogleIdQuery.value
    val updateUserDao = applicationFactory.updateUserQuery.value
    val insertUserDao = applicationFactory.insertUserQuery.value

    return transactionProvider.transaction(READ_WRITE) {
        // Try to find existing user
        val existingUser = findUserByGoogleIdDao.findByGoogleId(userInfo.id)

        if (existingUser != null) {
            // Update existing user
            val userId = existingUser.id!!
            val now = OffsetDateTime.now()

            updateUserDao.updateUser(
                userId = userId,
                email = userInfo.email,
                name = userInfo.name,
                avatarUrl = userInfo.avatarUrl,
                updatedAt = now
            )

            User(
                id = userId,
                googleId = userInfo.id,
                email = userInfo.email,
                name = userInfo.name,
                avatarUrl = userInfo.avatarUrl,
                createdAt = existingUser.createdAt!!.toEpochSecond(),
                updatedAt = now.toEpochSecond()
            )
        } else {
            // Insert new user
            val now = OffsetDateTime.now()

            val userId = insertUserDao.insertUser(
                googleId = userInfo.id,
                email = userInfo.email,
                name = userInfo.name,
                avatarUrl = userInfo.avatarUrl,
                createdAt = now,
                updatedAt = now
            )

            User(
                id = userId,
                googleId = userInfo.id,
                email = userInfo.email,
                name = userInfo.name,
                avatarUrl = userInfo.avatarUrl,
                createdAt = now.toEpochSecond(),
                updatedAt = now.toEpochSecond()
            )
        }
    }
}
