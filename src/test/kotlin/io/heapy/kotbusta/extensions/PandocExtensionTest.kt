package io.heapy.kotbusta.extensions

import io.heapy.komok.tech.logging.Logger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PandocExtensionTest {
    @Test
    fun `should always run - no pandoc required`() {
        log.info("✓ This test runs regardless of pandoc availability")
        assertTrue(true)
    }

    @Test
    @RequiresPandoc
    fun `should only run if pandoc is available`() {
        log.info("✓ This test only runs when pandoc is installed")
        // If this test runs, pandoc must be available
        // We can verify this by checking if pandoc --version works
        val process = ProcessBuilder("pandoc", "--version").start()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "Pandoc should be available")
        log.info("Pandoc is available and working")
    }

    private companion object : Logger()
}
