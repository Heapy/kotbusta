package io.heapy.kotbusta.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jooq.DSLContext

@TransactionDsl
sealed interface TransactionContext

private class JooqTransactionContext(
    val dslContext: DSLContext,
) : TransactionContext

enum class TransactionType {
    READ_ONLY,
    READ_WRITE,
}

@DslMarker
annotation class TransactionDsl

@TransactionDsl
context(_: TransactionContext)
fun <T> useTx(
    body: (DSLContext) -> T,
): T {
    return body(unwrap())
}

context(transactionContext: TransactionContext)
private fun unwrap(): DSLContext {
    return when(transactionContext) {
        is JooqTransactionContext -> transactionContext.dslContext
    }
}

interface TransactionProvider {
    @TransactionDsl
    suspend fun <T> transaction(
        type: TransactionType,
        block: suspend context(TransactionContext) () -> T,
    ): T
}

context(transactionProvider: TransactionProvider)
fun transaction(
    type: TransactionType = TransactionType.READ_WRITE,
    block: context(TransactionContext) () -> Unit,
) {
    runBlocking {
        transactionProvider.transaction(type, block)
    }
}

/**
 * Runs the block inside a real jOOQ transaction (`BEGIN` / `COMMIT` / `ROLLBACK`):
 * the whole block commits as a unit and rolls back if it throws. The
 * transaction-scoped [DSLContext] (`config.dsl()`, bound to a single JDBC
 * connection) is what `useTx` hands to DAO code, so every statement in the
 * block runs on the same connection.
 *
 * Writers are serialized by the read-write connection pool (a single permit),
 * so concurrent `READ_WRITE` transactions queue rather than interleave.
 *
 * IMPORTANT: a transaction block must not perform suspending I/O that hops
 * dispatchers (HTTP calls, SES, Pandoc, etc.). The connection — and, for
 * `READ_WRITE`, the single writer slot — is held for the entire block, so such
 * I/O would pin it for the duration. Do that work outside the transaction and
 * persist the outcome in a short follow-up transaction (see `KindleSendWorker`).
 */
class JooqTransactionProvider(
    private val roDslContext: DSLContext,
    private val rwDslContext: DSLContext,
    private val ioDispatcher: CoroutineDispatcher,
) : TransactionProvider {
    override suspend fun <T> transaction(
        type: TransactionType,
        block: suspend context(TransactionContext) () -> T,
    ): T = withContext(ioDispatcher) {
        val dslContext = when (type) {
            TransactionType.READ_ONLY -> roDslContext
            TransactionType.READ_WRITE -> rwDslContext
        }

        dslContext.transactionResult { config ->
            // jOOQ's transactional callback is blocking; bridge back into the
            // suspend block. Safe because transaction blocks do not suspend onto
            // another dispatcher (see KDoc), so this stays on the current thread.
            runBlocking {
                block(JooqTransactionContext(config.dsl()))
            }
        }
    }
}
