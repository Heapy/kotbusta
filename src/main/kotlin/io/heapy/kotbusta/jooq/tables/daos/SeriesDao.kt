/*
 * This file is generated by jOOQ.
 */
package io.heapy.kotbusta.jooq.tables.daos


import io.heapy.kotbusta.jooq.tables.Series
import io.heapy.kotbusta.jooq.tables.records.SeriesRecord

import java.time.OffsetDateTime

import kotlin.collections.List

import org.jooq.Configuration
import org.jooq.impl.DAOImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("warnings")
open class SeriesDao(configuration: Configuration?) : DAOImpl<SeriesRecord, io.heapy.kotbusta.jooq.tables.pojos.Series, Long>(Series.SERIES, io.heapy.kotbusta.jooq.tables.pojos.Series::class.java, configuration) {

    /**
     * Create a new SeriesDao without any configuration
     */
    constructor(): this(null)

    override fun getId(o: io.heapy.kotbusta.jooq.tables.pojos.Series): Long? = o.id

    /**
     * Fetch records that have <code>id BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfId(lowerInclusive: Long?, upperInclusive: Long?): List<io.heapy.kotbusta.jooq.tables.pojos.Series> = fetchRange(Series.SERIES.ID, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>id IN (values)</code>
     */
    fun fetchById(vararg values: Long): List<io.heapy.kotbusta.jooq.tables.pojos.Series> = fetch(Series.SERIES.ID, *values.toTypedArray())

    /**
     * Fetch a unique record that has <code>id = value</code>
     */
    fun fetchOneById(value: Long): io.heapy.kotbusta.jooq.tables.pojos.Series? = fetchOne(Series.SERIES.ID, value)

    /**
     * Fetch records that have <code>name BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfName(lowerInclusive: String, upperInclusive: String): List<io.heapy.kotbusta.jooq.tables.pojos.Series> = fetchRange(Series.SERIES.NAME, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>name IN (values)</code>
     */
    fun fetchByName(vararg values: String): List<io.heapy.kotbusta.jooq.tables.pojos.Series> = fetch(Series.SERIES.NAME, *values)

    /**
     * Fetch records that have <code>created_at BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    fun fetchRangeOfCreatedAt(lowerInclusive: OffsetDateTime?, upperInclusive: OffsetDateTime?): List<io.heapy.kotbusta.jooq.tables.pojos.Series> = fetchRange(Series.SERIES.CREATED_AT, lowerInclusive, upperInclusive)

    /**
     * Fetch records that have <code>created_at IN (values)</code>
     */
    fun fetchByCreatedAt(vararg values: OffsetDateTime): List<io.heapy.kotbusta.jooq.tables.pojos.Series> = fetch(Series.SERIES.CREATED_AT, *values)
}
