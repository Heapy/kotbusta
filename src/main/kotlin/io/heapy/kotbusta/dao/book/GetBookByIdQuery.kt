package io.heapy.kotbusta.dao.book

import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.*
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.Author
import io.heapy.kotbusta.model.Book
import io.heapy.kotbusta.model.Series
import org.jooq.impl.DSL

class GetBookByIdQuery {
    context(_: TransactionContext, userSession: UserSession)
    fun getBookById(bookId: Long): Book? = useTx { dslContext ->
        val record = dslContext
            .select(
                BOOKS.ID,
                BOOKS.TITLE,
                BOOKS.ANNOTATION,
                BOOKS.GENRE,
                BOOKS.LANGUAGE,
                BOOKS.FILE_PATH,
                BOOKS.ARCHIVE_PATH,
                BOOKS.FILE_SIZE,
                BOOKS.DATE_ADDED,
                BOOKS.SERIES_NUMBER,
                BOOKS.SERIES_ID,
                SERIES.NAME.`as`("series_name"),
                DSL.case_()
                    .`when`(USER_STARS.BOOK_ID.isNotNull, DSL.inline(true))
                    .otherwise(DSL.inline(false))
                    .`as`("is_starred"),
                USER_NOTES.NOTE.`as`("user_note"),
            )
            .from(BOOKS)
            .leftJoin(SERIES).on(BOOKS.SERIES_ID.eq(SERIES.ID))
            .leftJoin(USER_STARS)
            .on(BOOKS.ID.eq(USER_STARS.BOOK_ID).and(USER_STARS.USER_ID.eq(userSession.userId)))
            .leftJoin(USER_NOTES)
            .on(BOOKS.ID.eq(USER_NOTES.BOOK_ID).and(USER_NOTES.USER_ID.eq(userSession.userId)))
            .where(BOOKS.ID.eq(bookId))
            .fetchOne() ?: return@useTx null

        val authors = getBookAuthors(bookId)
        val series = record.get("series_name", String::class.java)?.let {
            Series(record.get(BOOKS.SERIES_ID)!!, it)
        }

        Book(
            id = record.get(BOOKS.ID)!!,
            title = record.get(BOOKS.TITLE)!!,
            annotation = record.get(BOOKS.ANNOTATION),
            genre = record.get(BOOKS.GENRE),
            language = record.get(BOOKS.LANGUAGE)!!,
            authors = authors,
            series = series,
            seriesNumber = record.get(BOOKS.SERIES_NUMBER)?.takeIf { it != 0 },
            filePath = record.get(BOOKS.FILE_PATH)!!,
            archivePath = record.get(BOOKS.ARCHIVE_PATH)!!,
            fileSize = record.get(BOOKS.FILE_SIZE)?.takeIf { it != 0L },
            dateAdded = record.get(BOOKS.DATE_ADDED)!!.toEpochSecond(),
            coverImageUrl = "/api/books/${bookId}/cover",
            isStarred = record.get("is_starred", Boolean::class.java) ?: false,
            userNote = record.get("user_note", String::class.java)
        )
    }

    context(_: TransactionContext)
    private fun getBookAuthors(bookId: Long): List<Author> = useTx { dslContext ->
        dslContext
            .select(
                AUTHORS.ID,
                AUTHORS.FIRST_NAME,
                AUTHORS.LAST_NAME,
                AUTHORS.FULL_NAME
            )
            .from(AUTHORS)
            .innerJoin(BOOK_AUTHORS).on(AUTHORS.ID.eq(BOOK_AUTHORS.AUTHOR_ID))
            .where(BOOK_AUTHORS.BOOK_ID.eq(bookId))
            .fetch { record ->
                Author(
                    id = record.get(AUTHORS.ID)!!,
                    firstName = record.get(AUTHORS.FIRST_NAME),
                    lastName = record.get(AUTHORS.LAST_NAME)!!,
                    fullName = record.get(AUTHORS.FULL_NAME)!!
                )
            }
    }
}
