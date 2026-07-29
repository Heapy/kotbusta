package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.SessionConfig
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.KindleConfigResponse
import io.heapy.kotbusta.module
import io.heapy.kotbusta.test.DatabaseExtension
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.defaultSessionSerializer
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GetKindleConfigRouteTest {
    @Test
    fun `config exposes sender email from environment`() = configRouteTest(
        senderEmail = "kindle-sender@example.com",
    ) { response ->
        assertEquals("kindle-sender@example.com", response.data.senderEmail)
    }

    @Test
    fun `config reports missing sender without breaking the application`() = configRouteTest(
        senderEmail = "",
    ) { response ->
        assertNull(response.data.senderEmail)
    }

    private fun configRouteTest(
        senderEmail: String,
        assertions: (ConfigApiResponse) -> Unit,
    ) = testApplication {
        val applicationModule = DatabaseExtension.createApplicationModule(
            extraEnv = mapOf("KOTBUSTA_SES_SENDER_EMAIL" to senderEmail),
        )
        try {
            application { module(applicationModule) }

            val response = client.get("/api/kindle/config") {
                header(
                    HttpHeaders.Cookie,
                    "$COOKIE_NAME=${encodeUserSessionCookie(applicationModule.sessionConfig.value)}",
                )
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.decodeFromString(
                ConfigApiResponse.serializer(),
                response.bodyAsText(),
            )
            assertTrue(payload.success)
            assertions(payload)
        } finally {
            applicationModule.close()
        }
    }

    @Serializable
    private data class ConfigApiResponse(
        val success: Boolean,
        val data: KindleConfigResponse,
    )

    private companion object {
        private const val COOKIE_NAME = "user_session"
        private val json = Json {
            ignoreUnknownKeys = true
        }

        private fun encodeUserSessionCookie(sessionConfig: SessionConfig): String {
            val serializer = defaultSessionSerializer<UserSession>()
            val transformer = SessionTransportTransformerEncrypt(
                encryptionKey = sessionConfig.secretEncryptKey.hexToByteArray(),
                signKey = sessionConfig.secretSignKey.hexToByteArray(),
            )
            val user = UserSession(
                userId = 1,
                email = "john.doe@example.com",
                name = "John Doe",
            )
            return transformer.transformWrite(serializer.serialize(user))
        }
    }
}
