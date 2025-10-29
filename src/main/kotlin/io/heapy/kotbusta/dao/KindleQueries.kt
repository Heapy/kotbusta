package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.records.KindleDevicesRecord
import io.heapy.kotbusta.jooq.tables.records.KindleSendEventsRecord
import io.heapy.kotbusta.jooq.tables.records.KindleSendQueueRecord
import io.heapy.kotbusta.jooq.tables.references.BOOKS
import io.heapy.kotbusta.jooq.tables.references.KINDLE_DEVICES
import io.heapy.kotbusta.jooq.tables.references.KINDLE_SEND_EVENTS
import io.heapy.kotbusta.jooq.tables.references.KINDLE_SEND_QUEUE
import io.heapy.kotbusta.mapper.TypeMapper
import io.heapy.kotbusta.mapper.mapUsing
import io.heapy.kotbusta.model.KindleFormat
import io.heapy.kotbusta.model.KindleSendStatus
import io.heapy.kotbusta.model.SendHistoryResponse
import kotlin.time.Clock
import kotlin.time.Instant

val KindleFormatMapper = TypeMapper<KindleFormat, String>(
    left = { input -> input.name },
    right = { output -> KindleFormat.valueOf(output) },
)

val KindleSendStatusMapper = TypeMapper<KindleSendStatus, String>(
    left = { input -> input.name },
    right = { output -> KindleSendStatus.valueOf(output) },
)

// Device queries

context(_: TransactionContext)
fun createKindleDevice(
    userId: Int,
    email: String,
    name: String,
    createdAt: Instant = Clock.System.now(),
    updatedAt: Instant = Clock.System.now(),
): KindleDevicesRecord = useTx { dslContext ->
    dslContext
        .insertInto(KINDLE_DEVICES)
        .set(KINDLE_DEVICES.USER_ID, userId)
        .set(KINDLE_DEVICES.EMAIL, email)
        .set(KINDLE_DEVICES.NAME, name)
        .set(KINDLE_DEVICES.CREATED_AT, createdAt)
        .set(KINDLE_DEVICES.UPDATED_AT, updatedAt)
        .returning()
        .fetchOne()!!
}

context(_: TransactionContext)
fun findKindleDevicesByUserId(
    userId: Int,
): List<KindleDevicesRecord> = useTx { dslContext ->
    dslContext
        .selectFrom(KINDLE_DEVICES)
        .where(KINDLE_DEVICES.USER_ID.eq(userId))
        .orderBy(KINDLE_DEVICES.CREATED_AT.desc())
        .fetch()
}

context(_: TransactionContext)
fun findKindleDeviceByIdAndUserId(
    id: Int,
    userId: Int,
): KindleDevicesRecord? = useTx { dslContext ->
    dslContext
        .selectFrom(KINDLE_DEVICES)
        .where(
            KINDLE_DEVICES.ID.eq(id)
                .and(KINDLE_DEVICES.USER_ID.eq(userId)),
        )
        .fetchOne()
}

context(_: TransactionContext)
fun findKindleDeviceByUserIdAndEmail(
    userId: Int,
    email: String,
): KindleDevicesRecord? = useTx { dslContext ->
    dslContext
        .selectFrom(KINDLE_DEVICES)
        .where(
            KINDLE_DEVICES.USER_ID.eq(userId)
                .and(KINDLE_DEVICES.EMAIL.eq(email)),
        )
        .fetchOne()
}

context(_: TransactionContext)
fun updateKindleDevice(
    id: Int,
    userId: Int,
    name: String,
    updatedAt: Instant = Clock.System.now(),
): Boolean = useTx { dslContext ->
    val updatedRows = dslContext
        .update(KINDLE_DEVICES)
        .set(KINDLE_DEVICES.NAME, name)
        .set(KINDLE_DEVICES.UPDATED_AT, updatedAt)
        .where(
            KINDLE_DEVICES.ID.eq(id)
                .and(KINDLE_DEVICES.USER_ID.eq(userId)),
        )
        .execute()
    updatedRows > 0
}

context(_: TransactionContext)
fun deleteKindleDevice(
    id: Int,
    userId: Int,
): Boolean = useTx { dslContext ->
    val deletedRows = dslContext
        .deleteFrom(KINDLE_DEVICES)
        .where(
            KINDLE_DEVICES.ID.eq(id)
                .and(KINDLE_DEVICES.USER_ID.eq(userId)),
        )
        .execute()
    deletedRows > 0
}

// Queue queries

context(_: TransactionContext)
fun createQueueItem(
    userId: Int,
    deviceId: Int,
    bookId: Int,
    format: KindleFormat,
    createdAt: Instant = Clock.System.now(),
    updatedAt: Instant = Clock.System.now(),
): KindleSendQueueRecord = useTx { dslContext ->
    dslContext
        .insertInto(KINDLE_SEND_QUEUE)
        .set(KINDLE_SEND_QUEUE.USER_ID, userId)
        .set(KINDLE_SEND_QUEUE.DEVICE_ID, deviceId)
        .set(KINDLE_SEND_QUEUE.BOOK_ID, bookId)
        .set(KINDLE_SEND_QUEUE.FORMAT, format.mapUsing(KindleFormatMapper))
        .set(KINDLE_SEND_QUEUE.STATUS, KindleSendStatus.PENDING.mapUsing(KindleSendStatusMapper))
        .set(KINDLE_SEND_QUEUE.ATTEMPTS, 0)
        .set(KINDLE_SEND_QUEUE.NEXT_RUN_AT, createdAt)
        .set(KINDLE_SEND_QUEUE.CREATED_AT, createdAt)
        .set(KINDLE_SEND_QUEUE.UPDATED_AT, updatedAt)
        .returning()
        .fetchOne()!!
}

context(_: TransactionContext)
fun findQueueItemById(id: Int): KindleSendQueueRecord? = useTx { dslContext ->
    dslContext
        .selectFrom(KINDLE_SEND_QUEUE)
        .where(KINDLE_SEND_QUEUE.ID.eq(id))
        .fetchOne()
}

context(_: TransactionContext)
fun findQueueItemsByUserId(
    userId: Int,
    limit: Int,
    offset: Int,
): List<SendHistoryResponse> = useTx { dslContext ->
    dslContext
        .select(
            KINDLE_SEND_QUEUE.ID,
            KINDLE_DEVICES.NAME,
            BOOKS.TITLE,
            KINDLE_SEND_QUEUE.FORMAT,
            KINDLE_SEND_QUEUE.STATUS,
            KINDLE_SEND_QUEUE.CREATED_AT,
            KINDLE_SEND_QUEUE.LAST_ERROR,
        )
        .from(KINDLE_SEND_QUEUE)
        .join(KINDLE_DEVICES).on(KINDLE_SEND_QUEUE.DEVICE_ID.eq(KINDLE_DEVICES.ID))
        .join(BOOKS).on(KINDLE_SEND_QUEUE.BOOK_ID.eq(BOOKS.ID))
        .where(KINDLE_SEND_QUEUE.USER_ID.eq(userId))
        .orderBy(KINDLE_SEND_QUEUE.CREATED_AT.desc())
        .limit(limit)
        .offset(offset)
        .fetch().map { record ->
            SendHistoryResponse(
                id = record.get(KINDLE_SEND_QUEUE.ID)!!,
                deviceName = record.get(KINDLE_DEVICES.NAME)!!,
                bookTitle = record.get(BOOKS.TITLE)!!,
                format = record.get(KINDLE_SEND_QUEUE.FORMAT)!!.mapUsing(KindleFormatMapper),
                status = record.get(KINDLE_SEND_QUEUE.STATUS)!!.mapUsing(KindleSendStatusMapper),
                createdAt = record.get(KINDLE_SEND_QUEUE.CREATED_AT)!!,
                lastError = record.get(KINDLE_SEND_QUEUE.LAST_ERROR),
            )
        }
}

context(_: TransactionContext)
fun findPendingQueueItems(
    limit: Int,
    now: Instant = Clock.System.now(),
): List<KindleSendQueueRecord> = useTx { dslContext ->
    dslContext
        .selectFrom(KINDLE_SEND_QUEUE)
        .where(
            KINDLE_SEND_QUEUE.STATUS.eq(KindleSendStatus.PENDING.mapUsing(KindleSendStatusMapper))
                .and(KINDLE_SEND_QUEUE.NEXT_RUN_AT.le(now)),
        )
        .orderBy(KINDLE_SEND_QUEUE.NEXT_RUN_AT.asc())
        .limit(limit)
        .fetch()
}

context(_: TransactionContext)
fun markQueueItemAsProcessing(id: Int): Boolean = useTx { dslContext ->
    val updatedRows = dslContext
        .update(KINDLE_SEND_QUEUE)
        .set(KINDLE_SEND_QUEUE.STATUS, KindleSendStatus.PROCESSING.mapUsing(KindleSendStatusMapper))
        .set(KINDLE_SEND_QUEUE.UPDATED_AT, Clock.System.now())
        .where(
            KINDLE_SEND_QUEUE.ID.eq(id)
                .and(KINDLE_SEND_QUEUE.STATUS.eq(KindleSendStatus.PENDING.mapUsing(KindleSendStatusMapper))),
        )
        .execute()
    updatedRows > 0
}

context(_: TransactionContext)
fun updateQueueItemStatus(
    id: Int,
    status: KindleSendStatus,
    error: String? = null,
    updatedAt: Instant = Clock.System.now(),
): Boolean = useTx { dslContext ->
    val updatedRows = dslContext
        .update(KINDLE_SEND_QUEUE)
        .set(KINDLE_SEND_QUEUE.STATUS, status.mapUsing(KindleSendStatusMapper))
        .set(KINDLE_SEND_QUEUE.LAST_ERROR, error)
        .set(KINDLE_SEND_QUEUE.UPDATED_AT, updatedAt)
        .where(KINDLE_SEND_QUEUE.ID.eq(id))
        .execute()
    updatedRows > 0
}

context(_: TransactionContext)
fun incrementQueueItemAttempts(
    id: Int,
    nextRunAt: Instant,
    updatedAt: Instant = Clock.System.now(),
): Boolean = useTx { dslContext ->
    val updatedRows = dslContext
        .update(KINDLE_SEND_QUEUE)
        .set(KINDLE_SEND_QUEUE.ATTEMPTS, KINDLE_SEND_QUEUE.ATTEMPTS.plus(1))
        .set(KINDLE_SEND_QUEUE.NEXT_RUN_AT, nextRunAt)
        .set(KINDLE_SEND_QUEUE.UPDATED_AT, updatedAt)
        .where(KINDLE_SEND_QUEUE.ID.eq(id))
        .execute()
    updatedRows > 0
}

context(_: TransactionContext)
fun countQueueItemsByUserId(userId: Int): Long = useTx { dslContext ->
    dslContext
        .selectCount()
        .from(KINDLE_SEND_QUEUE)
        .where(KINDLE_SEND_QUEUE.USER_ID.eq(userId))
        .fetchOne(0, Long::class.java) ?: 0L
}

context(_: TransactionContext)
fun countTodayQueueItemsByUserId(
    userId: Int,
    startOfDay: Instant,
): Int = useTx { dslContext ->
    dslContext
        .selectCount()
        .from(KINDLE_SEND_QUEUE)
        .where(
            KINDLE_SEND_QUEUE.USER_ID.eq(userId)
                .and(KINDLE_SEND_QUEUE.CREATED_AT.ge(startOfDay)),
        )
        .fetchOne(0, Int::class.java) ?: 0
}

// Event queries

context(_: TransactionContext)
fun createKindleSendEvent(
    queueId: Int,
    eventType: String,
    details: String? = null,
    createdAt: Instant = Clock.System.now(),
): KindleSendEventsRecord = useTx { dslContext ->
    dslContext
        .insertInto(KINDLE_SEND_EVENTS)
        .set(KINDLE_SEND_EVENTS.QUEUE_ID, queueId)
        .set(KINDLE_SEND_EVENTS.EVENT_TYPE, eventType)
        .set(KINDLE_SEND_EVENTS.DETAILS, details)
        .set(KINDLE_SEND_EVENTS.CREATED_AT, createdAt)
        .returning()
        .fetchOne()!!
}
