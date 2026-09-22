package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.model.KindleSendSource.CATALOG
import io.heapy.kotbusta.model.KindleSendSource.UPLOAD
import io.heapy.kotbusta.model.KindleUploadSourceFormat
import io.heapy.kotbusta.test.DatabaseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(DatabaseExtension::class)
class KindleHistoryQueriesTest {
    @Test
    context(_: TransactionProvider)
    fun `history is ordered newest first across both queues`() = transaction {
        val _ = seedUpload()

        val history = findSendHistoryByUserId(userId = 1, limit = 10, offset = 0)

        // Fixtures date user 1's three catalog sends to 2024, so the new upload leads.
        assertEquals(4, history.size)
        assertEquals(UPLOAD, history[0].source)
        assertEquals("fresh.epub", history[0].bookTitle)
        assertEquals(
            history.map { it.createdAt }.sortedDescending(),
            history.map { it.createdAt },
        )
    }

    @Test
    context(_: TransactionProvider)
    fun `pages do not repeat or skip rows from either queue`() = transaction {
        val _ = seedUpload()

        val firstPage = findSendHistoryByUserId(userId = 1, limit = 2, offset = 0)
        val secondPage = findSendHistoryByUserId(userId = 1, limit = 2, offset = 2)
        val lastPage = findSendHistoryByUserId(userId = 1, limit = 2, offset = 4)

        assertEquals(2, firstPage.size)
        assertEquals(2, secondPage.size)
        assertEquals(0, lastPage.size)

        val keys = (firstPage + secondPage).map { "${it.source}-${it.id}" }
        assertEquals(keys.size, keys.distinct().size)
        assertEquals(1, keys.count { it.startsWith(UPLOAD.name) })
        assertEquals(3, keys.count { it.startsWith(CATALOG.name) })
    }

    context(_: io.heapy.kotbusta.database.TransactionContext)
    private fun seedUpload() = createUploadItem(
        userId = 1,
        deviceId = 1,
        fileName = "fresh.epub",
        storedPath = "stored-history.epub",
        sizeBytes = 10,
        sourceFormat = KindleUploadSourceFormat.EPUB,
    )
}
