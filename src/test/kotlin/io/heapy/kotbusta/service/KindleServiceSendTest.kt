package io.heapy.kotbusta.service

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.KindleFormat
import io.heapy.kotbusta.model.KindleSendSource.CATALOG
import io.heapy.kotbusta.model.KindleSendSource.UPLOAD
import io.heapy.kotbusta.model.KindleUploadSourceFormat
import io.heapy.kotbusta.model.SendToKindleRequest
import io.heapy.kotbusta.test.DatabaseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(DatabaseExtension::class)
class KindleServiceSendTest {
    @Test
    context(_: TransactionProvider)
    fun `a catalog send refuses any format but epub`() = transaction {
        val service = service(dailyQuotaLimit = 20)

        context(session) {
            val _ = assertThrows<IllegalArgumentException> {
                service.enqueueSend(
                    bookId = 1,
                    request = SendToKindleRequest(deviceId = 1, format = KindleFormat.PDF),
                )
            }
        }
    }

    @Test
    context(_: TransactionProvider)
    fun `uploads count against the same daily quota as catalog sends`() = transaction {
        val service = service(dailyQuotaLimit = 1)

        context(session) {
            val _ = service.enqueueUpload(
                deviceId = 1,
                fileName = "book.epub",
                storedPath = "stored-quota.epub",
                sizeBytes = 10,
                sourceFormat = KindleUploadSourceFormat.EPUB,
            )

            val _ = assertThrows<QuotaExceededException> {
                service.enqueueSend(
                    bookId = 1,
                    request = SendToKindleRequest(deviceId = 1),
                )
            }
        }
    }

    @Test
    context(_: TransactionProvider)
    fun `history shows catalog sends and uploads together, newest first`() = transaction {
        val service = service(dailyQuotaLimit = 20)

        context(session) {
            val _ = service.enqueueUpload(
                deviceId = 1,
                fileName = "fresh.epub",
                storedPath = "stored-history.epub",
                sizeBytes = 10,
                sourceFormat = KindleUploadSourceFormat.EPUB,
            )

            val history = service.getSendHistory(limit = 20, offset = 0)

            // Fixtures give user 1 three catalog rows, all dated 2024.
            assertEquals(4L, history.total)
            assertEquals(UPLOAD, history.items[0].source)
            assertEquals("fresh.epub", history.items[0].bookTitle)
            assertTrue(history.items.any { it.source == CATALOG })
        }
    }

    @Test
    context(_: TransactionProvider)
    fun `history is empty past the last page`() = transaction {
        val service = service(dailyQuotaLimit = 20)

        context(session) {
            val page = service.getSendHistory(limit = 20, offset = 5_000)

            assertTrue(page.items.isEmpty())
            assertFalse(page.hasMore)
            // Fixtures give user 1 three catalog rows.
            assertEquals(3L, page.total)
        }
    }

    private fun service(dailyQuotaLimit: Int) = KindleService(
        dailyQuotaLimit = dailyQuotaLimit,
        timeService = DefaultTimeService(),
    )

    private companion object {
        private val session = UserSession(
            userId = 1,
            email = "john.doe@example.com",
            name = "John Doe",
        )
    }
}
