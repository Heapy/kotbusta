import io.heapy.komok.tech.config.dotenv.dotenv
import kotlin.io.path.Path

object Configuration {
    val env by lazy { System.getenv() + dotenv {}.properties }
    val dbPath by lazy { env.getValue("KOTBUSTA_DB_PATH").let(::Path) }
}
