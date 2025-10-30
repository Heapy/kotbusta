package io.heapy.kotbusta.mapper

/**
 * Maps between Boolean and Int (1/0) values.
 * Useful for databases like SQLite that don't have a native boolean type.
 *
 * Mapping:
 * - true -> 1
 * - false -> 0
 * - 1 -> true
 * - 0 -> false
 * - Any non-zero value -> true
 */
val BooleanIntMapper = TypeMapper<Boolean, Int>(
    left = { boolean -> if (boolean) 1 else 0 },
    right = { int -> int != 0 },
)
