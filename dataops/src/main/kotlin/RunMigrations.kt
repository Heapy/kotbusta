@file:JvmName("RunMigrations")

import Configuration.dbPath
import org.flywaydb.core.Flyway
import javax.sql.DataSource

fun main() {
    Flyway
        .configure()
        .locations("classpath:migrations")
        .dataSource(
            "jdbc:sqlite:$dbPath",
            null,
            null
        )
        .loggers("slf4j")
        .load()
        .migrate()
}

fun runMigrations(
    dataSource: DataSource,
) {
    Flyway
        .configure()
        .locations("classpath:migrations")
        .dataSource(dataSource)
        .loggers("slf4j")
        .load()
        .migrate()
}
