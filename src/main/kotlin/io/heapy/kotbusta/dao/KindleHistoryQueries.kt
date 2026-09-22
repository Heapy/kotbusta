package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.KINDLE_DEVICES
import io.heapy.kotbusta.jooq.tables.references.KINDLE_SEND_QUEUE
import io.heapy.kotbusta.jooq.tables.references.KINDLE_UPLOAD_QUEUE
import io.heapy.kotbusta.mapper.TypeMapper
import io.heapy.kotbusta.mapper.mapUsing
import io.heapy.kotbusta.model.KindleSendSource
import io.heapy.kotbusta.model.KindleSendSource.CATALOG
import io.heapy.kotbusta.model.KindleSendSource.UPLOAD
import io.heapy.kotbusta.model.SendHistoryResponse
import org.jooq.Field
import org.jooq.impl.DSL

val KindleSendSourceMapper = TypeMapper<KindleSendSource, String>(
    left = { input -> input.name },
    right = { output -> KindleSendSource.valueOf(output) },
)

/**
 * Returns one page of a user's send history across both queues, ordered and paged
 * by the database. The two queues keep separate id sequences, so a row is
 * identified by [SendHistoryResponse.source] together with its id.
 */
context(_: TransactionContext)
fun findSendHistoryByUserId(
    userId: Int,
    limit: Int,
    offset: Int,
): List<SendHistoryResponse> = useTx { dslContext ->
    // Aliasing the generated columns keeps their converters, so the union's
    // records still read back as the types the model expects.
    val id = KINDLE_SEND_QUEUE.ID.`as`(ID_ALIAS)
    val deviceName = KINDLE_DEVICES.NAME.`as`(DEVICE_NAME_ALIAS)
    val title = KINDLE_SEND_QUEUE.BOOK_TITLE.`as`(TITLE_ALIAS)
    val format = KINDLE_SEND_QUEUE.FORMAT.`as`(FORMAT_ALIAS)
    val status = KINDLE_SEND_QUEUE.STATUS.`as`(STATUS_ALIAS)
    val createdAt = KINDLE_SEND_QUEUE.CREATED_AT.`as`(CREATED_AT_ALIAS)
    val lastError = KINDLE_SEND_QUEUE.LAST_ERROR.`as`(LAST_ERROR_ALIAS)
    val source: Field<String> = DSL.field(DSL.name(SOURCE_ALIAS), String::class.java)

    val catalogSends = dslContext
        .select(
            id,
            DSL.inline(CATALOG.name).`as`(SOURCE_ALIAS),
            deviceName,
            title,
            format,
            status,
            createdAt,
            lastError,
        )
        .from(KINDLE_SEND_QUEUE)
        .join(KINDLE_DEVICES)
        .on(KINDLE_SEND_QUEUE.DEVICE_ID.eq(KINDLE_DEVICES.ID))
        .where(KINDLE_SEND_QUEUE.USER_ID.eq(userId))

    val uploadSends = dslContext
        .select(
            KINDLE_UPLOAD_QUEUE.ID.`as`(ID_ALIAS),
            DSL.inline(UPLOAD.name).`as`(SOURCE_ALIAS),
            KINDLE_DEVICES.NAME.`as`(DEVICE_NAME_ALIAS),
            KINDLE_UPLOAD_QUEUE.FILE_NAME.`as`(TITLE_ALIAS),
            KINDLE_UPLOAD_QUEUE.SEND_FORMAT.`as`(FORMAT_ALIAS),
            KINDLE_UPLOAD_QUEUE.STATUS.`as`(STATUS_ALIAS),
            KINDLE_UPLOAD_QUEUE.CREATED_AT.`as`(CREATED_AT_ALIAS),
            KINDLE_UPLOAD_QUEUE.LAST_ERROR.`as`(LAST_ERROR_ALIAS),
        )
        .from(KINDLE_UPLOAD_QUEUE)
        .join(KINDLE_DEVICES)
        .on(KINDLE_UPLOAD_QUEUE.DEVICE_ID.eq(KINDLE_DEVICES.ID))
        .where(KINDLE_UPLOAD_QUEUE.USER_ID.eq(userId))

    catalogSends
        .unionAll(uploadSends)
        // A compound select can only order by its result columns, and the extra
        // keys make paging stable when timestamps tie.
        .orderBy(
            DSL.field(DSL.name(CREATED_AT_ALIAS)).desc(),
            DSL.field(DSL.name(SOURCE_ALIAS)).asc(),
            DSL.field(DSL.name(ID_ALIAS)).desc(),
        )
        .limit(limit)
        .offset(offset)
        .fetch { record ->
            SendHistoryResponse(
                id = record.get(id)!!,
                source = record.get(source)!! mapUsing KindleSendSourceMapper,
                deviceName = record.get(deviceName)!!,
                bookTitle = record.get(title)!!,
                format = record.get(format)!! mapUsing KindleFormatMapper,
                status = record.get(status)!! mapUsing KindleSendStatusMapper,
                createdAt = record.get(createdAt)!!,
                lastError = record.get(lastError),
            )
        }
}

private const val ID_ALIAS = "HISTORY_ID"
private const val SOURCE_ALIAS = "HISTORY_SOURCE"
private const val DEVICE_NAME_ALIAS = "HISTORY_DEVICE_NAME"
private const val TITLE_ALIAS = "HISTORY_TITLE"
private const val FORMAT_ALIAS = "HISTORY_FORMAT"
private const val STATUS_ALIAS = "HISTORY_STATUS"
private const val CREATED_AT_ALIAS = "HISTORY_CREATED_AT"
private const val LAST_ERROR_ALIAS = "HISTORY_LAST_ERROR"
