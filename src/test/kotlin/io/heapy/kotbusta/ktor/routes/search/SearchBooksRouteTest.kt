package io.heapy.kotbusta.ktor.routes.search

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.ktor.SessionConfig
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.SearchQuery
import io.heapy.kotbusta.model.SearchResult
import io.heapy.kotbusta.module
import io.heapy.kotbusta.service.BookSearchService
import io.heapy.kotbusta.service.SearchIndexNotReadyException
import io.heapy.kotbusta.test.DatabaseExtension
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.defaultSessionSerializer
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchBooksRouteTest {
    @Test
    fun `free text query matches by title`() = searchRouteTest {
        val result = search("q=Harry")

        assertEquals(2L, result.total)
        assertEquals(setOf(1, 2), result.books.map { it.id }.toSet())
        assertTrue(result.books.all { it.title.contains("Harry") })
    }

    @Test
    fun `free text query matches via fuzzy typo`() = searchRouteTest {
        val result = search("q=foudation")

        assertEquals(2L, result.total)
        assertEquals(setOf(8, 9), result.books.map { it.id }.toSet())
    }

    @Test
    fun `free text query matches via prefix`() = searchRouteTest {
        val result = search("q=Rowl")

        assertEquals(setOf(1, 2), result.books.map { it.id }.toSet())
    }

    @Test
    fun `title matches rank ahead of series-only matches`() = searchRouteTest {
        val result = search("q=foundation&limit=1")

        assertEquals(2L, result.total)
        assertTrue(result.hasMore)
        assertEquals(listOf(8), result.books.map { it.id })
    }

    @Test
    fun `author filter restricts to that author`() = searchRouteTest {
        val result = search("author=Asimov")

        assertEquals(setOf(8, 9), result.books.map { it.id }.toSet())
        assertTrue(result.books.all { "Isaac Asimov" in it.authors })
    }

    @Test
    fun `genre filter restricts to that genre`() = searchRouteTest {
        val result = search("genre=Fantasy&limit=20")

        assertEquals(5L, result.total)
        assertEquals(setOf(1, 2, 3, 4, 10), result.books.map { it.id }.toSet())
        assertTrue(result.books.all { "Fantasy" in it.genres })
    }

    @Test
    fun `language filter restricts to that language`() = searchRouteTest {
        val result = search("language=en&limit=20")

        assertEquals(10L, result.total)
        assertTrue(result.books.all { it.language == "en" })
    }

    @Test
    fun `combined filters intersect with free text`() = searchRouteTest {
        val result = search("q=foundation&genre=Science Fiction&language=en&author=Asimov")

        assertEquals(2L, result.total)
        assertEquals(setOf(8, 9), result.books.map { it.id }.toSet())
        assertFalse(result.hasMore)
    }

    @Test
    fun `empty query with no filters returns all books`() = searchRouteTest {
        val result = search("limit=20")

        assertEquals(10L, result.total)
        assertEquals(10, result.books.size)
    }

    @Test
    fun `unknown genre returns no matches`() = searchRouteTest {
        val result = search("genre=Cyberpunk")

        assertEquals(0L, result.total)
        assertTrue(result.books.isEmpty())
        assertFalse(result.hasMore)
    }

    @Test
    fun `nonexistent title returns no matches`() = searchRouteTest {
        val result = search("q=zzznotfound")

        assertEquals(0L, result.total)
        assertTrue(result.books.isEmpty())
    }

    @Test
    fun `pagination advances through result set`() = searchRouteTest {
        val firstPage = search("genre=Fantasy&limit=2&offset=0")
        val secondPage = search("genre=Fantasy&limit=2&offset=2")
        val thirdPage = search("genre=Fantasy&limit=2&offset=4")

        assertEquals(5L, firstPage.total)
        assertEquals(2, firstPage.books.size)
        assertTrue(firstPage.hasMore)

        assertEquals(2, secondPage.books.size)
        assertTrue(secondPage.hasMore)

        assertEquals(1, thirdPage.books.size)
        assertFalse(thirdPage.hasMore)

        val seen = firstPage.books.map { it.id } +
            secondPage.books.map { it.id } +
            thirdPage.books.map { it.id }
        assertEquals(5, seen.distinct().size)
    }

    @Test
    fun `offset past total returns empty page but reports total`() = searchRouteTest {
        val result = search("q=Harry&limit=5&offset=10")

        assertEquals(2L, result.total)
        assertTrue(result.books.isEmpty())
    }

    @Test
    fun `is_starred reflects the caller's stars`() = searchRouteTest {
        val userOneView = search("q=Harry", userId = 1)
        val userTwoView = search("q=Harry", userId = 2)

        val userOneStarForBookOne = userOneView.books.first { it.id == 1 }.isStarred
        val userTwoStarForBookOne = userTwoView.books.first { it.id == 1 }.isStarred
        val userOneStarForBookTwo = userOneView.books.first { it.id == 2 }.isStarred

        assertTrue(userOneStarForBookOne)
        assertTrue(userTwoStarForBookOne)
        assertFalse(userOneStarForBookTwo)
    }

    @Test
    fun `rebuild picks up newly inserted books`() = searchRouteTest {
        val before = search("q=Discworld")
        assertEquals(0L, before.total)

        insertBook(
            id = 42,
            title = "Discworld: The Colour of Magic",
            authorId = 4,
            genreId = 1,
        )
        applicationModule.bookSearchService.value.rebuildNow()

        val after = search("q=Discworld")
        assertEquals(1L, after.total)
        assertEquals(42, after.books.single().id)
    }

    @Test
    fun `503 is returned when the index is not ready`() = testApplication {
        val applicationModule = DatabaseExtension.createApplicationModule()
        applicationModule.bookSearchService.setValue(UnavailableBookSearchService())

        try {
            application { module(applicationModule) }

            val response = get("/api/search/books?q=foundation", applicationModule, userId = 1)

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertTrue(response.bodyAsText().contains("503"))
        } finally {
            applicationModule.close()
        }
    }

    @Test
    fun `legacy books search route is gone`() = searchRouteTest {
        val response = rawGet("/api/books/search?q=foundation")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private fun searchRouteTest(block: suspend SearchTestContext.() -> Unit) = testApplication {
        val applicationModule = DatabaseExtension.createApplicationModule()
        try {
            applicationModule.bookSearchService.value.rebuildNow()
            application { module(applicationModule) }
            SearchTestContext(this, client, applicationModule).block()
        } finally {
            applicationModule.close()
        }
    }

    private suspend fun ApplicationTestBuilder.get(
        url: String,
        applicationModule: ApplicationModule,
        userId: Int,
    ): HttpResponse = client.get(url) {
        headers.append(
            HttpHeaders.Cookie,
            "$COOKIE_NAME=${encodeUserSessionCookie(applicationModule.sessionConfig.value, userId)}",
        )
    }

    private class SearchTestContext(
        private val testBuilder: ApplicationTestBuilder,
        private val client: HttpClient,
        val applicationModule: ApplicationModule,
    ) {
        suspend fun search(queryString: String, userId: Int = 1): SearchResult {
            val response = rawGet("/api/search/books?$queryString", userId)
            assertEquals(
                HttpStatusCode.OK,
                response.status,
                "search failed: ${response.bodyAsText()}",
            )
            val payload = json.decodeFromString(
                SearchResponse.serializer(),
                response.bodyAsText(),
            )
            assertTrue(payload.success)
            assertNotNull(payload.data)
            return payload.data
        }

        suspend fun rawGet(url: String, userId: Int = 1): HttpResponse = client.get(url) {
            headers.append(
                HttpHeaders.Cookie,
                "$COOKIE_NAME=${encodeUserSessionCookie(applicationModule.sessionConfig.value, userId)}",
            )
        }

        suspend fun insertBook(
            id: Int,
            title: String,
            authorId: Int,
            genreId: Int,
            language: String = "en",
        ) {
            applicationModule.transactionProvider.value.transaction(READ_WRITE) {
                useTx { dslContext ->
                    dslContext.execute(
                        """
                        INSERT INTO BOOKS (
                            ID, TITLE, ANNOTATION, LANGUAGE, SERIES_ID, SERIES_NUMBER,
                            FILE_FORMAT, FILE_PATH, ARCHIVE_PATH, FILE_SIZE,
                            DATE_ADDED, COVER_IMAGE, CREATED_AT
                        ) VALUES (?, ?, NULL, ?, NULL, NULL, 'fb2', ?, ?, 1024, ?, NULL, ?)
                        """.trimIndent(),
                        id,
                        title,
                        language,
                        "/books/$id.fb2",
                        "/archive/$id.zip",
                        INSERT_TIMESTAMP,
                        INSERT_TIMESTAMP,
                    )
                    dslContext.execute(
                        "INSERT INTO BOOK_AUTHORS (BOOK_ID, AUTHOR_ID) VALUES (?, ?)",
                        id,
                        authorId,
                    )
                    dslContext.execute(
                        "INSERT INTO BOOK_GENRES (BOOK_ID, GENRE_ID) VALUES (?, ?)",
                        id,
                        genreId,
                    )
                }
            }
        }
    }

    @Serializable
    private data class SearchResponse(
        val success: Boolean,
        val data: SearchResult,
    )

    private class UnavailableBookSearchService : BookSearchService {
        override fun initialize(scope: CoroutineScope) = Unit

        override fun scheduleRebuild(scope: CoroutineScope) = Unit

        override suspend fun rebuildNow() = Unit

        override suspend fun search(query: SearchQuery, userId: Int): SearchResult {
            throw SearchIndexNotReadyException()
        }
    }

    private companion object {
        private const val COOKIE_NAME = "user_session"
        private const val INSERT_TIMESTAMP = "2024-06-01T00:00:00Z"
        private val json = Json {
            ignoreUnknownKeys = true
        }

        private fun encodeUserSessionCookie(
            sessionConfig: SessionConfig,
            userId: Int,
        ): String {
            val serializer = defaultSessionSerializer<UserSession>()
            val transformer = SessionTransportTransformerEncrypt(
                encryptionKey = hex(sessionConfig.secretEncryptKey),
                signKey = hex(sessionConfig.secretEncryptKey),
            )
            val user = userFixtures.getValue(userId)
            return transformer.transformWrite(serializer.serialize(user))
        }

        private val userFixtures = mapOf(
            1 to UserSession(userId = 1, email = "john.doe@example.com", name = "John Doe"),
            2 to UserSession(userId = 2, email = "jane.smith@example.com", name = "Jane Smith"),
        )
    }
}
