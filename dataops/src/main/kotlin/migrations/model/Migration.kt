package migrations.model

data class Migration(
    val version: Int,
    val script: String,
)
