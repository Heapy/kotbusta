package io.heapy.kotbusta.jooq

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import kotlin.time.Instant

class KotlinInstantConverterTest {
    private val converter = KotlinInstantConverter()

    @Test
    fun `from should convert database string to Instant`() {
        fun assertFromConversion(
            input: String?,
            expected: String?,
        ): () -> Unit = {
            if (expected == null) {
                assertNull(converter.from(input))
            } else {
                val result = converter.from(input)
                assertNotNull(result)
                assertEquals(expected, result.toString())
            }
        }

        fun assertFromThrows(input: String): () -> Unit = {
            assertThrows<IllegalArgumentException> { converter.from(input) }
        }

        assertAll(
            // Valid conversions
            assertFromConversion(
                "2025-10-29T23:17:07.095Z",
                "2025-10-29T23:17:07.095Z",
            ),
            assertFromConversion(
                "2025-01-15T12:30:45.123Z",
                "2025-01-15T12:30:45.123Z",
            ),
            assertFromConversion(
                "2025-01-15T12:30:45Z",
                "2025-01-15T12:30:45Z",
            ),
            assertFromConversion(
                "1970-01-01T00:00:00Z",
                "1970-01-01T00:00:00Z",
            ),
            assertFromConversion(
                "1900-01-01T00:00:00Z",
                "1900-01-01T00:00:00Z",
            ),
            assertFromConversion(
                "2999-12-31T23:59:59Z",
                "2999-12-31T23:59:59Z",
            ),

            // Different precision levels
            assertFromConversion(
                "2025-10-29T23:17:07Z",
                "2025-10-29T23:17:07Z",
            ),
            assertFromConversion(
                "2025-10-29T23:17:07.1Z",
                "2025-10-29T23:17:07.100Z",
            ),
            assertFromConversion(
                "2025-10-29T23:17:07.12Z",
                "2025-10-29T23:17:07.120Z",
            ),
            assertFromConversion(
                "2025-10-29T23:17:07.123456Z",
                "2025-10-29T23:17:07.123456Z",
            ),
            assertFromConversion(
                "2025-10-29T23:17:07.123456789Z",
                "2025-10-29T23:17:07.123456789Z",
            ),

            // Null handling
            assertFromConversion(null, null),

            // Invalid formats
            assertFromThrows("not-a-timestamp"),
            assertFromThrows("2025-13-45"),
            assertFromThrows(""),
        )
    }

    @Test
    fun `to should convert Instant to database string`() {
        fun assertToConversion(
            input: Instant?,
            expected: String?,
        ): () -> Unit = {
            assertEquals(expected, converter.to(input))
        }

        assertAll(
            // Valid conversions
            assertToConversion(
                Instant.parse("2025-10-29T23:17:07.095Z"),
                "2025-10-29T23:17:07.095Z",
            ),
            assertToConversion(
                Instant.parse("2025-01-15T12:30:45.123Z"),
                "2025-01-15T12:30:45.123Z",
            ),
            assertToConversion(
                Instant.parse("2025-01-15T12:30:45Z"),
                "2025-01-15T12:30:45Z",
            ),
            assertToConversion(
                Instant.fromEpochSeconds(0),
                "1970-01-01T00:00:00Z",
            ),
            assertToConversion(
                Instant.parse("1900-01-01T00:00:00Z"),
                "1900-01-01T00:00:00Z",
            ),
            assertToConversion(
                Instant.parse("2999-12-31T23:59:59Z"),
                "2999-12-31T23:59:59Z",
            ),

            // Null handling
            assertToConversion(null, null),
        )
    }

    @Test
    fun `fromType should return String class`() {
        assertEquals(String::class.java, converter.fromType())
    }

    @Test
    fun `toType should return Instant class`() {
        assertEquals(Instant::class.java, converter.toType())
    }
}
