package io.heapy.kotbusta.database

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sqlite.JDBC
import org.sqlite.SQLiteConfig.Pragma
import org.sqlite.SQLiteOpenMode
import java.sql.Connection
import java.util.*

private typealias Query<T> = suspend (Connection) -> T

class QueryExecutor(
    private val databasePath: String,
) {
    private val parallelReads = 10
    private val parallelWrites = 1

    private val coroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val writeChannel = Channel<Operation<*>>()
    private val readChannel = Channel<Operation<*>>()
    private val writeConnections = Channel<Connection>(parallelWrites)
    private val readConnections = Channel<Connection>(parallelReads)

    class Operation<T>(
        val query: Query<T>,
        val deferred: CompletableDeferred<T>,
    ) {
        suspend fun execute(connection: Connection) {
            deferred.completeWith(runCatching { query(connection) })
        }
    }

    suspend fun <T, R> Channel<T>.borrow(
        body: suspend (T) -> R,
    ): R {
        val t = receive()
        return try {
            body(t)
        } finally {
            withContext(NonCancellable) {
                send(t)
            }
        }
    }

    fun initialize() {
        (1..parallelReads).forEach {
            coroutineScope.launch {
                readConnections.send(getConnection(readOnly = true))
            }
        }

        (1..parallelWrites).forEach {
            coroutineScope.launch {
                writeConnections.send(getConnection(readOnly = false))
            }
        }

        coroutineScope.launch {
            for (operation in writeChannel) {
                writeConnections.borrow { connection ->
                    operation.execute(connection)
                }
            }
        }

        coroutineScope.launch {
            for (operation in readChannel) {
                readConnections.borrow { connection ->
                    operation.execute(connection)
                }
            }
        }
    }

    suspend fun <T> execute(
        readOnly: Boolean = false,
        query: Query<T>,
    ): T {
        val operation = Operation(query, CompletableDeferred())

        if (readOnly) {
            readChannel.send(operation)
        } else {
            writeChannel.send(operation)
        }

        return operation.deferred.await()
    }

    private fun getConnection(readOnly: Boolean): Connection {
        return JDBC.createConnection(
            "jdbc:sqlite:$databasePath",
            Properties().apply {
                if (readOnly) {
                    put(Pragma.OPEN_MODE.pragmaName, SQLiteOpenMode.READONLY)
                }
            },
        )
    }
}
