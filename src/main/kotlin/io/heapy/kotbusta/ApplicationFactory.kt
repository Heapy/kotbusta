package io.heapy.kotbusta

import io.heapy.komok.tech.config.dotenv.dotenv
import io.heapy.komok.tech.di.delegate.bean
import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.config.GoogleOauthConfig
import io.heapy.kotbusta.config.SessionConfig
import io.heapy.kotbusta.database.DatabaseInitializer
import io.heapy.kotbusta.database.QueryExecutor
import io.heapy.kotbusta.parser.Fb2Parser
import io.heapy.kotbusta.parser.InpxParser
import io.heapy.kotbusta.service.AdminService
import io.heapy.kotbusta.service.BookService
import io.heapy.kotbusta.service.UserService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import kotlin.concurrent.thread
import kotlin.io.path.Path

class ApplicationFactory {
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

    val adminEmail by bean {
        env["ADMIN_EMAIL"]
    }

    val databasePath by bean {
        env["DATABASE_PATH"]
            ?: "data/database/kotbusta.db"
    }

    val googleOauthConfig by bean {
        val clientId = env["GOOGLE_CLIENT_ID"]
            ?: error("GOOGLE_CLIENT_ID not found")
        val clientSecret = env["GOOGLE_CLIENT_SECRET"]
            ?: error("GOOGLE_CLIENT_SECRET not found")
        val redirectUri = env["GOOGLE_REDIRECT_URI"]
            ?: error("GOOGLE_REDIRECT_URI not found")

        GoogleOauthConfig(
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = redirectUri,
        )
    }

    val booksDataPath by bean {
        env["BOOKS_DATA_PATH"]
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

        val sessionSignKey = env["SESSION_SIGN_KEY"]
            ?: generateRandomKey(32).also {
                log.info("Generated SESSION_SIGN_KEY=$it, add to env to persist")
            }
        val sessionEncryptKey = env["SESSION_ENCRYPT_KEY"]
            ?: generateRandomKey(16).also {
                log.info("Generated SESSION_ENCRYPT_KEY=$it, add to env to persist")
            }

        SessionConfig(
            secretSignKey = sessionSignKey,
            secretEncryptKey = sessionEncryptKey,
        )
    }

    val queryExecutor by bean {
        QueryExecutor(
            databasePath = databasePath.value,
        )
    }

    val databaseInitializer by bean {
        DatabaseInitializer(
            queryExecutor = queryExecutor.value,
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

    val bookService by bean {
        BookService(
            queryExecutor = queryExecutor.value,
        )
    }

    val userService by bean {
        UserService(
            queryExecutor = queryExecutor.value,
        )
    }

    val fb2Parser by bean {
        Fb2Parser(
            queryExecutor = queryExecutor.value,
        )
    }

    val inpxParser by bean {
        InpxParser(
            queryExecutor = queryExecutor.value,
        )
    }

    val adminService by bean {
        AdminService(
            adminEmail = adminEmail.value,
            booksDataPath = booksDataPath.value,
            fb2Parser = fb2Parser.value,
            inpxParser = inpxParser.value,
        )
    }

    fun initialize() = runBlocking(Dispatchers.IO) {
        launch {
            databaseInitializer.value.initialize()
        }
        launch {
            queryExecutor.value.initialize()
        }

        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                if (httpClient.isInitialized) {
                    httpClient.value.use {}
                }
            },
        )
    }

    private companion object : Logger()
}
