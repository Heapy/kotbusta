/*
 * This file is generated by jOOQ.
 */
package io.heapy.kotbusta.jooq.tables.interfaces


import java.io.Serializable
import java.time.OffsetDateTime


/**
 * This class is generated by jOOQ.
 */
@Suppress("warnings")
interface IUserStars : Serializable {
    val userId: Long
    val bookId: Long
    val createdAt: OffsetDateTime?
}
