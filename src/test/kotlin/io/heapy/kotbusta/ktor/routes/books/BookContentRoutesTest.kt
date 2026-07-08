package io.heapy.kotbusta.ktor.routes.books

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.ktor.SessionConfig
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.BookContent
import io.heapy.kotbusta.model.BookSearchResult
import io.heapy.kotbusta.model.BookToc
import io.heapy.kotbusta.module
import io.heapy.kotbusta.test.DatabaseExtension
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.defaultSessionSerializer
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream

class BookContentRoutesTest {
    @Test
    fun `content route returns the requested page`() = bookRoutesTest {
        val page2 = content(page = 2)

        assertEquals(2, page2.page)
        assertEquals(3, page2.totalPages)
        assertTrue(page2.hasMore)
        assertTrue(page2.nodes.isNotEmpty())
    }

    @Test
    fun `content route clamps out-of-range pages instead of erroring`() = bookRoutesTest {
        val low = content(page = 0)
        val high = content(page = 999)

        assertEquals(1, low.page)
        assertEquals(3, high.page)
        assertFalse(high.hasMore)
    }

    @Test
    fun `toc route returns chapter entries with page numbers`() = bookRoutesTest {
        val toc = toc()

        assertEquals(3, toc.totalPages)
        assertEquals(listOf("One", "Two", "Three"), toc.entries.map { it.title })
        assertEquals(listOf(1, 2, 3), toc.entries.map { it.page })
    }

    @Test
    fun `search route reports per-page match counts, empty result for a too-short query`() = bookRoutesTest {
        val result = search("chapter")

        assertEquals(3, result.pages.size)
        assertEquals(3, result.totalMatches)
        assertTrue(result.pages.all { it.count == 1 })

        val tooShort = search("c")

        assertEquals(0, tooShort.totalMatches)
        assertTrue(tooShort.pages.isEmpty())
    }

    @Test
    fun `nonexistent book 404s on content, toc, and search routes`() = bookRoutesTest {
        assertEquals(HttpStatusCode.NotFound, rawGet("/api/books/999999/content").status)
        assertEquals(HttpStatusCode.NotFound, rawGet("/api/books/999999/toc").status)
        assertEquals(HttpStatusCode.NotFound, rawGet("/api/books/999999/search?q=chapter").status)
    }

    private fun bookRoutesTest(block: suspend BookRoutesTestContext.() -> Unit) = testApplication {
        val applicationModule = DatabaseExtension.createApplicationModule()
        try {
            application { module(applicationModule) }
            val context = BookRoutesTestContext(client, applicationModule)
            context.insertMultiChapterBook()
            context.block()
        } finally {
            applicationModule.close()
        }
    }

    private class BookRoutesTestContext(
        private val client: HttpClient,
        val applicationModule: ApplicationModule,
    ) {
        suspend fun content(page: Int, userId: Int = 1): BookContent {
            val response = rawGet("/api/books/$BOOK_ID/content?page=$page", userId)
            assertEquals(HttpStatusCode.OK, response.status, "content failed: ${response.bodyAsText()}")
            val payload = json.decodeFromString(ContentResponse.serializer(), response.bodyAsText())
            assertTrue(payload.success)
            return payload.data
        }

        suspend fun toc(userId: Int = 1): BookToc {
            val response = rawGet("/api/books/$BOOK_ID/toc", userId)
            assertEquals(HttpStatusCode.OK, response.status, "toc failed: ${response.bodyAsText()}")
            val payload = json.decodeFromString(TocResponse.serializer(), response.bodyAsText())
            assertTrue(payload.success)
            return payload.data
        }

        suspend fun search(query: String, userId: Int = 1): BookSearchResult {
            val response = rawGet("/api/books/$BOOK_ID/search?q=$query", userId)
            assertEquals(HttpStatusCode.OK, response.status, "search failed: ${response.bodyAsText()}")
            val payload = json.decodeFromString(SearchResponse.serializer(), response.bodyAsText())
            assertTrue(payload.success)
            return payload.data
        }

        suspend fun rawGet(url: String, userId: Int = 1): HttpResponse = client.get(url) {
            headers.append(
                HttpHeaders.Cookie,
                "$COOKIE_NAME=${encodeUserSessionCookie(applicationModule.sessionConfig.value, userId)}",
            )
        }

        suspend fun insertMultiChapterBook() {
            val fb2 = "<FictionBook><body>" +
                "<section><title><p>One</p></title><p>Chapter text.</p></section>" +
                "<section><title><p>Two</p></title><p>Chapter text.</p></section>" +
                "<section><title><p>Three</p></title><p>Chapter text.</p></section>" +
                "</body></FictionBook>"

            val booksDataPath = applicationModule.booksDataPath.value
            ZipOutputStream(booksDataPath.resolve("$ARCHIVE_NAME.zip").outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("$BOOK_ID.fb2"))
                zip.write(fb2.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            applicationModule.transactionProvider.value.transaction(READ_WRITE) {
                useTx { dslContext ->
                    dslContext.execute(
                        """
                        INSERT INTO BOOKS (
                            ID, TITLE, LANGUAGE, SERIES_ID, SERIES_NUMBER,
                            FILE_FORMAT, FILE_PATH, ARCHIVE_PATH, FILE_SIZE,
                            DATE_ADDED, COVER_IMAGE, CREATED_AT
                        ) VALUES (?, ?, 'en', NULL, NULL, 'fb2', ?, ?, 1024, ?, NULL, ?)
                        """.trimIndent(),
                        BOOK_ID,
                        "Multi-chapter book",
                        "$BOOK_ID.fb2",
                        ARCHIVE_NAME,
                        INSERT_TIMESTAMP,
                        INSERT_TIMESTAMP,
                    )
                }
            }
        }
    }

    @Serializable
    private data class ContentResponse(val success: Boolean, val data: BookContent)

    @Serializable
    private data class TocResponse(val success: Boolean, val data: BookToc)

    @Serializable
    private data class SearchResponse(val success: Boolean, val data: BookSearchResult)

    private companion object {
        private const val COOKIE_NAME = "user_session"
        private const val INSERT_TIMESTAMP = "2024-06-01T00:00:00Z"
        // Fixture books already occupy ids 1-10 (test-fixtures.sql); pick a fresh one.
        private const val BOOK_ID = 501
        private const val ARCHIVE_NAME = "book-$BOOK_ID"
        private val json = Json { ignoreUnknownKeys = true }

        private fun encodeUserSessionCookie(sessionConfig: SessionConfig, userId: Int): String {
            val serializer = defaultSessionSerializer<UserSession>()
            val transformer = SessionTransportTransformerEncrypt(
                encryptionKey = sessionConfig.secretEncryptKey.hexToByteArray(),
                signKey = sessionConfig.secretSignKey.hexToByteArray(),
            )
            val user = UserSession(userId = userId, email = "john.doe@example.com", name = "John Doe")
            return transformer.transformWrite(serializer.serialize(user))
        }
    }
}
