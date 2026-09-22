package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.model.KindleSendSource.UPLOAD
import io.heapy.kotbusta.model.KindleSendStatus
import io.heapy.kotbusta.model.KindleUploadSourceFormat
import io.heapy.kotbusta.test.DatabaseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@ExtendWith(DatabaseExtension::class)
class KindleUploadQueriesTest {
    @Test
    context(_: TransactionProvider)
    fun `createUploadItem stores the send format derived from the source`() = transaction {
        val item = createUploadItem(
            userId = 1,
            deviceId = 1,
            fileName = "notes.fb2",
            storedPath = "stored-1.fb2",
            sizeBytes = 42,
            sourceFormat = KindleUploadSourceFormat.FB2,
        )

        assertNotNull(item.id)
        assertEquals("notes.fb2", item.fileName)
        assertEquals("stored-1.fb2", item.storedPath)
        assertEquals(42, item.sizeBytes)
        assertEquals("FB2", item.sourceFormat)
        assertEquals("EPUB", item.sendFormat)
        assertEquals(KindleSendStatus.PENDING.name, item.status)
        assertEquals(0, item.attempts)
    }

    @Test
    context(_: TransactionProvider)
    fun `pending items are claimed once`() = transaction {
        val item = newItem()

        val pending = findPendingUploadItems(limit = 10)
        assertTrue(pending.any { it.id == item.id })
        assertTrue(markUploadItemAsProcessing(item.id!!))
        assertFalse(markUploadItemAsProcessing(item.id!!))

        assertEquals(KindleSendStatus.PROCESSING.name, findUploadItemById(item.id!!)!!.status)
    }

    @Test
    context(_: TransactionProvider)
    fun `items that stay in processing are returned to pending`() = transaction {
        val item = newItem()
        assertTrue(markUploadItemAsProcessing(item.id!!))

        val reset = resetStuckProcessingUploadItems(olderThan = Clock.System.now() + 1.hours)

        assertEquals(1, reset)
        assertEquals(KindleSendStatus.PENDING.name, findUploadItemById(item.id!!)!!.status)
    }

    @Test
    context(_: TransactionProvider)
    fun `clearing the stored path keeps the row as history`() = transaction {
        val item = newItem()

        assertTrue(updateUploadItemStatus(item.id!!, KindleSendStatus.COMPLETED))
        assertTrue(clearUploadItemStoredPath(item.id!!))

        val updated = findUploadItemById(item.id!!)!!
        assertEquals(KindleSendStatus.COMPLETED.name, updated.status)
        assertNull(updated.storedPath)
    }

    @Test
    context(_: TransactionProvider)
    fun `attempts and next run time move forward on retry`() = transaction {
        val item = newItem()
        val nextRunAt = Clock.System.now() + 1.hours

        assertTrue(incrementUploadItemAttempts(item.id!!, nextRunAt))

        val updated = findUploadItemById(item.id!!)!!
        assertEquals(1, updated.attempts)
        assertTrue(updated.nextRunAt >= nextRunAt - 1.hours)
    }

    @Test
    context(_: TransactionProvider)
    fun `today count ignores older rows`() = transaction {
        val _ = newItem()
        val _ = createUploadItem(
            userId = 1,
            deviceId = 1,
            fileName = "old.epub",
            storedPath = "stored-old.epub",
            sizeBytes = 10,
            sourceFormat = KindleUploadSourceFormat.EPUB,
            createdAt = Clock.System.now() - 2.days,
            updatedAt = Clock.System.now() - 2.days,
        )

        val todayCount = countTodayUploadItemsByUserId(
            userId = 1,
            startOfDay = Clock.System.now() - 1.hours,
        )

        assertEquals(1, todayCount)
        assertEquals(2L, countUploadItemsByUserId(userId = 1))
    }

    @Test
    context(_: TransactionProvider)
    fun `merged history exposes the uploaded file name and its source`() = transaction {
        val _ = newItem(fileName = "travel.pdf", sourceFormat = KindleUploadSourceFormat.PDF)

        val uploads = findSendHistoryByUserId(userId = 1, limit = 10, offset = 0)
            .filter { it.source == UPLOAD }

        assertEquals(1, uploads.size)
        assertEquals("travel.pdf", uploads[0].bookTitle)
        assertEquals("John's Kindle Paperwhite", uploads[0].deviceName)
    }

    @Test
    context(_: TransactionProvider)
    fun `referenced paths list only files still attached to a row`() = transaction {
        val kept = newItem(storedPath = "kept.epub")
        val released = newItem(storedPath = "released.epub")
        assertTrue(clearUploadItemStoredPath(released.id!!))

        val referenced = findReferencedUploadPaths()

        assertEquals(setOf("kept.epub"), referenced)
        assertNotNull(kept.id)
    }

    context(_: io.heapy.kotbusta.database.TransactionContext)
    private fun newItem(
        fileName: String = "book.epub",
        storedPath: String = "stored-${Clock.System.now().toEpochMilliseconds()}.epub",
        sourceFormat: KindleUploadSourceFormat = KindleUploadSourceFormat.EPUB,
    ) = createUploadItem(
        userId = 1,
        deviceId = 1,
        fileName = fileName,
        storedPath = storedPath,
        sizeBytes = 100,
        sourceFormat = sourceFormat,
    )
}
