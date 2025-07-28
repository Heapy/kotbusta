package io.heapy.kotbusta.config

import io.heapy.kotbusta.database.DatabaseInitializer
import io.heapy.kotbusta.model.User
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.sql.PreparedStatement

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

fun Application.configureAuthentication() {
    val googleClientId = environment.config.propertyOrNull("kotbusta.google.clientId")?.getString()
    val googleClientSecret = environment.config.propertyOrNull("kotbusta.google.clientSecret")?.getString()
    
    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 86400 * 30 // 30 days
            cookie.httpOnly = true
            cookie.secure = false // Set to true in production with HTTPS
        }
    }
    
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }
    
    install(Authentication) {
        // Always install session authentication
        session<UserSession>("auth-session") {
            validate { session ->
                // Validate session by checking if user exists in database
                val connection = DatabaseInitializer.getConnection()
                connection.use { conn ->
                    val sql = "SELECT id FROM users WHERE id = ?"
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setLong(1, session.userId)
                        val rs = stmt.executeQuery()
                        if (rs.next()) session else null
                    }
                }
            }
        }
        
        // Only install OAuth if credentials are configured
        if (googleClientId != null && googleClientSecret != null) {
            oauth("google-oauth") {
                urlProvider = { "http://localhost:8080/callback" }
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "google",
                        authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                        accessTokenUrl = "https://oauth2.googleapis.com/token",
                        requestMethod = HttpMethod.Post,
                        clientId = googleClientId,
                        clientSecret = googleClientSecret,
                        defaultScopes = listOf("profile", "email")
                    )
                }
                client = httpClient
            }
        } else {
            println("Warning: Google OAuth credentials not configured - OAuth login will not be available")
        }
    }
}

suspend fun handleGoogleCallback(principal: OAuthAccessTokenResponse.OAuth2): UserSession {
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }
    
    try {
        val userInfo: GoogleUserInfo = httpClient.get("https://www.googleapis.com/oauth2/v1/userinfo") {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${principal.accessToken}")
            }
        }.body()
        
        // Insert or update user in database
        val user = insertOrUpdateUser(userInfo)
        
        return UserSession(
            userId = user.id,
            email = user.email,
            name = user.name
        )
    } finally {
        httpClient.close()
    }
}

private fun insertOrUpdateUser(userInfo: GoogleUserInfo): User {
    val connection = DatabaseInitializer.getConnection()
    connection.use { conn ->
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
                
                return User(
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
                        
                        return User(
                            id = userId,
                            googleId = userInfo.id,
                            email = userInfo.email,
                            name = userInfo.name,
                            avatarUrl = userInfo.avatarUrl,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                }
            }
        }
    }
    
    throw RuntimeException("Failed to insert/update user")
}