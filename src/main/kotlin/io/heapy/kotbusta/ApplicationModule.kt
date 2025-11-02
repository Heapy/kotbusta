package io.heapy.kotbusta

import aws.sdk.kotlin.services.ses.SesClient
import io.heapy.komok.tech.config.dotenv.dotenv
import io.heapy.komok.tech.di.delegate.MutableBean
import io.heapy.komok.tech.di.delegate.bean
import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.ktor.GoogleOauthConfig
import io.heapy.kotbusta.ktor.SessionConfig
import io.heapy.kotbusta.ktor.routes.StaticFilesConfig
import io.heapy.kotbusta.model.Database
import io.heapy.kotbusta.model.JsonDatabase
import io.heapy.kotbusta.parser.InpxParser
import io.heapy.kotbusta.service.CoverService
import io.heapy.kotbusta.service.DefaultTimeService
import io.heapy.kotbusta.service.EmailService
import io.heapy.kotbusta.service.ImportJobService
import io.heapy.kotbusta.service.PandocConversionService
import io.heapy.kotbusta.service.TimeService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import kotlin.concurrent.thread
import kotlin.io.path.Path

class ApplicationModule {
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

    val database by bean<Database> {
        JsonDatabase()
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

    val coverService by bean {
        CoverService()
    }

    val conversionService by bean {
        PandocConversionService()
    }

    val inpxParser by bean {
        InpxParser(
            booksDataPath = booksDataPath.value,
        )
    }

    val importJobService by bean {
        ImportJobService(
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

    val emailService by bean {
        EmailService(
            sesClient = sesClient.value,
            senderEmail = sesSenderEmail.value,
        )
    }

    val dbPath by bean {
        env["KOTBUSTA_DB_PATH"]
    }

    fun stopHttpClient() {
        if (httpClient.isInitialized) {
            httpClient.value.close()
        }
    }

    fun close() {
        stopHttpClient()
    }

    fun initializeShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                close()
            },
        )
    }

    fun initializeDatabase() {
        importJobService.value.importBooks()
    }

    fun initialize() {
        initializeDatabase()
        initializeShutdownHook()
    }

    private companion object : Logger()
}
