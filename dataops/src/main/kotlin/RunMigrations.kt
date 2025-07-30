@file:JvmName("RunMigrations")

import Configuration.pgDatabase
import Configuration.pgHost
import Configuration.pgPassword
import Configuration.pgPort
import Configuration.pgUser
import org.flywaydb.core.Flyway
import javax.sql.DataSource

fun main() {
    Flyway
        .configure()
        .locations("classpath:migrations")
        .dataSource(
            "jdbc:postgresql://$pgHost:$pgPort/$pgDatabase",
            pgUser,
            pgPassword
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
