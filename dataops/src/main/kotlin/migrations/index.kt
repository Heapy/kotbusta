package migrations

import migrations.model.Migration

val migrations: List<Migration>
    get() = listOf(
        v1,
        v2,
    )
