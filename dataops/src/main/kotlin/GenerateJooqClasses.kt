@file:JvmName("GenerateJooqClasses")

import Configuration.dbPath
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.sqlite.SQLiteDataSource
import java.util.*
import kotlin.io.path.deleteIfExists

fun main() {
    drop()
    migrate()
    jooq()
}

fun drop() {
    // Delete the database file to start fresh
    dbPath.deleteIfExists()
}

fun migrate() {
    val dataSource = SQLiteDataSource().apply {
        url = "jdbc:sqlite:$dbPath"
    }

    runMigrations(dataSource)
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
                excludes = "schema_version"

                // Convert TEXT datetime columns to kotlin.time.Instant
                forcedTypes = listOf(
                    ForcedType().apply {
                        userType = "kotlin.time.Instant"
                        converter = "io.heapy.kotbusta.jooq.KotlinInstantConverter"
                        includeExpression = ".*\\.(CREATED_AT|UPDATED_AT|STARTED_AT|COMPLETED_AT|DATE_ADDED|NEXT_RUN_AT)"
                        includeTypes = ".*"
                    }
                )
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
