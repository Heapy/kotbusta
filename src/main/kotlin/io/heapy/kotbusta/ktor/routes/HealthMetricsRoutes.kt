package io.heapy.kotbusta.ktor.routes

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.model.ApiResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

context(applicationModule: ApplicationModule)
fun Route.healthMetricsRoutes() {
    get("/health") {
        val searchState = applicationModule.bookSearchService.value.state()
        call.respond(
            ApiResponse.Success(
                data = mapOf(
                    "status" to "ok",
                    "search" to searchState.name,
                ),
            ),
        )
    }

    get("/metrics") {
        val token = applicationModule.metricsToken.value
        if (token != null) {
            val authorization = call.request.headers[HttpHeaders.Authorization]
            if (authorization != "Bearer $token") {
                call.respond(HttpStatusCode.Unauthorized, ApiResponse.Error("Unauthorized"))
                return@get
            }
        }

        call.respondText(
            text = applicationModule.prometheusRegistry.value.scrape(),
            contentType = ContentType.parse("text/plain; version=0.0.4; charset=utf-8"),
        )
    }
}
