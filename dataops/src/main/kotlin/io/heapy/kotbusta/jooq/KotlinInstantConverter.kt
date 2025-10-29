package io.heapy.kotbusta.jooq

import org.jooq.Converter
import kotlin.time.Instant

/**
 * jOOQ converter that maps between database TEXT representation (ISO-8601 string)
 * and Kotlin's kotlin.time.Instant.
 */
class KotlinInstantConverter : Converter<String, Instant> {
    override fun from(databaseObject: String?): Instant? {
        return databaseObject?.let { Instant.parse(it) }
    }

    override fun to(userObject: Instant?): String? {
        return userObject?.toString()
    }

    override fun fromType(): Class<String> = String::class.java

    override fun toType(): Class<Instant> = Instant::class.java
}
