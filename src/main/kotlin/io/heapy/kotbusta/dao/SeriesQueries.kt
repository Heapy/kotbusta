package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.SERIES

context(_: TransactionContext)
fun insertOrGetSeries(name: String): Int = useTx { dslContext ->
    // Try to get existing series - use LIMIT 1 to avoid multiple results
    val existing = dslContext
        .select(SERIES.ID)
        .from(SERIES)
        .where(SERIES.NAME.eq(name))
        .fetchOne(SERIES.ID)

    if (existing != null) {
        return@useTx existing
    }

    dslContext
        .insertInto(SERIES)
        .set(SERIES.NAME, name)
        .returning(SERIES.ID)
        .fetchOne(SERIES.ID)
        ?: error("Failed to insert series: $name")
}
