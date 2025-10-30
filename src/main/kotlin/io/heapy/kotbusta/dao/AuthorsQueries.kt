package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.AUTHORS
import io.heapy.kotbusta.model.Author

context(_: TransactionContext)
fun insertOrGetAuthor(author: Author): Int = useTx { dslContext ->
    // Try to get existing author - use LIMIT 1 to avoid multiple results
    val existing = dslContext
        .select(AUTHORS.ID)
        .from(AUTHORS)
        .where(AUTHORS.FULL_NAME.eq(author.fullName))
        .limit(1)
        .fetchOne(AUTHORS.ID)

    if (existing != null) {
        return@useTx existing
    }

    dslContext
        .insertInto(AUTHORS)
        .set(AUTHORS.FIRST_NAME, author.firstName)
        .set(AUTHORS.LAST_NAME, author.lastName)
        .set(AUTHORS.FULL_NAME, author.fullName)
        .set(AUTHORS.CREATED_AT, kotlin.time.Clock.System.now())
        .returning(AUTHORS.ID)
        .fetchOne(AUTHORS.ID)
        ?: error("Failed to insert author: ${author.fullName}")
}
