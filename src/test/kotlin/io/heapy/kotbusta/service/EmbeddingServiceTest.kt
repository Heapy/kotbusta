package io.heapy.kotbusta.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmbeddingServiceTest {
    @Test
    fun `embedding translator includes token types only when ONNX model requests them`() {
        assertTrue(
            AutoTokenTypeTextEmbeddingTranslator.requiresTokenTypes(
                listOf("input_ids", "attention_mask", "token_type_ids"),
            ),
        )

        assertFalse(
            AutoTokenTypeTextEmbeddingTranslator.requiresTokenTypes(
                listOf("input_ids", "attention_mask"),
            ),
        )
    }
}
