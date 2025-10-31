package io.heapy.kotbusta.database

import java.sql.Connection
import java.sql.ConnectionBuilder
import java.sql.ShardingKeyBuilder
import javax.sql.DataSource

class PragmaDataSource(
    private val delegate: DataSource,
    private val pragmas: List<String>
) : DataSource by delegate {
    override fun getConnection(): Connection {
        val c = delegate.connection
        applyPragmas(c)
        return c
    }
    override fun getConnection(username: String?, password: String?): Connection {
        val c = delegate.getConnection(username, password)
        applyPragmas(c)
        return c
    }

    override fun createConnectionBuilder(): ConnectionBuilder? {
        return delegate.createConnectionBuilder()
    }

    private fun applyPragmas(c: Connection) {
        c.createStatement().use { st ->
            pragmas.forEach { st.execute(it) }
        }
    }

    override fun createShardingKeyBuilder(): ShardingKeyBuilder? {
        return delegate.createShardingKeyBuilder()
    }
}
