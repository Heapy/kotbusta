package io.heapy.kotbusta

import Configuration.pgDatabase
import Configuration.pgHost
import Configuration.pgPassword
import Configuration.pgPort
import Configuration.pgUser
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.heapy.komok.tech.config.dotenv.dotenv
import io.heapy.komok.tech.di.delegate.bean
import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.config.GoogleOauthConfig
import io.heapy.kotbusta.config.SessionConfig
import io.heapy.kotbusta.coroutines.DispatchersModule
import io.heapy.kotbusta.database.JooqTransactionProvider
import io.heapy.kotbusta.parser.Fb2Parser
import io.heapy.kotbusta.parser.InpxParser
import io.heapy.kotbusta.service.AdminService
import io.heapy.kotbusta.service.BookService
import io.heapy.kotbusta.service.UserService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.postgresql.ds.PGSimpleDataSource
import runMigrations
import java.security.SecureRandom
import kotlin.concurrent.thread
import kotlin.io.path.Path

class ApplicationFactory(
    val dispatchersModule: DispatchersModule,
) {
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
        BookService()
    }

    val userService by bean {
        UserService()
    }

    val fb2Parser by bean {
        Fb2Parser()
    }

    val inpxParser by bean {
        InpxParser()
    }

    val adminService by bean {
        AdminService(
            adminEmail = adminEmail.value,
            booksDataPath = booksDataPath.value,
            fb2Parser = fb2Parser.value,
            inpxParser = inpxParser.value,
        )
    }

    val dslContext by bean {
        System.setProperty("org.jooq.no-logo", "true")
        System.setProperty("org.jooq.no-tips", "true")
        DSL.using(hikariDataSource.value, SQLDialect.POSTGRES)
    }

    val transactionProvider by bean {
        JooqTransactionProvider(
            dslContext = dslContext.value,
            ioDispatcher = dispatchersModule.ioDispatcher.value,
        )
    }

    val hikariConfig by bean {
        HikariConfig().apply {
            poolName = "kotbusta-hikari-pool"
            dataSourceClassName = PGSimpleDataSource::class.qualifiedName
            username = pgUser
            password = pgPassword
            dataSourceProperties["databaseName"] = pgDatabase
            dataSourceProperties["serverName"] = pgHost
            dataSourceProperties["portNumber"] = pgPort
        }
    }

    val hikariDataSource by bean {
        HikariDataSource(hikariConfig.value)
    }

    fun initialize() {
        runMigrations(hikariDataSource.value)

        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                if (httpClient.isInitialized) {
                    httpClient.value.use {}
                }
                if (hikariDataSource.isInitialized) {
                    hikariDataSource.value.use {}
                }
            },
        )
    }

    private companion object : Logger()
}
