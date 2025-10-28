@file:JvmName("GenerateJooqClasses")

import Configuration.dbPath
import org.flywaydb.core.Flyway
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import java.util.*
import kotlin.io.path.deleteIfExists

fun main() {
    drop()
    flyway()
    jooq()
}

fun drop() {
    // Delete the database file to start fresh
    dbPath.deleteIfExists()
}

fun flyway() {
    Flyway
        .configure()
        .locations(
            "filesystem:./dataops/src/main/resources/migrations",
        )
        .dataSource(
            "jdbc:sqlite:$dbPath",
            null,
            null
        )
        .loggers("slf4j")
        .load()
        .migrate()
}

fun jooq() {
    GenerationTool.generate(Configuration().apply {
        jdbc = Jdbc().apply {
            driver = "org.sqlite.JDBC"
            url = "jdbc:sqlite:$dbPath"
        }

        generator = Generator().apply {
            name = "org.jooq.codegen.KotlinGenerator"
            database = Database().apply {
                name = "org.jooq.meta.sqlite.SQLiteDatabase"
                includes = ".*"
                excludes = "flyway_schema_history"
            }

            generate = Generate().apply {
                isKotlinNotNullRecordAttributes = true
            }

            target = Target().apply {
                packageName = "io.heapy.kotbusta.jooq"
                directory = "./src/main/kotlin"
                locale = Locale.ROOT.toLanguageTag()
            }
        }
    })
}
