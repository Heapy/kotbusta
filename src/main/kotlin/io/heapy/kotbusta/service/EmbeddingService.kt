package io.heapy.kotbusta.service

import ai.djl.inference.Predictor
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.huggingface.translator.TextEmbeddingTranslator
import ai.djl.ndarray.NDList
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import ai.djl.translate.Batchifier
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
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

        val translator = AutoTokenTypeTextEmbeddingTranslator(tokenizer)

        return Criteria.builder()
            .setTypes(String::class.java, FloatArray::class.java)
            .optEngine("OnnxRuntime")
            .optModelPath(modelPath)
            .optTranslator(translator)
            .build()
            .loadModel()
    }
}

internal class AutoTokenTypeTextEmbeddingTranslator(
    tokenizer: HuggingFaceTokenizer,
) : Translator<String, FloatArray> {
    private val withoutTokenTypes = textEmbeddingTranslator(tokenizer, includeTokenTypes = false)
    private val withTokenTypes = textEmbeddingTranslator(tokenizer, includeTokenTypes = true)
    private var delegate: TextEmbeddingTranslator? = null

    override fun getBatchifier(): Batchifier =
        Batchifier.STACK

    override fun prepare(ctx: TranslatorContext) {
        val includeTokenTypes = requiresTokenTypes(ctx.model.describeInput().keys())
        delegate = if (includeTokenTypes) withTokenTypes else withoutTokenTypes
        selectedDelegate().prepare(ctx)
    }

    override fun processInput(ctx: TranslatorContext, input: String): NDList =
        selectedDelegate().processInput(ctx, input)

    override fun batchProcessInput(ctx: TranslatorContext, inputs: List<String>): NDList =
        selectedDelegate().batchProcessInput(ctx, inputs)

    override fun processOutput(ctx: TranslatorContext, list: NDList): FloatArray =
        selectedDelegate().processOutput(ctx, list)

    override fun batchProcessOutput(ctx: TranslatorContext, list: NDList): List<FloatArray> =
        selectedDelegate().batchProcessOutput(ctx, list)

    private fun selectedDelegate(): TextEmbeddingTranslator =
        delegate ?: error("Translator has not been prepared")

    companion object {
        fun requiresTokenTypes(inputNames: Iterable<String>): Boolean =
            "token_type_ids" in inputNames

        private fun textEmbeddingTranslator(
            tokenizer: HuggingFaceTokenizer,
            includeTokenTypes: Boolean,
        ): TextEmbeddingTranslator =
            TextEmbeddingTranslator.builder(tokenizer)
            .optBatchifier(Batchifier.STACK)
            .optPoolingMode("mean")
            .optNormalize(true)
            .optIncludeTokenTypes(includeTokenTypes)
            .build()
    }
}
