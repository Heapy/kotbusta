package io.heapy.kotbusta.ktor

import io.heapy.kotbusta.model.ApiResponse
import io.heapy.kotbusta.service.SearchIndexNotReadyException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.response.status(HttpStatusCode.BadRequest)
            call.respond(
                ApiResponse.Error(
                    message = "400: ${cause.message}",
                )
            )
        }

        exception<NotFoundException> { call, cause ->
            call.response.status(HttpStatusCode.NotFound)
            call.respond(
                ApiResponse.Error(
                    message = "404: ${cause.message}",
                )
            )
        }

        exception<SearchIndexNotReadyException> { call, cause ->
            call.response.status(HttpStatusCode.ServiceUnavailable)
            call.respond(
                ApiResponse.Error(
                    message = "503: ${cause.message}",
                )
            )
        }

        exception<Throwable> { call, cause ->
            // Log the full cause server-side; never leak exception details
            // (SQL, file paths, internal messages) to the client.
            call.application.log.error(
                "Unhandled exception while processing ${call.request.local.method.value} ${call.request.local.uri}",
                cause,
            )
            call.response.status(HttpStatusCode.InternalServerError)
            call.respond(
                ApiResponse.Error(
                    message = "Internal server error",
                )
            )
        }
    }
}

fun badRequestError(message: String): Nothing {
    throw BadRequestException(message)
}

private class BadRequestException(message: String) : RuntimeException(message)

fun notFoundError(message: String): Nothing {
    throw NotFoundException(message)
}

private class NotFoundException(message: String) : RuntimeException(message)
