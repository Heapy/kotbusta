import io.heapy.komok.tech.config.dotenv.dotenv

object Configuration {
    val env by lazy { System.getenv() + dotenv {}.properties }
    val pgHost by lazy { env["KOTBUSTA_POSTGRES_HOST"] }
    val pgPort by lazy { env["KOTBUSTA_POSTGRES_PORT"] }
    val pgUser by lazy { env["KOTBUSTA_POSTGRES_USER"] }
    val pgPassword by lazy { env["KOTBUSTA_POSTGRES_PASSWORD"] }
    val pgDatabase by lazy { env["KOTBUSTA_POSTGRES_DATABASE"] }
}
