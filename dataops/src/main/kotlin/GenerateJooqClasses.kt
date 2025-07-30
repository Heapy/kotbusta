@file:JvmName("GenerateJooqClasses")

import org.flywaydb.core.Flyway
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.postgresql.ds.PGSimpleDataSource
import java.util.Locale
import Configuration.pgDatabase
import Configuration.pgHost
import Configuration.pgPassword
import Configuration.pgPort
import Configuration.pgUser

fun main() {
    drop()
    flyway()
    jooq()
}

fun drop() {
    PGSimpleDataSource()
        .apply {
            setURL("jdbc:postgresql://$pgHost:$pgPort/$pgDatabase")
            user = pgUser
            password = pgPassword
        }
        .connection
        .use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP SCHEMA public CASCADE;")
                statement.execute("CREATE SCHEMA public;")
            }
        }
}

fun flyway() {
    Flyway
        .configure()
        .locations(
            "filesystem:./dataops/src/main/resources/migrations",
        )
        .dataSource(
            "jdbc:postgresql://$pgHost:$pgPort/$pgDatabase",
            pgUser,
            pgPassword
        )
        .loggers("slf4j")
        .load()
        .migrate()
}

fun jooq() {
    GenerationTool.generate(Configuration().apply {
        jdbc = Jdbc().apply {
            driver = "org.postgresql.Driver"
            url = "jdbc:postgresql://$pgHost:$pgPort/$pgDatabase"
            user = pgUser
            password = pgPassword
        }

        generator = Generator().apply {
            name = "org.jooq.codegen.KotlinGenerator"
            database = Database().apply {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                includes = ".*"
                excludes = "flyway_schema_history"
                inputSchema = "public"
            }

            generate = Generate().apply {
                isDaos = true
                isPojos = true
                isKotlinNotNullPojoAttributes = true
                isKotlinNotNullInterfaceAttributes = true
                isKotlinNotNullRecordAttributes = true
                isImmutablePojos = true
                isInterfaces = true
                isImmutableInterfaces = true
            }

            target = Target().apply {
                packageName = "io.heapy.kotbusta.jooq"
                directory = "./src/main/kotlin"
                locale = Locale.ROOT.toLanguageTag()
            }
        }
    })
}
