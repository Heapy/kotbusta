package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.records.KindleUploadQueueRecord
import io.heapy.kotbusta.jooq.tables.references.KINDLE_UPLOAD_QUEUE
import io.heapy.kotbusta.mapper.TypeMapper
import io.heapy.kotbusta.mapper.mapUsing
import io.heapy.kotbusta.model.KindleSendStatus
import io.heapy.kotbusta.model.KindleSendStatus.PENDING
import io.heapy.kotbusta.model.KindleSendStatus.PROCESSING
import io.heapy.kotbusta.model.KindleUploadSourceFormat
import kotlin.time.Clock
import kotlin.time.Instant

val KindleUploadSourceFormatMapper = TypeMapper<KindleUploadSourceFormat, String>(
    left = { input -> input.name },
    right = { output -> KindleUploadSourceFormat.valueOf(output) },
)

context(_: TransactionContext)
fun createUploadItem(
    userId: Int,
    deviceId: Int,
    fileName: String,
    storedPath: String,
    sizeBytes: Int,
    sourceFormat: KindleUploadSourceFormat,
    createdAt: Instant = Clock.System.now(),
    updatedAt: Instant = Clock.System.now(),
): KindleUploadQueueRecord = useTx { dslContext ->
    dslContext
        .insertInto(KINDLE_UPLOAD_QUEUE)
        .set(KINDLE_UPLOAD_QUEUE.USER_ID, userId)
        .set(KINDLE_UPLOAD_QUEUE.DEVICE_ID, deviceId)
        .set(KINDLE_UPLOAD_QUEUE.FILE_NAME, fileName)
        .set(KINDLE_UPLOAD_QUEUE.STORED_PATH, storedPath)
        .set(KINDLE_UPLOAD_QUEUE.SIZE_BYTES, sizeBytes)
        .set(KINDLE_UPLOAD_QUEUE.SOURCE_FORMAT, sourceFormat mapUsing KindleUploadSourceFormatMapper)
        .set(KINDLE_UPLOAD_QUEUE.SEND_FORMAT, sourceFormat.sendFormat mapUsing KindleFormatMapper)
        .set(KINDLE_UPLOAD_QUEUE.STATUS, PENDING mapUsing KindleSendStatusMapper)
        .set(KINDLE_UPLOAD_QUEUE.ATTEMPTS, 0)
        .set(KINDLE_UPLOAD_QUEUE.NEXT_RUN_AT, createdAt)
        .set(KINDLE_UPLOAD_QUEUE.CREATED_AT, createdAt)
        .set(KINDLE_UPLOAD_QUEUE.UPDATED_AT, updatedAt)
        .returning()
        .fetchOne()
        ?: error("Failed to create upload queue item")
}

context(_: TransactionContext)
fun findUploadItemById(id: Int): KindleUploadQueueRecord? = useTx { dslContext ->
    dslContext
        .selectFrom(KINDLE_UPLOAD_QUEUE)
        .where(KINDLE_UPLOAD_QUEUE.ID.eq(id))
        .fetchOne()
}

context(_: TransactionContext)
fun findPendingUploadItems(
    limit: Int,
    now: Instant = Clock.System.now(),
): List<KindleUploadQueueRecord> = useTx { dslContext ->
    dslContext
        .selectFrom(KINDLE_UPLOAD_QUEUE)
        .where(
            KINDLE_UPLOAD_QUEUE.STATUS.eq(PENDING mapUsing KindleSendStatusMapper)
                .and(KINDLE_UPLOAD_QUEUE.NEXT_RUN_AT.le(now)),
        )
        .orderBy(KINDLE_UPLOAD_QUEUE.NEXT_RUN_AT.asc())
        .limit(limit)
        .fetch()
}

context(_: TransactionContext)
fun markUploadItemAsProcessing(id: Int): Boolean = useTx { dslContext ->
    val updatedRows = dslContext
        .update(KINDLE_UPLOAD_QUEUE)
        .set(KINDLE_UPLOAD_QUEUE.STATUS, PROCESSING mapUsing KindleSendStatusMapper)
        .set(KINDLE_UPLOAD_QUEUE.UPDATED_AT, Clock.System.now())
        .where(
            KINDLE_UPLOAD_QUEUE.ID.eq(id)
                .and(KINDLE_UPLOAD_QUEUE.STATUS.eq(PENDING mapUsing KindleSendStatusMapper)),
        )
        .execute()
    updatedRows > 0
}

context(_: TransactionContext)
fun resetStuckProcessingUploadItems(
    olderThan: Instant,
    now: Instant = Clock.System.now(),
): Int = useTx { dslContext ->
    dslContext
        .update(KINDLE_UPLOAD_QUEUE)
        .set(KINDLE_UPLOAD_QUEUE.STATUS, PENDING mapUsing KindleSendStatusMapper)
        .set(KINDLE_UPLOAD_QUEUE.NEXT_RUN_AT, now)
        .set(KINDLE_UPLOAD_QUEUE.UPDATED_AT, now)
        .where(
            KINDLE_UPLOAD_QUEUE.STATUS.eq(PROCESSING mapUsing KindleSendStatusMapper)
                .and(KINDLE_UPLOAD_QUEUE.UPDATED_AT.lt(olderThan)),
        )
        .execute()
}

context(_: TransactionContext)
fun updateUploadItemStatus(
    id: Int,
    status: KindleSendStatus,
    error: String? = null,
    updatedAt: Instant = Clock.System.now(),
): Boolean = useTx { dslContext ->
    val updatedRows = dslContext
        .update(KINDLE_UPLOAD_QUEUE)
        .set(KINDLE_UPLOAD_QUEUE.STATUS, status mapUsing KindleSendStatusMapper)
        .set(KINDLE_UPLOAD_QUEUE.LAST_ERROR, error)
        .set(KINDLE_UPLOAD_QUEUE.UPDATED_AT, updatedAt)
        .where(KINDLE_UPLOAD_QUEUE.ID.eq(id))
        .execute()
    updatedRows > 0
}

/**
 * Detaches the stored file from the row once the send reached a terminal status:
 * the row stays as send history, but nothing refers to the file any more, so the
 * worker can unlink it and the startup sweep treats a leftover as an orphan.
 */
context(_: TransactionContext)
fun clearUploadItemStoredPath(id: Int): Boolean = useTx { dslContext ->
    val updatedRows = dslContext
        .update(KINDLE_UPLOAD_QUEUE)
        .setNull(KINDLE_UPLOAD_QUEUE.STORED_PATH)
        .where(KINDLE_UPLOAD_QUEUE.ID.eq(id))
        .execute()
    updatedRows > 0
}

context(_: TransactionContext)
fun incrementUploadItemAttempts(
    id: Int,
    nextRunAt: Instant,
    updatedAt: Instant = Clock.System.now(),
): Boolean = useTx { dslContext ->
    val updatedRows = dslContext
        .update(KINDLE_UPLOAD_QUEUE)
        .set(KINDLE_UPLOAD_QUEUE.ATTEMPTS, KINDLE_UPLOAD_QUEUE.ATTEMPTS.plus(1))
        .set(KINDLE_UPLOAD_QUEUE.NEXT_RUN_AT, nextRunAt)
        .set(KINDLE_UPLOAD_QUEUE.UPDATED_AT, updatedAt)
        .where(KINDLE_UPLOAD_QUEUE.ID.eq(id))
        .execute()
    updatedRows > 0
}

context(_: TransactionContext)
fun countUploadItemsByUserId(userId: Int): Long = useTx { dslContext ->
    dslContext
        .selectCount()
        .from(KINDLE_UPLOAD_QUEUE)
        .where(KINDLE_UPLOAD_QUEUE.USER_ID.eq(userId))
        .fetchOne(0, Long::class.java) ?: 0L
}

context(_: TransactionContext)
fun countTodayUploadItemsByUserId(
    userId: Int,
    startOfDay: Instant,
): Int = useTx { dslContext ->
    dslContext
        .selectCount()
        .from(KINDLE_UPLOAD_QUEUE)
        .where(
            KINDLE_UPLOAD_QUEUE.USER_ID.eq(userId)
                .and(KINDLE_UPLOAD_QUEUE.CREATED_AT.ge(startOfDay)),
        )
        .fetchOne(0, Int::class.java) ?: 0
}

context(_: TransactionContext)
fun findReferencedUploadPaths(): Set<String> = useTx { dslContext ->
    dslContext
        .select(KINDLE_UPLOAD_QUEUE.STORED_PATH)
        .from(KINDLE_UPLOAD_QUEUE)
        .where(KINDLE_UPLOAD_QUEUE.STORED_PATH.isNotNull)
        .fetchSet(KINDLE_UPLOAD_QUEUE.STORED_PATH)
        .filterNotNullTo(mutableSetOf())
}
