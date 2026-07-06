package io.heapy.kotbusta.worker

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.createKindleDevice
import io.heapy.kotbusta.dao.createQueueItem
import io.heapy.kotbusta.dao.findQueueItemById
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.BOOKS
import io.heapy.kotbusta.jooq.tables.references.KINDLE_SEND_EVENTS
import io.heapy.kotbusta.jooq.tables.references.KINDLE_SEND_QUEUE
import io.heapy.kotbusta.model.Book
import io.heapy.kotbusta.model.KindleFormat
import io.heapy.kotbusta.model.KindleSendStatus
import io.heapy.kotbusta.service.BookFileService
import io.heapy.kotbusta.service.EmailResult
import io.heapy.kotbusta.service.EmailService
import io.heapy.kotbusta.service.MaterializedBook
import io.heapy.kotbusta.test.DatabaseExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@ExtendWith(DatabaseExtension::class)
class KindleSendWorkerTest {

    @Test
    fun `successful send marks item completed`(applicationModule: ApplicationModule) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val queueId = seedQueueItem(tx)
        val email = FakeEmailService(EmailResult.Success("msg-1"))

        worker(tx, email).processQueue()

        assertEquals(KindleSendStatus.COMPLETED.name, status(tx, queueId))
        assertEquals(1, email.calls)
    }

    @Test
    fun `retryable failure re-queues with incremented attempts`(applicationModule: ApplicationModule) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val queueId = seedQueueItem(tx)

        worker(tx, FakeEmailService(EmailResult.RetryableFailure("throttled")), maxRetries = 5).processQueue()

        val item = item(tx, queueId)
        assertEquals(KindleSendStatus.PENDING.name, item.status)
        assertEquals(1, item.attempts)
    }

    @Test
    fun `retryable failure past max retries fails`(applicationModule: ApplicationModule) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val queueId = seedQueueItem(tx)

        // maxRetries = 1: the first attempt (attempts -> 1) is already at the limit.
        worker(tx, FakeEmailService(EmailResult.RetryableFailure("throttled")), maxRetries = 1).processQueue()

        assertEquals(KindleSendStatus.FAILED.name, status(tx, queueId))
    }

    @Test
    fun `permanent failure marks item failed`(applicationModule: ApplicationModule) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val queueId = seedQueueItem(tx)

        worker(tx, FakeEmailService(EmailResult.PermanentFailure("bad address"))).processQueue()

        assertEquals(KindleSendStatus.FAILED.name, status(tx, queueId))
    }

    @Test
    fun `missing book fails without attempting send`(applicationModule: ApplicationModule) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val queueId = seedQueueItem(tx, bookDeleted = true)
        val email = FakeEmailService(EmailResult.Success("unused"))

        worker(tx, email).processQueue()

        assertEquals(KindleSendStatus.FAILED.name, status(tx, queueId))
        assertEquals(0, email.calls)
    }

    @Test
    fun `stuck processing items are recovered on start`(applicationModule: ApplicationModule) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val queueId = seedQueueItem(tx)

        // Simulate a crash: leave the item PROCESSING with an old UPDATED_AT.
        tx.transaction(READ_WRITE) {
            useTx { dsl ->
                dsl.update(KINDLE_SEND_QUEUE)
                    .set(KINDLE_SEND_QUEUE.STATUS, KindleSendStatus.PROCESSING.name)
                    .set(KINDLE_SEND_QUEUE.UPDATED_AT, Clock.System.now() - 1.hours)
                    .where(KINDLE_SEND_QUEUE.ID.eq(queueId))
                    .execute()
            }
        }

        worker(tx, FakeEmailService(EmailResult.Success("msg"))).recoverStuckItems()

        assertEquals(KindleSendStatus.PENDING.name, status(tx, queueId))
    }

    // --- helpers ---

    private fun worker(
        tx: TransactionProvider,
        email: EmailService,
        maxRetries: Int = 5,
    ) = KindleSendWorker(
        emailService = email,
        transactionProvider = tx,
        bookFileService = FakeBookFileService(),
        batchSize = 10,
        maxRetries = maxRetries,
    )

    private suspend fun seedQueueItem(
        tx: TransactionProvider,
        bookDeleted: Boolean = false,
    ): Int = tx.transaction(READ_WRITE) {
        useTx { dsl ->
            // Start from an empty queue so only the seeded item is processed
            // (fixtures ship their own PENDING queue rows).
            dsl.deleteFrom(KINDLE_SEND_EVENTS).execute()
            dsl.deleteFrom(KINDLE_SEND_QUEUE).execute()

            val now = Clock.System.now()
            dsl.insertInto(BOOKS)
                .set(BOOKS.ID, BOOK_ID)
                .set(BOOKS.TITLE, "Worker Book")
                .set(BOOKS.LANGUAGE, "en")
                .set(BOOKS.FILE_FORMAT, "fb2")
                .set(BOOKS.FILE_PATH, "$BOOK_ID.fb2")
                .set(BOOKS.ARCHIVE_PATH, "archive")
                .set(BOOKS.DATE_ADDED, now)
                .set(BOOKS.CREATED_AT, now)
                .execute()
        }
        val device = createKindleDevice(userId = 1, email = "worker@kindle.com", name = "Worker Kindle")
        val item = createQueueItem(
            userId = 1,
            deviceId = device.id!!,
            bookId = BOOK_ID,
            bookTitle = "Worker Book",
            format = KindleFormat.EPUB,
        )
        if (bookDeleted) {
            useTx { dsl ->
                dsl.deleteFrom(BOOKS)
                    .where(BOOKS.ID.eq(BOOK_ID))
                    .execute()
            }
        }
        item.id!!
    }

    private suspend fun item(tx: TransactionProvider, queueId: Int) =
        tx.transaction(READ_ONLY) { findQueueItemById(queueId)!! }

    private suspend fun status(tx: TransactionProvider, queueId: Int) = item(tx, queueId).status

    private class FakeEmailService(private val result: EmailResult) : EmailService {
        var calls = 0
        override suspend fun sendBookToKindle(
            recipientEmail: String,
            bookFile: File,
            bookTitle: String,
            format: String,
        ): EmailResult {
            calls++
            return result
        }
    }

    private class FakeBookFileService : BookFileService {
        override suspend fun materialize(book: Book, format: String): MaterializedBook {
            val dir = Files.createTempDirectory("fake-materialize-").toFile()
            val file = File(dir, "book.$format").apply { writeText("fake") }
            return MaterializedBook(file = file, fileName = "book.$format", format = format, tempDir = dir)
        }
    }

    private companion object {
        private const val BOOK_ID = 9100
    }
}
