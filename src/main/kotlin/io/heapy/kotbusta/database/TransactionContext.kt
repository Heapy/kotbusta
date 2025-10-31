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

internal object MockTransactionContext : TransactionContext

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
        MockTransactionContext -> throw IllegalStateException("MockTransactionContext does not have a DSLContext")
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

class JooqTransactionProvider(
    private val roDslContext: DSLContext,
    private val rwDslContext: DSLContext,
    private val ioDispatcher: CoroutineDispatcher,
) : TransactionProvider {
    override suspend fun <T> transaction(
        type: TransactionType,
        block: suspend context(TransactionContext) () -> T,
    ): T = withContext(ioDispatcher) {
        val transactionContext = when (type) {
            TransactionType.READ_ONLY -> JooqTransactionContext(
                dslContext = roDslContext,
            )

            TransactionType.READ_WRITE -> JooqTransactionContext(
                dslContext = rwDslContext,
            )
        }

        block(transactionContext)
    }
}
