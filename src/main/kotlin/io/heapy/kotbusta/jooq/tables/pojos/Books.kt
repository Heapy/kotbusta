/*
 * This file is generated by jOOQ.
 */
package io.heapy.kotbusta.jooq.tables.pojos


import io.heapy.kotbusta.jooq.tables.interfaces.IBooks

import java.time.OffsetDateTime
import java.util.Arrays


/**
 * This class is generated by jOOQ.
 */
@Suppress("warnings")
data class Books(
    override val id: Long,
    override val title: String,
    override val `annotation`: String? = null,
    override val genre: String? = null,
    override val language: String? = null,
    override val seriesId: Long? = null,
    override val seriesNumber: Int? = null,
    override val filePath: String,
    override val archivePath: String,
    override val fileSize: Long? = null,
    override val dateAdded: OffsetDateTime,
    override val coverImage: ByteArray? = null,
    override val createdAt: OffsetDateTime? = null
): IBooks {

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true
        if (other == null)
            return false
        if (this::class != other::class)
            return false
        val o: Books = other as Books
        if (this.id != o.id)
            return false
        if (this.title != o.title)
            return false
        if (this.`annotation` == null) {
            if (o.`annotation` != null)
                return false
        }
        else if (this.`annotation` != o.`annotation`)
            return false
        if (this.genre == null) {
            if (o.genre != null)
                return false
        }
        else if (this.genre != o.genre)
            return false
        if (this.language == null) {
            if (o.language != null)
                return false
        }
        else if (this.language != o.language)
            return false
        if (this.seriesId == null) {
            if (o.seriesId != null)
                return false
        }
        else if (this.seriesId != o.seriesId)
            return false
        if (this.seriesNumber == null) {
            if (o.seriesNumber != null)
                return false
        }
        else if (this.seriesNumber != o.seriesNumber)
            return false
        if (this.filePath != o.filePath)
            return false
        if (this.archivePath != o.archivePath)
            return false
        if (this.fileSize == null) {
            if (o.fileSize != null)
                return false
        }
        else if (this.fileSize != o.fileSize)
            return false
        if (this.dateAdded != o.dateAdded)
            return false
        if (this.coverImage == null) {
            if (o.coverImage != null)
                return false
        }
        else if (!Arrays.equals(this.coverImage, o.coverImage))
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
        result = prime * result + this.id.hashCode()
        result = prime * result + this.title.hashCode()
        result = prime * result + (if (this.`annotation` == null) 0 else this.`annotation`.hashCode())
        result = prime * result + (if (this.genre == null) 0 else this.genre.hashCode())
        result = prime * result + (if (this.language == null) 0 else this.language.hashCode())
        result = prime * result + (if (this.seriesId == null) 0 else this.seriesId.hashCode())
        result = prime * result + (if (this.seriesNumber == null) 0 else this.seriesNumber.hashCode())
        result = prime * result + this.filePath.hashCode()
        result = prime * result + this.archivePath.hashCode()
        result = prime * result + (if (this.fileSize == null) 0 else this.fileSize.hashCode())
        result = prime * result + this.dateAdded.hashCode()
        result = prime * result + (if (this.coverImage == null) 0 else Arrays.hashCode(this.coverImage))
        result = prime * result + (if (this.createdAt == null) 0 else this.createdAt.hashCode())
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder("Books (")

        sb.append(id)
        sb.append(", ").append(title)
        sb.append(", ").append(`annotation`)
        sb.append(", ").append(genre)
        sb.append(", ").append(language)
        sb.append(", ").append(seriesId)
        sb.append(", ").append(seriesNumber)
        sb.append(", ").append(filePath)
        sb.append(", ").append(archivePath)
        sb.append(", ").append(fileSize)
        sb.append(", ").append(dateAdded)
        sb.append(", ").append("[binary...]")
        sb.append(", ").append(createdAt)

        sb.append(")")
        return sb.toString()
    }
}
