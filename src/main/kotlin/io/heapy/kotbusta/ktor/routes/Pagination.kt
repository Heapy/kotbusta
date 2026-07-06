package io.heapy.kotbusta.ktor.routes

import io.ktor.server.routing.RoutingCall

const val DEFAULT_PAGE_LIMIT = 20
const val MAX_PAGE_LIMIT = 100
const val MAX_PAGE_OFFSET = 100_000

data class Pagination(
    val limit: Int,
    val offset: Int,
)

/**
 * Parses and clamps the `limit`/`offset` query parameters: limit to
 * `[1, maxLimit]`, offset to `[0, maxOffset]`. Prevents resource-exhaustion from
 * requests like `?limit=2000000000` that would otherwise try to build an
 * enormous result set / deep page.
 */
fun RoutingCall.pagination(
    defaultLimit: Int = DEFAULT_PAGE_LIMIT,
    maxLimit: Int = MAX_PAGE_LIMIT,
    maxOffset: Int = MAX_PAGE_OFFSET,
): Pagination {
    val limit = (request.queryParameters["limit"]?.toIntOrNull() ?: defaultLimit)
        .coerceIn(1, maxLimit)
    val offset = (request.queryParameters["offset"]?.toIntOrNull() ?: 0)
        .coerceIn(0, maxOffset)
    return Pagination(limit, offset)
}
