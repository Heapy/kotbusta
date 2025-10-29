package migrations.model

data class MigrationResult(
    val appliedCount: Int,
    val migrations: List<Int>,
)
