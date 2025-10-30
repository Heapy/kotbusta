package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.KINDLE_SEND_EVENTS
import io.heapy.kotbusta.model.KindleFormat
import io.heapy.kotbusta.model.KindleSendStatus
import io.heapy.kotbusta.test.DatabaseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Integration tests for KindleQueries.
 *
 * Tests all database query methods that use useTx for Kindle-related operations.
 */
@ExtendWith(DatabaseExtension::class)
class KindleQueriesTest {
    @Test
    context(_: TransactionProvider)
    fun `createKindleDevice should insert and return device`() = transaction {
        // When: Creating a new Kindle device
        val device = createKindleDevice(
            userId = 1,
            email = "test@kindle.com",
            name = "Test Kindle",
        )

        // Then: Device should be created with correct data
        assertNotNull(device.id)
        assertEquals(1, device.userId)
        assertEquals("test@kindle.com", device.email)
        assertEquals("Test Kindle", device.name)
        assertNotNull(device.createdAt)
        assertNotNull(device.updatedAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `findKindleDevicesByUserId should return user's devices`() = transaction {
        // Given: Test fixtures with 2 devices for user 1
        // When: Finding devices for user 1
        val devices = findKindleDevicesByUserId(userId = 1)

        // Then: Should return 2 devices
        assertEquals(2, devices.size)
        assertTrue(devices.all { it.userId == 1 })
        // Should be ordered by created_at desc
        assertTrue(devices[0].createdAt >= devices[1].createdAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `findKindleDevicesByUserId should return empty list for user with no devices`() = transaction {
        // Given: User 3 has no devices in fixtures
        // When: Finding devices for user 3
        val devices = findKindleDevicesByUserId(userId = 3)

        // Then: Should return empty list
        assertTrue(devices.isEmpty())
    }

    @Test
    context(_: TransactionProvider)
    fun `findKindleDeviceByIdAndUserId should return device when found`() = transaction {
        // Given: Device ID 1 belongs to user 1
        // When: Finding device by ID and user ID
        val device = findKindleDeviceByIdAndUserId(id = 1, userId = 1)

        // Then: Should return the device
        assertNotNull(device)
        assertEquals(1, device?.id)
        assertEquals(1, device?.userId)
        assertEquals("john.doe_kindle@kindle.com", device?.email)
    }

    @Test
    context(_: TransactionProvider)
    fun `findKindleDeviceByIdAndUserId should return null for wrong user`() = transaction {
        // Given: Device ID 1 belongs to user 1, not user 2
        // When: Finding device with wrong user ID
        val device = findKindleDeviceByIdAndUserId(id = 1, userId = 2)

        // Then: Should return null
        assertNull(device)
    }

    @Test
    context(_: TransactionProvider)
    fun `findKindleDeviceByUserIdAndEmail should return device when found`() = transaction {
        // Given: User 1 has device with specific email
        // When: Finding device by user ID and email
        val device = findKindleDeviceByUserIdAndEmail(
            userId = 1,
            email = "john.doe_kindle@kindle.com",
        )

        // Then: Should return the device
        assertNotNull(device)
        assertEquals(1, device?.userId)
        assertEquals("john.doe_kindle@kindle.com", device?.email)
    }

    @Test
    context(_: TransactionProvider)
    fun `findKindleDeviceByUserIdAndEmail should return null when not found`() = transaction {
        // Given: User 1 does not have device with this email
        // When: Finding device with non-existent email
        val device = findKindleDeviceByUserIdAndEmail(
            userId = 1,
            email = "nonexistent@kindle.com",
        )

        // Then: Should return null
        assertNull(device)
    }

    @Test
    context(_: TransactionProvider)
    fun `updateKindleDevice should update device name`() = transaction {
        // Given: Device ID 1 exists for user 1
        // When: Updating device name
        val updated = updateKindleDevice(
            id = 1,
            userId = 1,
            name = "Updated Kindle Name",
        )

        // Then: Should return true and update the name
        assertTrue(updated)

        val device = findKindleDeviceByIdAndUserId(id = 1, userId = 1)
        assertEquals("Updated Kindle Name", device?.name)
    }

    @Test
    context(_: TransactionProvider)
    fun `updateKindleDevice should return false for wrong user`() = transaction {
        // Given: Device ID 1 belongs to user 1
        // When: Trying to update with wrong user ID
        val updated = updateKindleDevice(
            id = 1,
            userId = 2,
            name = "Hacker's Kindle",
        )

        // Then: Should return false and not update
        assertFalse(updated)

        val device = findKindleDeviceByIdAndUserId(id = 1, userId = 1)
        assertEquals("John's Kindle Paperwhite", device?.name)
    }

    @Test
    context(_: TransactionProvider)
    fun `deleteKindleDevice should delete device`() = transaction {
        // Given: Device ID 1 exists for user 1
        // When: Deleting the device
        val deleted = deleteKindleDevice(id = 1, userId = 1)

        // Then: Should return true and delete the device
        assertTrue(deleted)

        val device = findKindleDeviceByIdAndUserId(id = 1, userId = 1)
        assertNull(device)
    }

    @Test
    context(_: TransactionProvider)
    fun `deleteKindleDevice should return false for wrong user`() = transaction {
        // Given: Device ID 1 belongs to user 1
        // When: Trying to delete with wrong user ID
        val deleted = deleteKindleDevice(id = 1, userId = 2)

        // Then: Should return false and not delete
        assertFalse(deleted)

        val device = findKindleDeviceByIdAndUserId(id = 1, userId = 1)
        assertNotNull(device)
    }

    // Queue Query Tests

    @Test
    context(_: TransactionProvider)
    fun `createQueueItem should insert and return queue item`() = transaction {
        // When: Creating a new queue item
        val queueItem = createQueueItem(
            userId = 1,
            deviceId = 1,
            bookId = 5,
            format = KindleFormat.EPUB,
        )

        // Then: Queue item should be created with correct data
        assertNotNull(queueItem.id)
        assertEquals(1, queueItem.userId)
        assertEquals(1, queueItem.deviceId)
        assertEquals(5, queueItem.bookId)
        assertEquals("EPUB", queueItem.format)
        assertEquals("PENDING", queueItem.status)
        assertEquals(0, queueItem.attempts)
        assertNotNull(queueItem.nextRunAt)
        assertNotNull(queueItem.createdAt)
        assertNotNull(queueItem.updatedAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `findQueueItemById should return queue item when found`() = transaction {
        // Given: Queue item ID 1 exists in fixtures
        // When: Finding queue item by ID
        val queueItem = findQueueItemById(id = 1)

        // Then: Should return the queue item
        assertNotNull(queueItem)
        assertEquals(1, queueItem?.id)
        assertEquals(1, queueItem?.userId)
        assertEquals(1, queueItem?.deviceId)
        assertEquals(1, queueItem?.bookId)
    }

    @Test
    context(_: TransactionProvider)
    fun `findQueueItemById should return null when not found`() = transaction {
        // Given: Queue item ID 999 does not exist
        // When: Finding non-existent queue item
        val queueItem = findQueueItemById(id = 999)

        // Then: Should return null
        assertNull(queueItem)
    }

    @Test
    context(_: TransactionProvider)
    fun `findQueueItemsByUserId should return send history with pagination`() = transaction {
        // Given: User 1 has 3 queue items in fixtures
        // When: Finding queue items for user 1 with pagination
        val history = findQueueItemsByUserId(
            userId = 1,
            limit = 10,
            offset = 0,
        )

        // Then: Should return 3 items with complete data
        assertEquals(3, history.size)

        val firstItem = history[0]
        assertNotNull(firstItem.id)
        assertNotNull(firstItem.deviceName)
        assertNotNull(firstItem.bookTitle)
        assertNotNull(firstItem.format)
        assertNotNull(firstItem.status)
        assertNotNull(firstItem.createdAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `findPendingQueueItems should return only pending items`() = transaction {
        // Given: Test fixtures with various queue item statuses
        // When: Finding pending queue items
        val pendingItems = findPendingQueueItems(
            limit = 10,
            now = Clock.System.now(),
        )

        // Then: Should return only items with PENDING status
        assertTrue(pendingItems.isNotEmpty())
        assertTrue(pendingItems.all { it.status == "PENDING" })
    }

    @Test
    context(_: TransactionProvider)
    fun `markQueueItemAsProcessing should update status from PENDING to PROCESSING`() = transaction {
        // Given: Queue item ID 2 is PENDING
        // When: Marking as processing
        val marked = markQueueItemAsProcessing(id = 2)

        // Then: Should return true and update status
        assertTrue(marked)

        val queueItem = findQueueItemById(id = 2)
        assertEquals("PROCESSING", queueItem?.status)
    }

    @Test
    context(_: TransactionProvider)
    fun `markQueueItemAsProcessing should return false for non-PENDING item`() = transaction {
        // Given: Queue item ID 1 is COMPLETED (not PENDING)
        // When: Trying to mark as processing
        val marked = markQueueItemAsProcessing(id = 1)

        // Then: Should return false
        assertFalse(marked)

        val queueItem = findQueueItemById(id = 1)
        assertEquals("COMPLETED", queueItem?.status)
    }

    @Test
    context(_: TransactionProvider)
    fun `updateQueueItemStatus should update status and error`() = transaction {
        // Given: Queue item ID 2 exists
        // When: Updating status to FAILED with error
        val updated = updateQueueItemStatus(
            id = 2,
            status = KindleSendStatus.FAILED,
            error = "Test error message",
        )

        // Then: Should return true and update both fields
        assertTrue(updated)

        val queueItem = findQueueItemById(id = 2)
        assertEquals("FAILED", queueItem?.status)
        assertEquals("Test error message", queueItem?.lastError)
    }

    @Test
    context(_: TransactionProvider)
    fun `incrementQueueItemAttempts should increment attempts and update nextRunAt`() = transaction {
        // Given: Queue item ID 2 has 0 attempts
        val originalItem = findQueueItemById(id = 2)
        val originalAttempts = originalItem?.attempts ?: 0

        // When: Incrementing attempts
        val nextRunAt = Clock.System.now()
        val updated = incrementQueueItemAttempts(
            id = 2,
            nextRunAt = nextRunAt,
        )

        // Then: Should return true and increment attempts
        assertTrue(updated)

        val queueItem = findQueueItemById(id = 2)
        assertEquals(originalAttempts + 1, queueItem?.attempts)
        assertEquals(nextRunAt, queueItem?.nextRunAt)
    }

    @Test
    context(_: TransactionProvider)
    fun `countQueueItemsByUserId should return correct count`() = transaction {
        // Given: User 1 has 3 queue items in fixtures
        // When: Counting queue items
        val count = countQueueItemsByUserId(userId = 1)

        // Then: Should return 3
        assertEquals(3L, count)
    }

    @Test
    context(_: TransactionProvider)
    fun `countQueueItemsByUserId should return 0 for user with no items`() = transaction {
        // Given: User 3 has no queue items
        // When: Counting queue items
        val count = countQueueItemsByUserId(userId = 3)

        // Then: Should return 0
        assertEquals(0L, count)
    }

    @Test
    context(_: TransactionProvider)
    fun `countTodayQueueItemsByUserId should return items created today`() = transaction {
        // Given: User 1 has queue items in fixtures
        // When: Counting items from start of 2024-01-22
        val count = countTodayQueueItemsByUserId(
            userId = 1,
            startOfDay = Instant.parse("2024-01-22T00:00:00Z"),
        )

        // Then: Should return items created on or after 2024-01-22
        assertTrue(count > 0)
    }

    // Event Query Tests

    @Test
    context(_: TransactionProvider)
    fun `createKindleSendEvent should insert and return event`() = transaction {
        // Given: Queue item ID 1 exists
        // When: Creating a new event
        val event = createKindleSendEvent(
            queueId = 1,
            eventType = "TEST_EVENT",
            details = "Test event details",
        )

        // Then: Event should be created with correct data
        assertNotNull(event.id)
        assertEquals(1, event.queueId)
        assertEquals("TEST_EVENT", event.eventType)
        assertEquals("Test event details", event.details)
        assertNotNull(event.createdAt)

        // Verify event was persisted
        val savedEvent = useTx { dslContext ->
            dslContext
                .selectFrom(KINDLE_SEND_EVENTS)
                .where(KINDLE_SEND_EVENTS.ID.eq(event.id))
                .fetchOne()
        }


        assertNotNull(savedEvent)
        assertEquals("TEST_EVENT", savedEvent?.eventType)
    }

    @Test
    context(_: TransactionProvider)
    fun `createKindleSendEvent should allow null details`() = transaction {
        // Given: Queue item ID 1 exists
        // When: Creating event without details
        val event = createKindleSendEvent(
            queueId = 1,
            eventType = "NO_DETAILS_EVENT",
            details = null,
        )

        // Then: Event should be created with null details
        assertNotNull(event.id)
        assertNull(event.details)
    }
}
