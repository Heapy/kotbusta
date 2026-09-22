package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.dao.countQueueItemsByUserId
import io.heapy.kotbusta.dao.countTodayQueueItemsByUserId
import io.heapy.kotbusta.dao.countTodayUploadItemsByUserId
import io.heapy.kotbusta.dao.countUploadItemsByUserId
import io.heapy.kotbusta.dao.createKindleDevice
import io.heapy.kotbusta.dao.createKindleSendEvent
import io.heapy.kotbusta.dao.createQueueItem
import io.heapy.kotbusta.dao.createUploadItem
import io.heapy.kotbusta.dao.deleteKindleDevice
import io.heapy.kotbusta.dao.findKindleDeviceByIdAndUserId
import io.heapy.kotbusta.dao.findKindleDeviceByUserIdAndEmail
import io.heapy.kotbusta.dao.findKindleDevicesByUserId
import io.heapy.kotbusta.dao.findSendHistoryByUserId
import io.heapy.kotbusta.dao.getBookById
import io.heapy.kotbusta.dao.updateKindleDevice
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.CreateDeviceRequest
import io.heapy.kotbusta.model.DeviceResponse
import io.heapy.kotbusta.model.EnqueueResponse
import io.heapy.kotbusta.model.KindleFormat
import io.heapy.kotbusta.model.KindleUploadSourceFormat
import io.heapy.kotbusta.model.SendHistoryResult
import io.heapy.kotbusta.model.SendToKindleRequest
import io.heapy.kotbusta.model.UpdateDeviceRequest
import io.heapy.kotbusta.worker.QueuedEventDetails
import kotlinx.serialization.json.Json
import org.jooq.exception.DataAccessException
import java.sql.SQLException
import java.time.ZoneId
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

class KindleService(
    private val dailyQuotaLimit: Int,
    private val timeService: TimeService,
    private val quotaZoneId: ZoneId = ZoneId.systemDefault(),
) {
    // Device CRUD operations
    context(_: TransactionContext, userSession: UserSession)
    fun getUserDevices(): List<DeviceResponse> {
        val devices = findKindleDevicesByUserId(userSession.userId)
        return devices.map { device ->
            DeviceResponse(
                id = device.id!!,
                email = device.email,
                name = device.name,
                createdAt = device.createdAt,
            )
        }
    }

    context(_: TransactionContext, userSession: UserSession)
    fun createDevice(request: CreateDeviceRequest): DeviceResponse {
        // Validate email format
        if (!isValidKindleEmail(request.email)) {
            throw IllegalArgumentException("Email must be a valid Kindle email address (ending with @kindle.com)")
        }

        // Check for duplicate
        val existing = findKindleDeviceByUserIdAndEmail(
            userSession.userId,
            request.email,
        )
        if (existing != null) {
            throw IllegalArgumentException("A device with this email already exists")
        }

        try {
            val device = createKindleDevice(
                userId = userSession.userId,
                email = request.email,
                name = request.name,
            )

            log.info("Created Kindle device ${device.id} for user ${userSession.userId}")

            return DeviceResponse(
                id = device.id!!,
                email = device.email,
                name = device.name,
                createdAt = device.createdAt,
            )
        } catch (e: DataAccessException) {
            // Handle race condition: concurrent requests may both pass the pre-check
            // but fail on the unique constraint (user_id, email)
            val cause = e.cause
            if (cause is SQLException && cause.sqlState == "23505") {
                throw IllegalArgumentException("A device with this email already exists")
            }
            throw e
        }
    }

    context(_: TransactionContext, userSession: UserSession)
    fun updateDevice(
        deviceId: Int,
        request: UpdateDeviceRequest,
    ): DeviceResponse {
        val device = findKindleDeviceByIdAndUserId(
            deviceId,
            userSession.userId,
        )
            ?: throw NoSuchElementException("Device not found or does not belong to user")

        val updated = updateKindleDevice(
            id = deviceId,
            userId = userSession.userId,
            name = request.name,
        )

        if (!updated) {
            throw IllegalStateException("Failed to update device")
        }

        log.info("Updated Kindle device $deviceId for user ${userSession.userId}")

        return DeviceResponse(
            id = device.id!!,
            email = device.email,
            name = request.name,
            createdAt = device.createdAt,
        )
    }

    context(_: TransactionContext, userSession: UserSession)
    fun deleteDevice(deviceId: Int): Boolean {
        val deleted = deleteKindleDevice(deviceId, userSession.userId)

        if (deleted) {
            log.info("Deleted Kindle device $deviceId for user ${userSession.userId}")
        }

        return deleted
    }

    // Send-to-Kindle operations
    context(_: TransactionContext, userSession: UserSession)
    fun enqueueSend(
        bookId: Int,
        request: SendToKindleRequest,
    ): EnqueueResponse {
        // Catalog books are stored as FB2 and converted on the fly; the catalog
        // queue's CHECK constraint accepts EPUB alone, so reject anything else
        // before it reaches the insert.
        if (request.format != KindleFormat.EPUB) {
            throw IllegalArgumentException("Books from the catalog can only be sent as EPUB")
        }

        // Verify device ownership
        val device = findKindleDeviceByIdAndUserId(
            request.deviceId,
            userSession.userId,
        )
            ?: throw NoSuchElementException("Device not found or does not belong to user")

        // Verify book access
        val book = getBookById(bookId)
            ?: throw NoSuchElementException("Book not found")

        requireQuota()

        // Create queue entry
        val queueItem = createQueueItem(
            userId = userSession.userId,
            deviceId = request.deviceId,
            bookId = bookId,
            bookTitle = book.title,
            format = request.format,
        )

        // Create QUEUED event
        val _ = createKindleSendEvent(
            queueId = queueItem.id!!,
            eventType = "QUEUED",
            details = Json.encodeToString(
                QueuedEventDetails(
                    bookId = bookId,
                    bookTitle = book.title,
                    deviceId = request.deviceId,
                    format = request.format.name,
                ),
            ),
        )

        log.info("Enqueued book $bookId to be sent to device ${request.deviceId} for user ${userSession.userId}")

        return EnqueueResponse(queueId = queueItem.id!!)
    }

    context(_: TransactionContext, userSession: UserSession)
    fun enqueueUpload(
        deviceId: Int,
        fileName: String,
        storedPath: String,
        sizeBytes: Int,
        sourceFormat: KindleUploadSourceFormat,
    ): EnqueueResponse {
        val _ = findKindleDeviceByIdAndUserId(deviceId, userSession.userId)
            ?: throw NoSuchElementException("Device not found or does not belong to user")

        requireQuota()

        val item = createUploadItem(
            userId = userSession.userId,
            deviceId = deviceId,
            fileName = fileName,
            storedPath = storedPath,
            sizeBytes = sizeBytes,
            sourceFormat = sourceFormat,
        )

        log.info("Enqueued upload '$fileName' to be sent to device $deviceId for user ${userSession.userId}")

        return EnqueueResponse(queueId = item.id!!)
    }

    /**
     * Rejects an upload before its bytes are read, so a user over quota or naming
     * an unknown device never gets a file written to disk.
     */
    context(_: TransactionContext, userSession: UserSession)
    fun checkUploadAllowed(deviceId: Int) {
        val _ = findKindleDeviceByIdAndUserId(deviceId, userSession.userId)
            ?: throw NoSuchElementException("Device not found or does not belong to user")
        requireQuota()
    }

    context(_: TransactionContext, userSession: UserSession)
    fun getSendHistory(limit: Int = 20, offset: Int = 0): SendHistoryResult {
        val limitCapped = limit.coerceIn(1, 100)
        val offsetSafe = offset.coerceAtLeast(0)

        val items = findSendHistoryByUserId(
            userId = userSession.userId,
            limit = limitCapped + 1, // Fetch one extra to check for more
            offset = offsetSafe,
        )

        val hasMore = items.size > limitCapped
        val total = countQueueItemsByUserId(userSession.userId) +
            countUploadItemsByUserId(userSession.userId)

        return SendHistoryResult(
            items = if (hasMore) items.dropLast(1) else items,
            total = total,
            hasMore = hasMore,
        )
    }

    context(_: TransactionContext, userSession: UserSession)
    private fun requireQuota() {
        val startOfDay = startOfToday()
        val todayCount = countTodayQueueItemsByUserId(userSession.userId, startOfDay) +
            countTodayUploadItemsByUserId(userSession.userId, startOfDay)
        if (todayCount >= dailyQuotaLimit) {
            throw QuotaExceededException("Daily send limit of $dailyQuotaLimit reached")
        }
    }

    private fun startOfToday(): Instant =
        java.time.Instant.ofEpochMilli(timeService.now().toEpochMilliseconds())
            .atZone(quotaZoneId)
            .toLocalDate()
            .atStartOfDay(quotaZoneId)
            .toInstant()
            .toKotlinInstant()

    private companion object : Logger()
}

internal fun isValidKindleEmail(email: String): Boolean =
    email.isNotBlank() &&
        email.none { it.isISOControl() || it.isWhitespace() } &&
        email.endsWith("@kindle.com", ignoreCase = true)

class QuotaExceededException(message: String) : Exception(message)
