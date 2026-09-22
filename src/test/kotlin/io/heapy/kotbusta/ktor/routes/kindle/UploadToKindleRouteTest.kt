package io.heapy.kotbusta.ktor.routes.kindle

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.findUploadItemById
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.KINDLE_SEND_EVENTS
import io.heapy.kotbusta.jooq.tables.references.KINDLE_SEND_QUEUE
import io.heapy.kotbusta.ktor.SessionConfig
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.EnqueueResponse
import io.heapy.kotbusta.model.KindleSendStatus
import io.heapy.kotbusta.module
import io.heapy.kotbusta.test.DatabaseExtension
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.defaultSessionSerializer
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

class UploadToKindleRouteTest {
    @Test
    fun `an epub is stored and queued`() = uploadRouteTest { applicationModule, uploadDir ->
        val response = upload(applicationModule, fileName = "book.epub", content = EPUB_BYTES)

        assertEquals(HttpStatusCode.Accepted, response.status)
        val queueId = queueId(response)
        val item = runBlocking {
            applicationModule.transactionProvider.value.transaction(READ_ONLY) {
                findUploadItemById(queueId)
            }
        }
        assertNotNull(item)
        assertEquals("book.epub", item!!.fileName)
        assertEquals("EPUB", item.sourceFormat)
        assertEquals(KindleSendStatus.PENDING.name, item.status)
        assertEquals(EPUB_BYTES.size, item.sizeBytes)
        assertTrue(uploadDir.resolve(item.storedPath!!).toFile().isFile)
    }

    @Test
    fun `an unsupported extension is refused`() = uploadRouteTest { applicationModule, uploadDir ->
        val response = upload(applicationModule, fileName = "book.txt", content = "plain".toByteArray())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(emptyList<Path>(), uploadDir.listDirectoryEntries())
    }

    @Test
    fun `content that contradicts the extension is refused and deleted`() =
        uploadRouteTest { applicationModule, uploadDir ->
            val response = upload(
                applicationModule,
                fileName = "book.epub",
                content = "%PDF-1.7 not an epub".toByteArray(),
            )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(emptyList<Path>(), uploadDir.listDirectoryEntries())
        }

    @Test
    fun `a file over the limit is refused`() = uploadRouteTest(maxUploadBytes = 8) { applicationModule, uploadDir ->
        val response = upload(
            applicationModule,
            fileName = "book.epub",
            content = EPUB_BYTES + ByteArray(64),
        )

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(emptyList<Path>(), uploadDir.listDirectoryEntries())
    }

    @Test
    fun `a device of another user is not found`() = uploadRouteTest { applicationModule, _ ->
        val response = upload(
            applicationModule,
            fileName = "book.epub",
            content = EPUB_BYTES,
            deviceId = 3,
        )

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `uploads are refused when send to kindle is not configured`() = uploadRouteTest(
        senderEmail = "",
        workerIntervalMs = "0",
    ) { applicationModule, _ ->
        val response = upload(applicationModule, fileName = "book.epub", content = EPUB_BYTES)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun `uploads are refused when the upload directory cannot be used`() {
        val blocker = Files.createTempFile("test-kotbusta-not-a-dir-", ".tmp")

        uploadRouteTest(uploadPath = blocker) { applicationModule, _ ->
            val response = upload(applicationModule, fileName = "book.epub", content = EPUB_BYTES)

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        }
    }

    // --- helpers ---

    private suspend fun ApplicationTestBuilder.upload(
        applicationModule: ApplicationModule,
        fileName: String,
        content: ByteArray,
        deviceId: Int = 1,
    ): HttpResponse =
        client.submitFormWithBinaryData(
            url = "/api/kindle/uploads?deviceId=$deviceId",
            formData = formData {
                append(
                    key = "file",
                    value = content,
                    headers = Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    },
                )
            },
        ) {
            header(
                HttpHeaders.Cookie,
                "$COOKIE_NAME=${encodeUserSessionCookie(applicationModule.sessionConfig.value)}",
            )
        }

    private fun uploadRouteTest(
        maxUploadBytes: Long = 4096,
        senderEmail: String = "kindle-sender@example.com",
        workerIntervalMs: String = "600000",
        uploadPath: Path? = null,
        block: suspend ApplicationTestBuilder.(ApplicationModule, Path) -> Unit,
    ) = testApplication {
        val uploadDir = uploadPath ?: Files.createTempDirectory("test-kotbusta-uploads-")
        val applicationModule = DatabaseExtension.createApplicationModule(
            extraEnv = mapOf(
                "KOTBUSTA_SES_SENDER_EMAIL" to senderEmail,
                "KOTBUSTA_KINDLE_WORKER_INTERVAL_MS" to workerIntervalMs,
                "KOTBUSTA_KINDLE_UPLOAD_PATH" to uploadDir.toString(),
                "KOTBUSTA_KINDLE_UPLOAD_MAX_BYTES" to maxUploadBytes.toString(),
            ),
        )
        try {
            // The fixtures ship pending catalog sends; drop them so the worker
            // this application starts has nothing to deliver during the test.
            clearCatalogQueue(applicationModule)
            application { module(applicationModule) }

            block(applicationModule, uploadDir)
        } finally {
            applicationModule.close()
            val _ = uploadDir.toFile().deleteRecursively()
        }
    }

    private fun clearCatalogQueue(applicationModule: ApplicationModule) = runBlocking {
        applicationModule.transactionProvider.value.transaction(READ_WRITE) {
            val _ = useTx { dsl ->
                val _ = dsl.deleteFrom(KINDLE_SEND_EVENTS).execute()
                dsl.deleteFrom(KINDLE_SEND_QUEUE).execute()
            }
        }
    }

    private suspend fun queueId(response: HttpResponse): Int =
        json.decodeFromString(
            EnqueueApiResponse.serializer(),
            response.bodyAsText(),
        ).data.queueId

    @Serializable
    private data class EnqueueApiResponse(
        val success: Boolean,
        val data: EnqueueResponse,
    )

    private companion object {
        private const val COOKIE_NAME = "user_session"
        private val EPUB_BYTES = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + "epub payload".toByteArray()
        private val json = Json {
            ignoreUnknownKeys = true
        }

        private fun encodeUserSessionCookie(sessionConfig: SessionConfig): String {
            val serializer = defaultSessionSerializer<UserSession>()
            val transformer = SessionTransportTransformerEncrypt(
                encryptionKey = sessionConfig.secretEncryptKey.hexToByteArray(),
                signKey = sessionConfig.secretSignKey.hexToByteArray(),
            )
            val user = UserSession(
                userId = 1,
                email = "john.doe@example.com",
                name = "John Doe",
            )
            return transformer.transformWrite(serializer.serialize(user))
        }
    }
}
