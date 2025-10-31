package io.heapy.kotbusta.test

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.coroutines.DispatchersModule
import io.heapy.kotbusta.database.TransactionProvider
import org.jooq.DSLContext
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.nio.file.Files
import javax.sql.DataSource

/**
 * JUnit 5 extension that provides test database setup using ApplicationModule.
 *
 * Stores ApplicationModule in the test context and provides parameter injection
 * for TestDatabase, ApplicationModule, DSLContext, and TransactionProvider.
 *
 * Usage:
 * ```kotlin
 * @ExtendWith(DatabaseExtension::class)
 * class MyTest {
 *     @Test
 *     fun test(db: TestDatabase) {
 *         // Use db.dslContext or db.transactionProvider
 *     }
 * }
 * ```
 */
class DatabaseExtension : BeforeEachCallback, AfterEachCallback, ParameterResolver {
    override fun beforeEach(context: ExtensionContext) {
        // Use a temporary file-based database instead of in-memory
        // This ensures proper persistence across connections
        val dbUrl = Files
            .createTempFile("test-kotbusta-", ".db")
            .toAbsolutePath()
            .toString()

        val testEnv = mapOf(
            "KOTBUSTA_DB_PATH" to dbUrl,
            // Disable worker auto-start in tests
            "KOTBUSTA_KINDLE_WORKER_INTERVAL_MS" to "0",
            // Provide mock AWS credentials to prevent errors
            "AWS_ACCESS_KEY_ID" to "test",
            "AWS_SECRET_ACCESS_KEY" to "test",
            "AWS_REGION" to "us-east-1",
            // Provide required email config with test values
            "KOTBUSTA_SES_SENDER_EMAIL" to "test@example.com",
            // Provide required OAuth config with test values
            "KOTBUSTA_GOOGLE_CLIENT_ID" to "test-client-id",
            "KOTBUSTA_GOOGLE_CLIENT_SECRET" to "test-client-secret",
            "KOTBUSTA_GOOGLE_REDIRECT_URI" to "http://localhost/callback",
        )

        // Create application module with test configuration
        val applicationModule = ApplicationModule(DispatchersModule())
        applicationModule.envOverrides.setValue(testEnv)

        // Initialize database (runs migrations)
        applicationModule.initializeDatabase()

        // Load test fixtures using the same data source
        loadTestFixtures(applicationModule.rwDataSource.value)

        // Store in test context
        context.getStore(NAMESPACE).put(APP_MODULE_KEY, applicationModule)
    }

    override fun afterEach(context: ExtensionContext) {
        val applicationModule = context.getStore(NAMESPACE)
            .get(APP_MODULE_KEY, ApplicationModule::class.java)

        // Close the application module
        applicationModule?.close()
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean {
        return when (parameterContext.parameter.type) {
            ApplicationModule::class.java,
            TransactionProvider::class.java -> true
            else -> false
        }
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any {
        val applicationModule = getApplicationModule(extensionContext)

        return when (parameterContext.parameter.type) {
            ApplicationModule::class.java -> applicationModule
            TransactionProvider::class.java -> applicationModule.transactionProvider.value
            else -> error("Unsupported parameter type: ${parameterContext.parameter.type}")
        }
    }

    private fun loadTestFixtures(dataSource: DataSource) {
        // Load the test fixtures SQL file from test resources
        val fixturesStream = this::class.java.classLoader
            .getResourceAsStream("sql/test-fixtures.sql")
            ?: throw IllegalStateException("test-fixtures.sql file not found in test resources")

        val sql = fixturesStream.bufferedReader().use { it.readText() }

        // Use JDBC connection directly to execute the SQL script
        // This allows SQLite to handle multiple statements more reliably
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate(sql)
            }
        }
    }

    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(DatabaseExtension::class.java)
        private const val APP_MODULE_KEY = "applicationModule"

        fun getApplicationModule(context: ExtensionContext): ApplicationModule {
            return context.getStore(NAMESPACE)
                .get(APP_MODULE_KEY, ApplicationModule::class.java)
                ?: error("DatabaseExtension not registered")
        }
    }
}
