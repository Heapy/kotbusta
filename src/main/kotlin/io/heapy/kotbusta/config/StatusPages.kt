package io.heapy.kotbusta.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respondText(
                text = "400: ${cause.message}",
                status = HttpStatusCode.BadRequest,
            )
        }

        exception<NotFoundException> { call, cause ->
            call.respondText(
                text = "404: ${cause.message}",
                status = HttpStatusCode.NotFound,
            )
        }

        exception<Throwable> { call, cause ->
            call.respondText(
                text = "500: ${cause.message}",
                status = HttpStatusCode.InternalServerError,
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
