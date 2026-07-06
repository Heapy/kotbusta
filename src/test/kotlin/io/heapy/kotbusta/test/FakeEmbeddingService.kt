package io.heapy.kotbusta.test

import io.heapy.kotbusta.service.EmbeddingService

class FakeEmbeddingService : EmbeddingService {
    val queryInputs = mutableListOf<String>()
    val passageBatches = mutableListOf<List<String>>()

    override suspend fun embedQuery(text: String): FloatArray {
        queryInputs += text
        return vectorFor(text)
    }

    override suspend fun embedPassages(texts: List<String>): List<FloatArray> {
        passageBatches += texts
        return texts.map(::vectorFor)
    }

    companion object {
        fun vectorFor(text: String): FloatArray {
            val normalized = text.lowercase()
            return when {
                "harry" in normalized || "wizard" in normalized || "magic" in normalized ->
                    floatArrayOf(1f, 0f, 0f, 0f)
                "foundation" in normalized || "galaxy" in normalized || "asimov" in normalized ->
                    floatArrayOf(0f, 1f, 0f, 0f)
                "horror" in normalized || "overlook" in normalized || "shining" in normalized ->
                    floatArrayOf(0f, 0f, 1f, 0f)
                else ->
                    floatArrayOf(0f, 0f, 0f, 1f)
            }
        }
    }
}
