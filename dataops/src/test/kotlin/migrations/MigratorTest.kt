package migrations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.Instant as JavaInstant
import kotlin.time.Instant as KotlinInstant

class MigratorTest {
    @Test
    fun `migrate records Java-compatible installation timestamps`(
        @TempDir tempDir: Path,
    ) {
        val dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${tempDir.resolve("migrator.db")}"
        }

        val result = Migrator(dataSource).migrate()
        val records = dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT version, installed_at FROM schema_version ORDER BY version",
                ).use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                MigrationRecord(
                                    version = resultSet.getInt("version"),
                                    installedAt = resultSet.getString("installed_at"),
                                ),
                            )
                        }
                    }
                }
            }
        }

        assertEquals(migrations.map { it.version }, result.migrations)
        assertEquals(result.migrations, records.map { it.version })
        records.forEach { record ->
            val javaInstant = JavaInstant.parse(record.installedAt)
            val kotlinInstant = KotlinInstant.parse(record.installedAt)

            assertEquals(javaInstant.epochSecond, kotlinInstant.epochSeconds)
            assertEquals(javaInstant.nano, kotlinInstant.nanosecondsOfSecond)
            assertEquals(javaInstant.toString(), kotlinInstant.toString())
            assertEquals(record.installedAt, javaInstant.toString())
        }
    }

    private data class MigrationRecord(
        val version: Int,
        val installedAt: String,
    )
}
