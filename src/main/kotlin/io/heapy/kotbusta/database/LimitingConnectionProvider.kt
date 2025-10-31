package io.heapy.kotbusta.database

import org.jooq.ConnectionProvider
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.Semaphore
import javax.sql.DataSource

class LimitingConnectionProvider(
    private val dataSource: DataSource,
    maxConcurrentConnections: Int,
) : ConnectionProvider {
    private val semaphore = Semaphore(maxConcurrentConnections, false)

    override fun acquire(): Connection {
        // Block until a slot is available
        semaphore.acquireUninterruptibly()
        return try {
            dataSource.connection
        } catch (e: SQLException) {
            semaphore.release()
            throw RuntimeException(
                "Error getting connection from data source $dataSource",
                e,
            )
        }
    }

    override fun release(connection: Connection) {
        try {
            connection.close()
        } catch (e: SQLException) {
            throw RuntimeException("Error closing connection $connection", e)
        } finally {
            semaphore.release()
        }
    }
}
