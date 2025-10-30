package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.dao.countQueueItemsByUserId
import io.heapy.kotbusta.dao.countTodayQueueItemsByUserId
import io.heapy.kotbusta.dao.createKindleDevice
import io.heapy.kotbusta.dao.createKindleSendEvent
import io.heapy.kotbusta.dao.createQueueItem
import io.heapy.kotbusta.dao.deleteKindleDevice
import io.heapy.kotbusta.dao.findKindleDeviceByIdAndUserId
import io.heapy.kotbusta.dao.findKindleDeviceByUserIdAndEmail
import io.heapy.kotbusta.dao.findKindleDevicesByUserId
import io.heapy.kotbusta.dao.findQueueItemsByUserId
import io.heapy.kotbusta.dao.getBookById
import io.heapy.kotbusta.dao.updateKindleDevice
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.CreateDeviceRequest
import io.heapy.kotbusta.model.DeviceResponse
import io.heapy.kotbusta.model.EnqueueResponse
import io.heapy.kotbusta.model.KindleFormat
import io.heapy.kotbusta.model.SendHistoryResult
import io.heapy.kotbusta.model.SendToKindleRequest
import io.heapy.kotbusta.model.UpdateDeviceRequest
import io.heapy.kotbusta.worker.QueuedEventDetails
import kotlinx.serialization.json.Json
import org.jooq.exception.DataAccessException
import java.sql.SQLException
import kotlin.time.Clock

class KindleService(
    private val dailyQuotaLimit: Int,
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
        if (!request.email.endsWith("@kindle.com", ignoreCase = true)) {
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
                createdAt = device.createdAt!!,
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
            createdAt = device.createdAt!!,
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
        // Verify device ownership
        val device = findKindleDeviceByIdAndUserId(
            request.deviceId,
            userSession.userId,
        )
            ?: throw NoSuchElementException("Device not found or does not belong to user")

        // Verify book access
        val book = getBookById(bookId)
            ?: throw NoSuchElementException("Book not found")

        // Check daily quota
        val startOfDay = Clock.System.now().minus(
            kotlin.time.Duration.parse("PT${Clock.System.now().toEpochMilliseconds() % (24 * 60 * 60 * 1000)}ms")
        )
        val todayCount = countTodayQueueItemsByUserId(userSession.userId, startOfDay)
        if (todayCount >= dailyQuotaLimit) {
            throw QuotaExceededException("Daily send limit of $dailyQuotaLimit reached")
        }

        // Create queue entry
        val queueItem = createQueueItem(
            userId = userSession.userId,
            deviceId = request.deviceId,
            bookId = bookId,
            format = request.format,
        )

        // Create QUEUED event
        createKindleSendEvent(
            queueId = queueItem.id!!,
            eventType = "QUEUED",
            details = Json.encodeToString(
                QueuedEventDetails(
                    bookId = bookId,
                    deviceId = request.deviceId,
                    format = request.format.name,
                ),
            ),
        )

        log.info("Enqueued book $bookId to be sent to device ${request.deviceId} for user ${userSession.userId}")

        return EnqueueResponse(queueId = queueItem.id!!)
    }

    context(_: TransactionContext, userSession: UserSession)
    fun getSendHistory(limit: Int = 20, offset: Int = 0): SendHistoryResult {
        val limitCapped = limit.coerceIn(1, 100)
        val offsetCapped = offset.coerceAtLeast(0)

        val items = findQueueItemsByUserId(
            userId = userSession.userId,
            limit = limitCapped + 1, // Fetch one extra to check for more
            offset = offsetCapped,
        )

        val hasMore = items.size > limitCapped
        val resultItems = if (hasMore) items.dropLast(1) else items
        val total = countQueueItemsByUserId(userSession.userId)

        return SendHistoryResult(
            items = resultItems,
            total = total,
            hasMore = hasMore,
        )
    }

    context(_: TransactionContext)
    fun checkDailyQuota(userId: Int): Boolean {
        val startOfDay = Clock.System.now().minus(
            kotlin.time.Duration.parse("PT${Clock.System.now().toEpochMilliseconds() % (24 * 60 * 60 * 1000)}ms")
        )
        val todayCount = countTodayQueueItemsByUserId(userId, startOfDay)
        return todayCount < dailyQuotaLimit
    }

    private companion object : Logger()
}

class QuotaExceededException(message: String) : Exception(message)
