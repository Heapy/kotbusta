package io.heapy.kotbusta.service

import ai.djl.inference.Predictor
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.huggingface.translator.TextEmbeddingTranslator
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import ai.djl.translate.Batchifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.exists

interface EmbeddingService {
    suspend fun embedQuery(text: String): FloatArray

    suspend fun embedPassages(texts: List<String>): List<FloatArray>
}

class DjlEmbeddingService(
    private val modelPath: Path,
) : EmbeddingService, AutoCloseable {
    private val predictors = ConcurrentLinkedQueue<Predictor<String, FloatArray>>()
    private val model: ZooModel<String, FloatArray> = loadModel()
    private val predictorThreadLocal = ThreadLocal.withInitial {
        model.newPredictor().also(predictors::add)
    }

    override suspend fun embedQuery(text: String): FloatArray =
        embedOne("query: $text")

    override suspend fun embedPassages(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) {
            return emptyList()
        }
        return withContext(Dispatchers.Default) {
            predictorThreadLocal.get().batchPredict(texts.map { "passage: $it" })
        }
    }

    override fun close() {
        predictors.forEach(Predictor<String, FloatArray>::close)
        predictors.clear()
        model.close()
    }

    private suspend fun embedOne(text: String): FloatArray =
        withContext(Dispatchers.Default) {
            predictorThreadLocal.get().predict(text)
        }

    private fun loadModel(): ZooModel<String, FloatArray> {
        val tokenizerPath = modelPath.resolve("tokenizer.json")
            .takeIf { it.exists() }
            ?: modelPath

        val tokenizer = HuggingFaceTokenizer.builder()
            .optTokenizerPath(tokenizerPath)
            .optTruncation(true)
            .optPadding(true)
            .optMaxLength(256)
            .build()

        val translator = TextEmbeddingTranslator.builder(tokenizer)
            .optBatchifier(Batchifier.STACK)
            .optPoolingMode("mean")
            .optNormalize(true)
            .optIncludeTokenTypes(false)
            .build()

        return Criteria.builder()
            .setTypes(String::class.java, FloatArray::class.java)
            .optEngine("OnnxRuntime")
            .optModelPath(modelPath)
            .optTranslator(translator)
            .build()
            .loadModel()
    }
}
