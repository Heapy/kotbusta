/*
 * This file is generated by jOOQ.
 */
package io.heapy.kotbusta.jooq.tables.pojos


import io.heapy.kotbusta.jooq.tables.interfaces.IUserStars

import java.time.OffsetDateTime


/**
 * This class is generated by jOOQ.
 */
@Suppress("warnings")
data class UserStars(
    override val userId: Long,
    override val bookId: Long,
    override val createdAt: OffsetDateTime? = null
): IUserStars {

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true
        if (other == null)
            return false
        if (this::class != other::class)
            return false
        val o: UserStars = other as UserStars
        if (this.userId != o.userId)
            return false
        if (this.bookId != o.bookId)
            return false
        if (this.createdAt == null) {
            if (o.createdAt != null)
                return false
        }
        else if (this.createdAt != o.createdAt)
            return false
        return true
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + this.userId.hashCode()
        result = prime * result + this.bookId.hashCode()
        result = prime * result + (if (this.createdAt == null) 0 else this.createdAt.hashCode())
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder("UserStars (")

        sb.append(userId)
        sb.append(", ").append(bookId)
        sb.append(", ").append(createdAt)

        sb.append(")")
        return sb.toString()
    }
}
