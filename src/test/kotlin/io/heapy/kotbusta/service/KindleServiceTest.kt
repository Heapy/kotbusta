package io.heapy.kotbusta.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KindleServiceTest {
    @Test
    fun `valid kindle email is accepted`() {
        assertTrue(isValidKindleEmail("device@kindle.com"))
    }

    @Test
    fun `crlf header injection payload is rejected`() {
        assertFalse(isValidKindleEmail("me@evil.com\r\nBcc: x@kindle.com"))
    }

    @Test
    fun `non kindle email is rejected`() {
        assertFalse(isValidKindleEmail("me@gmail.com"))
    }
}
