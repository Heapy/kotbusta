package migrations

import io.heapy.komok.tech.logging.Logger
import migrations.model.Migration
import migrations.model.MigrationResult
import java.sql.Connection
import javax.sql.DataSource

const val next = "-- migrator separator --"

class Migrator(
    private val dataSource: DataSource,
) {
    fun migrate(): MigrationResult {
        return dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                // Ensure migration table exists
                ensureMigrationTable(connection)

                // Get applied migrations
                val appliedVersions = getAppliedMigrations(connection)
                log.info("Applied migrations: $appliedVersions")

                // Load migration files
                log.info("Found ${migrations.size} migrations")

                // Filter pending migrations
                val pendingMigrations = migrations
                    .filter { it.version !in appliedVersions }
                    .sortedBy { it.version }

                if (pendingMigrations.isEmpty()) {
                    log.info("No pending migrations")
                    connection.commit()
                    MigrationResult(
                        appliedCount = 0,
                        migrations = emptyList(),
                    )
                } else {
                    migrate(
                        connection = connection,
                        pendingMigrations = pendingMigrations,
                    )
                }
            } catch (e: Exception) {
                connection.rollback()
                log.error("Migration failed, rolling back", e)
                throw RuntimeException("Migration failed: ${e.message}", e)
            }
        }
    }

    private fun migrate(
        connection: Connection,
        pendingMigrations: List<Migration>,
    ): MigrationResult {
        log.info("Applying ${pendingMigrations.size} pending migrations")

        val appliedMigrations = pendingMigrations.map { migration ->
            log.info("Applying migration v${migration.version}")
            applyMigration(connection, migration)
            recordMigration(connection, migration)
            migration.version
        }

        connection.commit()
        log.info("Successfully applied ${appliedMigrations.size} migrations")

        return MigrationResult(
            appliedCount = appliedMigrations.size,
            migrations = appliedMigrations,
        )
    }

    private fun ensureMigrationTable(connection: Connection) {
        val sql = """
            CREATE TABLE IF NOT EXISTS $MIGRATION_TABLE (
                version INTEGER PRIMARY KEY,
                installed_at TEXT NOT NULL
            )
        """.trimIndent()

        connection.createStatement().use { stmt ->
            stmt.execute(sql)
        }
    }

    private fun getAppliedMigrations(connection: Connection): Set<Int> {
        val sql = "SELECT version FROM $MIGRATION_TABLE"
        return connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(sql)
            buildSet {
                while (rs.next()) {
                    add(rs.getInt("version"))
                }
            }
        }
    }

    private fun applyMigration(connection: Connection, migration: Migration) {
        // Split by separator and execute each statement
        val statements = migration.script
            .split(next)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        connection.createStatement().use { stmt ->
            statements.forEach { sql ->
                stmt.execute(sql)
            }
        }
    }

    private fun recordMigration(connection: Connection, migration: Migration) {
        val sql = """
            INSERT INTO $MIGRATION_TABLE (version, installed_at)
            VALUES (?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, migration.version)
            stmt.setString(2, java.time.Instant.now().toString())
            stmt.executeUpdate()
        }
    }

    private companion object : Logger() {
        private const val MIGRATION_TABLE = "schema_version"
    }
}
