@file:JvmName("RunMigrations")

import Configuration.dbPath
import migrations.Migrator
import org.sqlite.SQLiteDataSource
import javax.sql.DataSource

fun main() {
    val dataSource = SQLiteDataSource().apply {
        url = "jdbc:sqlite:$dbPath"
    }

    runMigrations(dataSource)
}

fun runMigrations(
    dataSource: DataSource,
) {
    Migrator(dataSource).migrate()
}
