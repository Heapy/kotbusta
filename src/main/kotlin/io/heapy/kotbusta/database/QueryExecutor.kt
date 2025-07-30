package io.heapy.kotbusta.database

import io.heapy.komok.tech.logging.Logger
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
import kotlin.io.path.Path

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
        val name: String,
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
        log.info("Initializing QueryExecutor")

        Path(databasePath).parent.toFile().mkdirs()

        (1..parallelReads).forEachIndexed { index, _ ->
            coroutineScope.launch {
                coroutineScope.launch {
                    try {
                        readConnections.send(getConnection(readOnly = true))
                    } catch (e: Exception) {
                        log.error("Error creating read connection #$index", e)
                    }
                }
            }
        }

        (1..parallelWrites).forEachIndexed { index, _ ->
            coroutineScope.launch {
                try {
                    writeConnections.send(getConnection(readOnly = false))
                } catch (e: Exception) {
                    log.error("Error creating write connection #$index", e)
                }
            }
        }

        coroutineScope.launch {
            var counter = 0
            for (operation in writeChannel) {
                log.info("Executing write operation: ${operation.name} #${++counter}")
                writeConnections.borrow { connection ->
                    operation.execute(connection)
                }
                log.info("Completed write operation: ${operation.name} #$counter")
            }
        }

        coroutineScope.launch {
            var counter = 0
            for (operation in readChannel) {
                log.info("Executing read operation: ${operation.name} #${++counter}")
                readConnections.borrow { connection ->
                    operation.execute(connection)
                }
                log.info("Completed read operation: ${operation.name} #$counter")
            }
        }

        log.info("QueryExecutor initialized")
    }

    suspend fun <T> execute(
        readOnly: Boolean = false,
        name: String,
        query: Query<T>,
    ): T {
        val operation = Operation(
            name = name,
            query = query,
            deferred = CompletableDeferred(),
        )

        if (readOnly) {
            readChannel.send(operation)
        } else {
            writeChannel.send(operation)
        }

        return operation.deferred.await()
    }

    private fun getConnection(readOnly: Boolean): Connection {
        log.info("Getting connection. readOnly=$readOnly")
        return JDBC.createConnection(
            "jdbc:sqlite:$databasePath",
            Properties().apply {
                if (readOnly) {
                    put(Pragma.OPEN_MODE.pragmaName, SQLiteOpenMode.READONLY)
                }
            },
        )
    }

    private companion object : Logger()
}
