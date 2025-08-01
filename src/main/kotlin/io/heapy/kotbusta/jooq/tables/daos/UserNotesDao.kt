/*
 * This file is generated by jOOQ.
 */
package io.heapy.kotbusta.jooq.tables.daos


import io.heapy.kotbusta.jooq.tables.UserNotes
import io.heapy.kotbusta.jooq.tables.records.UserNotesRecord

import java.time.OffsetDateTime

import kotlin.collections.List

import org.jooq.Configuration
import org.jooq.impl.DAOImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("warnings")
open class UserNotesDao(configuration: Configuration?) : DAOImpl<UserNotesRecord, io.heapy.kotbusta.jooq.tables.pojos.UserNotes, Long>(UserNotes.USER_NOTES, io.heapy.kotbusta.jooq.tables.pojos.UserNotes::class.java, configuration) {

    /**
     * Create a new UserNotesDao without any configuration
     */
    constructor(): this(null)

    override fun getId(o: io.heapy.kotbusta.jooq.tables.pojos.UserNotes): Long? = o.id

    /**
     * Fetch records that have <code>id BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfId(lowerInclusive: Long?, upperInclusive: Long?): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetchRange(UserNotes.USER_NOTES.ID, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>id IN (values)</code>
     */
    fun fetchById(vararg values: Long): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetch(UserNotes.USER_NOTES.ID, *values.toTypedArray())

    /**
     * Fetch a unique record that has <code>id = value</code>
     */
    fun fetchOneById(value: Long): io.heapy.kotbusta.jooq.tables.pojos.UserNotes? = fetchOne(UserNotes.USER_NOTES.ID, value)

    /**
     * Fetch records that have <code>user_id BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfUserId(lowerInclusive: Long, upperInclusive: Long): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetchRange(UserNotes.USER_NOTES.USER_ID, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>user_id IN (values)</code>
     */
    fun fetchByUserId(vararg values: Long): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetch(UserNotes.USER_NOTES.USER_ID, *values.toTypedArray())

    /**
     * Fetch records that have <code>book_id BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfBookId(lowerInclusive: Long, upperInclusive: Long): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetchRange(UserNotes.USER_NOTES.BOOK_ID, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>book_id IN (values)</code>
     */
    fun fetchByBookId(vararg values: Long): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetch(UserNotes.USER_NOTES.BOOK_ID, *values.toTypedArray())

    /**
     * Fetch records that have <code>note BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfNote(lowerInclusive: String, upperInclusive: String): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetchRange(UserNotes.USER_NOTES.NOTE, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>note IN (values)</code>
     */
    fun fetchByNote(vararg values: String): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetch(UserNotes.USER_NOTES.NOTE, *values)

    /**
     * Fetch records that have <code>is_private BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfIsPrivate(lowerInclusive: Boolean?, upperInclusive: Boolean?): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetchRange(UserNotes.USER_NOTES.IS_PRIVATE, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>is_private IN (values)</code>
     */
    fun fetchByIsPrivate(vararg values: Boolean): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetch(UserNotes.USER_NOTES.IS_PRIVATE, *values.toTypedArray())

    /**
     * Fetch records that have <code>created_at BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfCreatedAt(lowerInclusive: OffsetDateTime?, upperInclusive: OffsetDateTime?): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetchRange(UserNotes.USER_NOTES.CREATED_AT, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>created_at IN (values)</code>
     */
    fun fetchByCreatedAt(vararg values: OffsetDateTime): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetch(UserNotes.USER_NOTES.CREATED_AT, *values)

    /**
     * Fetch records that have <code>updated_at BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfUpdatedAt(lowerInclusive: OffsetDateTime?, upperInclusive: OffsetDateTime?): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetchRange(UserNotes.USER_NOTES.UPDATED_AT, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>updated_at IN (values)</code>
     */
    fun fetchByUpdatedAt(vararg values: OffsetDateTime): List<io.heapy.kotbusta.jooq.tables.pojos.UserNotes> = fetch(UserNotes.USER_NOTES.UPDATED_AT, *values)
}
