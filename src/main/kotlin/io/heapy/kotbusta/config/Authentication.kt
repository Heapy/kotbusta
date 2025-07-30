package io.heapy.kotbusta.config

import io.heapy.komok.tech.logging.logger
import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.model.User
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import io.ktor.util.hex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.sql.PreparedStatement
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
    val queryExecutor = applicationFactory.queryExecutor.value
    val sessionConfig = applicationFactory.sessionConfig.value

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
                queryExecutor.execute(readOnly = true) { connection ->
                    val sql = "SELECT id FROM users WHERE id = ?"
                    connection.prepareStatement(sql).use { stmt ->
                        stmt.setLong(1, session.userId)
                        val rs = stmt.executeQuery()
                        if (rs.next()) session else null
                    }
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
    val queryExecutor = applicationFactory.queryExecutor.value

    return queryExecutor.execute { conn ->
        // Try to find existing user
        val selectSql = "SELECT id, email, name, avatar_url, created_at, updated_at FROM users WHERE google_id = ?"
        conn.prepareStatement(selectSql).use { stmt ->
            stmt.setString(1, userInfo.id)
            val rs = stmt.executeQuery()

            if (rs.next()) {
                // Update existing user
                val userId = rs.getLong("id")
                val updateSql = "UPDATE users SET email = ?, name = ?, avatar_url = ?, updated_at = strftime('%s', 'now') WHERE id = ?"
                conn.prepareStatement(updateSql).use { updateStmt ->
                    updateStmt.setString(1, userInfo.email)
                    updateStmt.setString(2, userInfo.name)
                    updateStmt.setString(3, userInfo.avatarUrl)
                    updateStmt.setLong(4, userId)
                    updateStmt.executeUpdate()
                }

                User(
                    id = userId,
                    googleId = userInfo.id,
                    email = userInfo.email,
                    name = userInfo.name,
                    avatarUrl = userInfo.avatarUrl,
                    createdAt = rs.getLong("created_at"),
                    updatedAt = System.currentTimeMillis() / 1000
                )
            } else {
                // Insert new user
                val insertSql = "INSERT INTO users (google_id, email, name, avatar_url) VALUES (?, ?, ?, ?)"
                conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS).use { insertStmt ->
                    insertStmt.setString(1, userInfo.id)
                    insertStmt.setString(2, userInfo.email)
                    insertStmt.setString(3, userInfo.name)
                    insertStmt.setString(4, userInfo.avatarUrl)
                    insertStmt.executeUpdate()

                    val keys = insertStmt.generatedKeys
                    if (keys.next()) {
                        val userId = keys.getLong(1)
                        val now = System.currentTimeMillis() / 1000

                        User(
                            id = userId,
                            googleId = userInfo.id,
                            email = userInfo.email,
                            name = userInfo.name,
                            avatarUrl = userInfo.avatarUrl,
                            createdAt = now,
                            updatedAt = now
                        )
                    } else {
                        throw RuntimeException("Failed to insert/update user")
                    }
                }
            }
        }
    }
}
