package io.heapy.kotbusta.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BooleanIntMapperTest {
    @Test
    fun `should map true to 1`() {
        assertEquals(1, true mapUsing BooleanIntMapper)
    }

    @Test
    fun `should map false to 0`() {
        assertEquals(0, false mapUsing BooleanIntMapper)
    }

    @Test
    fun `should map 1 to true`() {
        assertTrue(1 mapUsing BooleanIntMapper)
    }

    @Test
    fun `should map 0 to false`() {
        assertFalse(0 mapUsing BooleanIntMapper)
    }

    @Test
    fun `should map any non-zero to true`() {
        assertTrue(42 mapUsing BooleanIntMapper)
        assertTrue(-1 mapUsing BooleanIntMapper)
        assertTrue(999 mapUsing BooleanIntMapper)
        assertTrue(Int.MAX_VALUE mapUsing BooleanIntMapper)
        assertTrue(Int.MIN_VALUE mapUsing BooleanIntMapper)
    }

    @Test
    fun `should be bidirectional`() {
        BooleanIntMapper.verifyBidirectional(
            inputs = listOf(true, false),
            outputs = listOf(0, 1),
        )
    }

    @Test
    fun `should round-trip correctly`() {
        val trueRoundTrip = true mapUsing BooleanIntMapper mapUsing BooleanIntMapper
        assertEquals(true, trueRoundTrip)

        val falseRoundTrip = false mapUsing BooleanIntMapper mapUsing BooleanIntMapper
        assertEquals(false, falseRoundTrip)
    }
}
