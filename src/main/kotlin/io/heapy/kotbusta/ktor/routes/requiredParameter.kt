package io.heapy.kotbusta.ktor.routes

import io.heapy.kotbusta.ktor.badRequestError
import io.ktor.server.routing.RoutingCall
import kotlin.text.toBooleanStrictOrNull
import kotlin.text.toIntOrNull
import kotlin.text.toLongOrNull

inline fun <reified T: Any> RoutingCall.requiredParameter(name: String): T {
    val parameterValue = parameters[name]
        ?: badRequestError("Required parameter '$name' is missing")

    val parsed = when (T::class) {
        Int::class -> parameterValue.toIntOrNull()
        Long::class -> parameterValue.toLongOrNull()
        Boolean::class -> parameterValue.toBooleanStrictOrNull()
        String::class -> parameterValue
        else -> error("Unsupported type ${T::class}")
    }

    parsed ?: badRequestError("Required parameter '$name: ${T::class}' having illegal value: $parameterValue")

    return parsed as T
}
