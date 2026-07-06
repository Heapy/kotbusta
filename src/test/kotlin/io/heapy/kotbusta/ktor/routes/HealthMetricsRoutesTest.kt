package io.heapy.kotbusta.ktor.routes

import io.heapy.kotbusta.module
import io.heapy.kotbusta.test.DatabaseExtension
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealthMetricsRoutesTest {
    @Test
    fun `health is available without authentication`() = testApplication {
        val applicationModule = DatabaseExtension.createApplicationModule()
        try {
            application { module(applicationModule) }

            val response = client.get("/health")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("ok"), body)
            assertTrue(body.contains("search"), body)
        } finally {
            applicationModule.close()
        }
    }

    @Test
    fun `metrics can be protected by bearer token`() = testApplication {
        val applicationModule = DatabaseExtension.createApplicationModule(
            extraEnv = mapOf("KOTBUSTA_METRICS_TOKEN" to "secret-token"),
        )
        try {
            application { module(applicationModule) }

            val unauthorized = client.get("/metrics")
            assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

            val authorized = client.get("/metrics") {
                header(HttpHeaders.Authorization, "Bearer secret-token")
            }

            assertEquals(HttpStatusCode.OK, authorized.status)
            assertTrue(authorized.bodyAsText().contains("jvm_"))
        } finally {
            applicationModule.close()
        }
    }
}
