package io.heapy.kotbusta.config

import io.heapy.kotbusta.model.ApiResponse
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

        exception<Throwable> { call, cause ->
            call.response.status(HttpStatusCode.InternalServerError)
            call.respond(
                ApiResponse.Error(
                    message = "500: ${cause.message}",
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
