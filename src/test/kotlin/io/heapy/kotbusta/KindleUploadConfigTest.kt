package io.heapy.kotbusta

import io.heapy.kotbusta.test.DatabaseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KindleUploadConfigTest {
    @Test
    fun `a cap that could never be emailed falls back to the default`() {
        // 100 MiB: accepted on disk, always rejected by SES.
        withUploadMaxBytes("104857600") { configured ->
            assertEquals(DEFAULT_MAX_BYTES, configured)
        }
    }

    @Test
    fun `a cap of zero falls back to the default instead of failing startup`() {
        withUploadMaxBytes("0") { configured ->
            assertEquals(DEFAULT_MAX_BYTES, configured)
        }
    }

    @Test
    fun `a usable cap is taken as it is`() {
        withUploadMaxBytes("1048576") { configured ->
            assertEquals(1_048_576L, configured)
        }
    }

    private fun withUploadMaxBytes(
        value: String,
        assertions: (Long) -> Unit,
    ) {
        val applicationModule = DatabaseExtension.createApplicationModule(
            extraEnv = mapOf("KOTBUSTA_KINDLE_UPLOAD_MAX_BYTES" to value),
        )
        try {
            assertions(applicationModule.kindleUploadMaxBytes.value)
        } finally {
            applicationModule.close()
        }
    }

    private companion object {
        private const val DEFAULT_MAX_BYTES = 25L * 1024 * 1024
    }
}
