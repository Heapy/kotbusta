package io.heapy.kotbusta

import aws.sdk.kotlin.services.ses.SesClient
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.heapy.komok.tech.config.dotenv.dotenv
import io.heapy.komok.tech.di.delegate.MutableBean
import io.heapy.komok.tech.di.delegate.bean
import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.coroutines.DispatchersModule
import io.heapy.kotbusta.database.JooqTransactionProvider
import io.heapy.kotbusta.ktor.GoogleOauthConfig
import io.heapy.kotbusta.ktor.SessionConfig
import io.heapy.kotbusta.ktor.routes.StaticFilesConfig
import io.heapy.kotbusta.parser.Fb2Parser
import io.heapy.kotbusta.parser.InpxParser
import io.heapy.kotbusta.service.AdminService
import io.heapy.kotbusta.service.DefaultTimeService
import io.heapy.kotbusta.service.EmailService
import io.heapy.kotbusta.service.ImportJobService
import io.heapy.kotbusta.service.KindleService
import io.heapy.kotbusta.service.TimeService
import io.heapy.kotbusta.service.UserService
import io.heapy.kotbusta.worker.KindleSendWorker
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.sqlite.SQLiteDataSource
import runMigrations
import java.io.File
import java.security.SecureRandom
import kotlin.concurrent.thread
import kotlin.io.path.Path

class ApplicationModule(
    val dispatchersModule: DispatchersModule,
) {
    private val workerScope = CoroutineScope(SupervisorJob() + dispatchersModule.ioDispatcher.value)
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

    // Lazy is ok here, since value is derived from systemEnv and dotenv
    val env by lazy {
        systemEnv.value + dotenv.value.properties
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
            ?: Path("data", "books")
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
                log.info("Generated KOTBUSTA_SESSION_SIGN_KEY=$it, add to env to persist")
            }
        val sessionEncryptKey = env["KOTBUSTA_SESSION_ENCRYPT_KEY"]
            ?: generateRandomKey(16).also {
                log.info("Generated KOTBUSTA_SESSION_ENCRYPT_KEY=$it, add to env to persist")
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

    val userService by bean {
        UserService()
    }

    val fb2Parser by bean {
        Fb2Parser(
            transactionProvider = transactionProvider.value,
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
            fb2Parser = fb2Parser.value,
            inpxParser = inpxParser.value,
        )
    }

    // Kindle Configuration
    val sesClient by bean {
        SesClient {}
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

    val emailService by bean {
        EmailService(
            sesClient = sesClient.value,
            senderEmail = sesSenderEmail.value,
        )
    }

    val kindleService by bean {
        KindleService(
            dailyQuotaLimit = kindleDailyQuota.value,
        )
    }

    val kindleSendWorker by bean {
        KindleSendWorker(
            emailService = emailService.value,
            transactionProvider = transactionProvider.value,
            batchSize = kindleWorkerBatchSize.value,
            maxRetries = kindleWorkerMaxRetries.value,
            getBookFile = { bookId, format ->
                // TODO: Implement proper book file resolution
                File(booksDataPath.value.toFile(), "$bookId.$format")
            },
        )
    }

    val dslContext by bean {
        System.setProperty("org.jooq.no-logo", "true")
        System.setProperty("org.jooq.no-tips", "true")
        DSL.using(hikariDataSource.value, SQLDialect.SQLITE)
    }

    val transactionProvider by bean {
        JooqTransactionProvider(
            dslContext = dslContext.value,
            ioDispatcher = dispatchersModule.ioDispatcher.value,
        )
    }

    val dbPath by bean {
        env["KOTBUSTA_DB_PATH"] ?: "kotbusta.db"
    }

    val hikariConfig by bean {
        HikariConfig().apply {
            poolName = "kotbusta-hikari-pool"
            dataSourceClassName = SQLiteDataSource::class.qualifiedName
            dataSourceProperties["url"] = "jdbc:sqlite:$dbPath"
        }
    }

    val hikariDataSource by bean {
        HikariDataSource(hikariConfig.value)
    }

    fun initializeDatabase() {
        runMigrations(hikariDataSource.value)
    }

    fun initializeKindleSendWorker() {
        kindleSendWorker.value.start(
            scope = workerScope,
            intervalMillis = kindleWorkerIntervalMs.value,
        )
    }

    fun stopKindleSendWorker() {
        // Stop Kindle send worker
        if (kindleSendWorker.isInitialized) {
            kindleSendWorker.value.stop()
        }
        workerScope.cancel()
    }

    fun stopHttpClient() {
        if (httpClient.isInitialized) {
            httpClient.value.close()
        }
    }

    fun stopHikariPool() {
        if (hikariDataSource.isInitialized) {
            hikariDataSource.value.close()
        }
    }

    fun close() {
        stopKindleSendWorker()
        stopHttpClient()
        stopHikariPool()
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
        initializeKindleSendWorker()
        initializeShutdownHook()
    }

    private companion object : Logger()
}
