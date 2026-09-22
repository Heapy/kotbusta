package io.heapy.kotbusta.worker

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.dao.createUploadItem
import io.heapy.kotbusta.dao.findUploadItemById
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.model.KindleSendStatus
import io.heapy.kotbusta.model.KindleUploadSourceFormat
import io.heapy.kotbusta.service.ConversionResult
import io.heapy.kotbusta.service.ConversionService
import io.heapy.kotbusta.service.EmailResult
import io.heapy.kotbusta.service.EmailService
import io.heapy.kotbusta.service.KindleUploadStorage
import io.heapy.kotbusta.test.DatabaseExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@ExtendWith(DatabaseExtension::class)
class UploadKindleQueueWorkerTest {
    @Test
    fun `successful send completes the item and releases the file`(
        applicationModule: ApplicationModule,
        @TempDir uploadDir: Path,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val storage = storage(uploadDir)
        val (queueId, storedName) = seedUpload(tx, storage, "book.epub", KindleUploadSourceFormat.EPUB)
        val email = FakeEmailService(EmailResult.Success("msg-1"))

        worker(tx, email, storage).processQueue()

        val item = tx.transaction(READ_ONLY) { findUploadItemById(queueId)!! }
        assertEquals(KindleSendStatus.COMPLETED.name, item.status)
        assertNull(item.storedPath)
        assertFalse(storage.resolve(storedName).exists())
        assertEquals("book.epub", email.lastAttachmentFileName)
    }

    @Test
    fun `fb2 upload is converted to epub before sending`(
        applicationModule: ApplicationModule,
        @TempDir uploadDir: Path,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val storage = storage(uploadDir)
        val _ = seedUpload(tx, storage, "notes.fb2", KindleUploadSourceFormat.FB2)
        val email = FakeEmailService(EmailResult.Success("msg-2"))
        val conversion = FakeConversionService()

        worker(tx, email, storage, conversion = conversion).processQueue()

        assertEquals("notes.epub", email.lastAttachmentFileName)
        // Pandoc works in its input file's directory, so converting must never be
        // pointed at the upload directory.
        assertNotEquals(
            uploadDir.toFile().canonicalFile,
            conversion.lastInputParent?.canonicalFile,
        )
    }

    @Test
    fun `a file that cannot be read is retried instead of failed`(
        applicationModule: ApplicationModule,
        @TempDir uploadDir: Path,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val storage = storage(uploadDir)
        val (queueId, storedName) = seedUpload(tx, storage, "book.epub", KindleUploadSourceFormat.EPUB)
        storage.delete(storedName)
        val email = FakeEmailService(EmailResult.Success("unused"))

        worker(tx, email, storage).processQueue()

        val item = tx.transaction(READ_ONLY) { findUploadItemById(queueId)!! }
        assertEquals(KindleSendStatus.PENDING.name, item.status)
        assertEquals(1, item.attempts)
        assertEquals(storedName, item.storedPath)
        assertEquals(0, email.calls)
    }

    @Test
    fun `a file that stays unreadable fails once the retries run out`(
        applicationModule: ApplicationModule,
        @TempDir uploadDir: Path,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val storage = storage(uploadDir)
        val (queueId, storedName) = seedUpload(tx, storage, "book.epub", KindleUploadSourceFormat.EPUB)
        storage.delete(storedName)

        worker(tx, FakeEmailService(EmailResult.Success("unused")), storage, maxRetries = 1)
            .processQueue()

        assertEquals(
            KindleSendStatus.FAILED.name,
            tx.transaction(READ_ONLY) { findUploadItemById(queueId)!!.status },
        )
    }

    @Test
    fun `a retryable failure keeps the file for the next attempt`(
        applicationModule: ApplicationModule,
        @TempDir uploadDir: Path,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val storage = storage(uploadDir)
        val (queueId, storedName) = seedUpload(tx, storage, "book.pdf", KindleUploadSourceFormat.PDF)

        worker(tx, FakeEmailService(EmailResult.RetryableFailure("throttled")), storage).processQueue()

        val item = tx.transaction(READ_ONLY) { findUploadItemById(queueId)!! }
        assertEquals(KindleSendStatus.PENDING.name, item.status)
        assertEquals(1, item.attempts)
        assertEquals(storedName, item.storedPath)
        assertTrue(storage.resolve(storedName).exists())
    }

    @Test
    fun `a failed conversion is retried and keeps the file`(
        applicationModule: ApplicationModule,
        @TempDir uploadDir: Path,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val storage = storage(uploadDir)
        val (queueId, storedName) = seedUpload(tx, storage, "notes.fb2", KindleUploadSourceFormat.FB2)
        val email = FakeEmailService(EmailResult.Success("unused"))

        worker(tx, email, storage, conversion = FailingConversionService()).processQueue()

        val item = tx.transaction(READ_ONLY) { findUploadItemById(queueId)!! }
        assertEquals(KindleSendStatus.PENDING.name, item.status)
        assertEquals(1, item.attempts)
        assertEquals(storedName, item.storedPath)
        assertTrue(storage.resolve(storedName).exists())
        assertEquals(0, email.calls)
    }

    // --- helpers ---

    private fun storage(uploadDir: Path) = KindleUploadStorage(
        uploadPath = uploadDir,
        maxUploadBytes = 1024,
    )

    private fun worker(
        tx: TransactionProvider,
        email: EmailService,
        storage: KindleUploadStorage,
        maxRetries: Int = 5,
        conversion: ConversionService = FakeConversionService(),
    ) = KindleSendWorker(
        emailService = email,
        transactionProvider = tx,
        queues = [
            UploadKindleQueue(
                storage = storage,
                conversionService = conversion,
            ),
        ],
        batchSize = 10,
        maxRetries = maxRetries,
    )

    private suspend fun seedUpload(
        tx: TransactionProvider,
        storage: KindleUploadStorage,
        fileName: String,
        sourceFormat: KindleUploadSourceFormat,
    ): SeededUpload {
        val stored = storage.store("book bytes".byteInputStream(), sourceFormat.extension)
        val queueId = tx.transaction(READ_WRITE) {
            createUploadItem(
                userId = 1,
                deviceId = 1,
                fileName = fileName,
                storedPath = stored.fileName,
                sizeBytes = stored.sizeBytes,
                sourceFormat = sourceFormat,
            ).id!!
        }
        return SeededUpload(queueId = queueId, storedName = stored.fileName)
    }

    private data class SeededUpload(
        val queueId: Int,
        val storedName: String,
    )

    private class FakeEmailService(private val result: EmailResult) : EmailService {
        var calls = 0
        var lastAttachmentFileName: String? = null

        override suspend fun sendBookToKindle(
            recipientEmail: String,
            bookFile: File,
            bookTitle: String,
            attachmentFileName: String,
        ): EmailResult {
            calls++
            lastAttachmentFileName = attachmentFileName
            return result
        }
    }

    private class FailingConversionService : ConversionService {
        override fun getSupportedFormats() = listOf("epub")

        override fun isFormatSupported(format: String) = format == "epub"

        override suspend fun convertFb2(
            inputFile: File,
            outputFormat: String,
            outputFile: File,
        ): ConversionResult =
            ConversionResult(
                success = false,
                outputFile = null,
                errorMessage = "pandoc is not installed",
            )
    }

    private class FakeConversionService : ConversionService {
        var lastInputParent: File? = null

        override fun getSupportedFormats() = listOf("epub")

        override fun isFormatSupported(format: String) = format == "epub"

        override suspend fun convertFb2(
            inputFile: File,
            outputFormat: String,
            outputFile: File,
        ): ConversionResult {
            lastInputParent = inputFile.parentFile
            outputFile.writeText("converted")
            return ConversionResult(success = true, outputFile = outputFile)
        }
    }
}
