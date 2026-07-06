package io.heapy.kotbusta.service

import java.nio.ByteBuffer
import java.nio.ByteOrder

object EmbeddingCodec {
    fun encode(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach(buffer::putFloat)
        return buffer.array()
    }

    fun decode(bytes: ByteArray?): FloatArray? {
        if (bytes == null) {
            return null
        }
        require(bytes.size % Float.SIZE_BYTES == 0) {
            "Invalid embedding byte length: ${bytes.size}"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) {
            buffer.float
        }
    }
}
