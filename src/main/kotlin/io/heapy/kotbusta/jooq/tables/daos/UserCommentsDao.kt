/*
 * This file is generated by jOOQ.
 */
package io.heapy.kotbusta.jooq.tables.daos


import io.heapy.kotbusta.jooq.tables.UserComments
import io.heapy.kotbusta.jooq.tables.records.UserCommentsRecord

import java.time.OffsetDateTime

import kotlin.collections.List

import org.jooq.Configuration
import org.jooq.impl.DAOImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("warnings")
open class UserCommentsDao(configuration: Configuration?) : DAOImpl<UserCommentsRecord, io.heapy.kotbusta.jooq.tables.pojos.UserComments, Long>(UserComments.USER_COMMENTS, io.heapy.kotbusta.jooq.tables.pojos.UserComments::class.java, configuration) {

    /**
     * Create a new UserCommentsDao without any configuration
     */
    constructor(): this(null)

    override fun getId(o: io.heapy.kotbusta.jooq.tables.pojos.UserComments): Long? = o.id

    /**
     * Fetch records that have <code>id BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfId(lowerInclusive: Long?, upperInclusive: Long?): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetchRange(UserComments.USER_COMMENTS.ID, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>id IN (values)</code>
     */
    fun fetchById(vararg values: Long): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetch(UserComments.USER_COMMENTS.ID, *values.toTypedArray())

    /**
     * Fetch a unique record that has <code>id = value</code>
     */
    fun fetchOneById(value: Long): io.heapy.kotbusta.jooq.tables.pojos.UserComments? = fetchOne(UserComments.USER_COMMENTS.ID, value)

    /**
     * Fetch records that have <code>user_id BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfUserId(lowerInclusive: Long, upperInclusive: Long): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetchRange(UserComments.USER_COMMENTS.USER_ID, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>user_id IN (values)</code>
     */
    fun fetchByUserId(vararg values: Long): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetch(UserComments.USER_COMMENTS.USER_ID, *values.toTypedArray())

    /**
     * Fetch records that have <code>book_id BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfBookId(lowerInclusive: Long, upperInclusive: Long): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetchRange(UserComments.USER_COMMENTS.BOOK_ID, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>book_id IN (values)</code>
     */
    fun fetchByBookId(vararg values: Long): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetch(UserComments.USER_COMMENTS.BOOK_ID, *values.toTypedArray())

    /**
     * Fetch records that have <code>comment BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfComment(lowerInclusive: String, upperInclusive: String): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetchRange(UserComments.USER_COMMENTS.COMMENT, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>comment IN (values)</code>
     */
    fun fetchByComment(vararg values: String): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetch(UserComments.USER_COMMENTS.COMMENT, *values)

    /**
     * Fetch records that have <code>created_at BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfCreatedAt(lowerInclusive: OffsetDateTime?, upperInclusive: OffsetDateTime?): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetchRange(UserComments.USER_COMMENTS.CREATED_AT, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>created_at IN (values)</code>
     */
    fun fetchByCreatedAt(vararg values: OffsetDateTime): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetch(UserComments.USER_COMMENTS.CREATED_AT, *values)

    /**
     * Fetch records that have <code>updated_at BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfUpdatedAt(lowerInclusive: OffsetDateTime?, upperInclusive: OffsetDateTime?): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetchRange(UserComments.USER_COMMENTS.UPDATED_AT, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>updated_at IN (values)</code>
     */
    fun fetchByUpdatedAt(vararg values: OffsetDateTime): List<io.heapy.kotbusta.jooq.tables.pojos.UserComments> = fetch(UserComments.USER_COMMENTS.UPDATED_AT, *values)
}
