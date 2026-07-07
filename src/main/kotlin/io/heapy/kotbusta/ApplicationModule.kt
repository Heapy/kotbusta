package io.heapy.kotbusta

import aws.sdk.kotlin.services.sesv2.SesV2Client
import io.heapy.komok.tech.config.dotenv.dotenv
import io.heapy.komok.tech.di.delegate.MutableBean
import io.heapy.komok.tech.di.delegate.bean
import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.database.JooqTransactionProvider
import io.heapy.kotbusta.database.LimitingConnectionProvider
import io.heapy.kotbusta.database.PragmaDataSource
import io.heapy.kotbusta.ktor.GoogleOauthConfig
import io.heapy.kotbusta.ktor.SessionConfig
import io.heapy.kotbusta.ktor.routes.StaticFilesConfig
import io.heapy.kotbusta.parser.InpxParser
import io.heapy.kotbusta.service.AdminService
import io.heapy.kotbusta.service.AnnotationService
import io.heapy.kotbusta.service.BookSearchService
import io.heapy.kotbusta.service.CoverService
import io.heapy.kotbusta.service.DefaultTimeService
import io.heapy.kotbusta.service.DjlEmbeddingService
import io.heapy.kotbusta.service.EmbeddingService
import io.heapy.kotbusta.service.SesEmailService
import io.heapy.kotbusta.service.ImportJobService
import io.heapy.kotbusta.service.KindleService
import io.heapy.kotbusta.service.LuceneBookSearchService
import io.heapy.kotbusta.service.PandocConversionService
import io.heapy.kotbusta.service.TimeService
import io.heapy.kotbusta.service.ZipBookFileService
import io.heapy.kotbusta.worker.BookEnrichmentWorker
import io.heapy.kotbusta.worker.KindleSendWorker
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.sqlite.SQLiteDataSource
import runMigrations
import java.security.SecureRandom
import kotlin.concurrent.thread
import kotlin.io.path.Path

class ApplicationModule {
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val staticFilesConfig by bean {
        val staticFilesPath = env["KOTBUSTA_STATIC_FILES_PATH"]
        StaticFilesConfig(
            filesPath = staticFilesPath ?: "static",
            useResources = staticFilesPath == null,
        )
    }

    val dotenv by bean {
        dotenv {}
    }

    val systemEnv by bean {
        System.getenv()
    }

    val envOverrides: MutableBean<Map<String, String>> by bean {
        emptyMap()
    }

    // Lazy is ok here, since value is derived from systemEnv and dotenv
    val env by lazy {
        systemEnv.value + dotenv.value.properties + envOverrides.value
    }

    val timeService: MutableBean<TimeService> by bean {
        DefaultTimeService()
    }

    val adminEmail by bean {
        env["KOTBUSTA_ADMIN_EMAIL"]
    }

    val googleOauthConfig by bean {
        val clientId = env["KOTBUSTA_GOOGLE_CLIENT_ID"]
            ?: error("KOTBUSTA_GOOGLE_CLIENT_ID not found")
        val clientSecret = env["KOTBUSTA_GOOGLE_CLIENT_SECRET"]
            ?: error("KOTBUSTA_GOOGLE_CLIENT_SECRET not found")
        val redirectUri = env["KOTBUSTA_GOOGLE_REDIRECT_URI"]
            ?: error("KOTBUSTA_GOOGLE_REDIRECT_URI not found")

        GoogleOauthConfig(
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = redirectUri,
        )
    }

    val booksDataPath by bean {
        env["KOTBUSTA_BOOKS_DATA_PATH"]
            ?.let(::Path)
            ?: error("KOTBUSTA_BOOKS_DATA_PATH not found")
    }

    val luceneIndexPath by bean {
        env["KOTBUSTA_LUCENE_INDEX_PATH"]
            ?.let(::Path)
            ?: error("KOTBUSTA_LUCENE_INDEX_PATH not found")
    }

    val prometheusRegistry by bean {
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    }

    val metricsToken by bean {
        env["KOTBUSTA_METRICS_TOKEN"]?.takeIf(String::isNotBlank)
    }

    val embeddingModelPath by bean {
        env["KOTBUSTA_EMBEDDING_MODEL_PATH"]
            ?.takeIf(String::isNotBlank)
            ?.let(::Path)
    }

    val sessionConfig by bean {
        val random by lazy {
            SecureRandom.getInstanceStrong()
        }

        fun generateRandomKey(size: Int): String {
            return ByteArray(size)
                .also { random.nextBytes(it) }
                .toHexString()
        }

        val sessionSignKey = env["KOTBUSTA_SESSION_SIGN_KEY"]
            ?: generateRandomKey(32).also {
                log.info("Generated KOTBUSTA_SESSION_SIGN_KEY; set it explicitly to keep sessions across restarts")
            }
        val sessionEncryptKey = env["KOTBUSTA_SESSION_ENCRYPT_KEY"]
            ?: generateRandomKey(16).also {
                log.info("Generated KOTBUSTA_SESSION_ENCRYPT_KEY; set it explicitly to keep sessions across restarts")
            }

        SessionConfig(
            secretSignKey = sessionSignKey,
            secretEncryptKey = sessionEncryptKey,
        )
    }

    val httpClient by bean {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    },
                )
            }
        }
    }

    val coverService by bean {
        CoverService()
    }

    val annotationService by bean {
        AnnotationService()
    }

    val conversionService by bean {
        PandocConversionService()
    }

    val bookFileService by bean {
        ZipBookFileService(
            booksDataPath = booksDataPath.value,
            conversionService = conversionService.value,
        )
    }

    val inpxParser by bean {
        InpxParser(
            transactionProvider = transactionProvider.value,
            timeService = timeService.value,
        )
    }

    val adminService by bean {
        AdminService(
            adminEmail = adminEmail.value,
        )
    }

    val importJobService by bean {
        ImportJobService(
            booksDataPath = booksDataPath.value,
            inpxParser = inpxParser.value,
            bookSearchService = bookSearchService.value,
        )
    }

    val bookSearchService: MutableBean<BookSearchService> by bean {
        LuceneBookSearchService(
            transactionProvider = transactionProvider.value,
            indexPath = luceneIndexPath.value,
            embeddingService = embeddingService.value,
            meterRegistry = prometheusRegistry.value,
        )
    }

    val embeddingService: MutableBean<EmbeddingService?> by bean {
        embeddingModelPath.value?.let(::DjlEmbeddingService)
    }

    // Kindle Configuration
    val sesClient by bean {
        SesV2Client {}
    }

    val sesSenderEmail by bean {
        env["KOTBUSTA_SES_SENDER_EMAIL"]
            ?: error("KOTBUSTA_SES_SENDER_EMAIL not configured")
    }

    val kindleDailyQuota by bean {
        env["KOTBUSTA_KINDLE_DAILY_QUOTA"]?.toIntOrNull() ?: 20
    }

    val kindleWorkerBatchSize by bean {
        env["KOTBUSTA_KINDLE_WORKER_BATCH_SIZE"]?.toIntOrNull() ?: 10
    }

    val kindleWorkerMaxRetries by bean {
        env["KOTBUSTA_KINDLE_WORKER_MAX_RETRIES"]?.toIntOrNull() ?: 5
    }

    val kindleWorkerIntervalMs by bean {
        env["KOTBUSTA_KINDLE_WORKER_INTERVAL_MS"]?.toLongOrNull() ?: 30_000L
    }

    val enrichBatchSize by bean {
        env["KOTBUSTA_ENRICH_BATCH_SIZE"]?.toIntOrNull() ?: 32
    }

    val enrichParallelism by bean {
        env["KOTBUSTA_ENRICH_PARALLELISM"]?.toIntOrNull() ?: 4
    }

    val enrichIntervalMs by bean {
        env["KOTBUSTA_ENRICH_INTERVAL_MS"]?.toLongOrNull() ?: 60_000L
    }

    val enrichRebuildEvery by bean {
        env["KOTBUSTA_ENRICH_REBUILD_EVERY"]?.toIntOrNull() ?: 5_000
    }

    val emailService by bean {
        SesEmailService(
            sesClient = sesClient.value,
            senderEmail = sesSenderEmail.value,
        )
    }

    val kindleService by bean {
        KindleService(
            dailyQuotaLimit = kindleDailyQuota.value,
            timeService = timeService.value,
        )
    }

    val kindleSendWorker by bean {
        KindleSendWorker(
            emailService = emailService.value,
            transactionProvider = transactionProvider.value,
            bookFileService = bookFileService.value,
            batchSize = kindleWorkerBatchSize.value,
            maxRetries = kindleWorkerMaxRetries.value,
            meterRegistry = prometheusRegistry.value,
        )
    }

    val bookEnrichmentWorker by bean {
        BookEnrichmentWorker(
            transactionProvider = transactionProvider.value,
            booksDataPath = booksDataPath.value,
            annotationService = annotationService.value,
            embeddingService = embeddingService.value
                ?: error("Book enrichment worker requires KOTBUSTA_EMBEDDING_MODEL_PATH"),
            bookSearchService = bookSearchService.value,
            batchSize = enrichBatchSize.value,
            parallelism = enrichParallelism.value,
            rebuildEvery = enrichRebuildEvery.value,
            meterRegistry = prometheusRegistry.value,
        )
    }

    val roDslContext by bean {
        System.setProperty("org.jooq.no-logo", "true")
        System.setProperty("org.jooq.no-tips", "true")
        DSL.using(LimitingConnectionProvider(roDataSource.value, 2), SQLDialect.SQLITE)
    }

    val rwDslContext by bean {
        System.setProperty("org.jooq.no-logo", "true")
        System.setProperty("org.jooq.no-tips", "true")
        DSL.using(LimitingConnectionProvider(rwDataSource.value, 1), SQLDialect.SQLITE)
    }

    val transactionProvider by bean {
        JooqTransactionProvider(
            roDslContext = roDslContext.value,
            rwDslContext = rwDslContext.value,
            ioDispatcher = Dispatchers.IO,
        )
    }

    val dbPath by bean {
        env["KOTBUSTA_DB_PATH"]
            ?.let(::Path)
            ?: error("KOTBUSTA_DB_PATH not configured")
    }

    val roDataSource by bean {
        val base = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${dbPath.value}"
        }
        PragmaDataSource(
            base,
            listOf(
                "PRAGMA foreign_keys=ON",
                "PRAGMA busy_timeout=5000",
                "PRAGMA synchronous=NORMAL",
                "PRAGMA query_only=ON"
            )
        )
    }

    // Writer: normal RW URL, per-connection PRAGMAs
    val rwDataSource by bean {
        val base = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${dbPath.value}"
        }
        PragmaDataSource(
            base,
            listOf(
                "PRAGMA foreign_keys=ON",
                "PRAGMA busy_timeout=5000",
                "PRAGMA synchronous=NORMAL",
                "PRAGMA journal_mode=WAL",
            )
        )
    }

    fun initializeDatabase() {
        runMigrations(rwDataSource.value)
    }

    fun initializeKindleSendWorker() {
        // Kindle delivery is optional: only start the worker when both the interval
        // and the SES sender are configured. This keeps the core app (search,
        // download, conversion) runnable without any AWS/SES configuration.
        val senderEmail = env["KOTBUSTA_SES_SENDER_EMAIL"]
        when {
            kindleWorkerIntervalMs.value <= 0L ->
                log.info("Kindle send worker disabled because interval is ${kindleWorkerIntervalMs.value}ms")

            senderEmail.isNullOrBlank() ->
                log.info("Kindle send worker disabled because KOTBUSTA_SES_SENDER_EMAIL is not configured")

            else ->
                kindleSendWorker.value.start(
                    scope = workerScope,
                    intervalMillis = kindleWorkerIntervalMs.value,
                )
        }
    }

    fun initializeBookEnrichmentWorker() {
        when {
            enrichIntervalMs.value <= 0L ->
                log.info("Book enrichment worker disabled because interval is ${enrichIntervalMs.value}ms")

            embeddingModelPath.value == null ->
                log.info("Book enrichment worker disabled because KOTBUSTA_EMBEDDING_MODEL_PATH is not configured")

            else ->
                bookEnrichmentWorker.value.start(
                    scope = workerScope,
                    intervalMillis = enrichIntervalMs.value,
                )
        }
    }

    fun initializeSearchService() {
        bookSearchService.value.initialize(workerScope)
    }

    fun stopBackgroundWorkers() {
        if (bookEnrichmentWorker.isInitialized) {
            bookEnrichmentWorker.value.stop()
        }
        if (kindleSendWorker.isInitialized) {
            kindleSendWorker.value.stop()
        }
    }

    fun cancelBackgroundScope() {
        workerScope.cancel()
    }

    fun stopSearchService() {
        if (bookSearchService.isInitialized) {
            bookSearchService.value.close()
        }
    }

    fun stopHttpClient() {
        if (httpClient.isInitialized) {
            httpClient.value.close()
        }
    }

    fun stopEmbeddingService() {
        val service = if (embeddingService.isInitialized) embeddingService.value else null
        if (service is AutoCloseable) {
            service.close()
        }
    }

    fun close() {
        stopBackgroundWorkers()
        stopSearchService()
        stopEmbeddingService()
        stopHttpClient()
        cancelBackgroundScope()
    }

    fun initializeShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                close()
            },
        )
    }

    fun initialize() {
        initializeDatabase()
        initializeSearchService()
        initializeKindleSendWorker()
        initializeBookEnrichmentWorker()
        initializeShutdownHook()
    }

    private companion object : Logger()
}
