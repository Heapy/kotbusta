package io.heapy.kotbusta.repository

import io.heapy.kotbusta.dao.book.*
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.Book
import io.heapy.kotbusta.model.BookSummary
import io.heapy.kotbusta.model.SearchQuery
import io.heapy.kotbusta.model.SearchResult

class BookRepository(
    private val getBooksQuery: GetBooksQuery,
    private val searchBooksQuery: SearchBooksQuery,
    private val getBookByIdQuery: GetBookByIdQuery,
    private val getSimilarBooksQuery: GetSimilarBooksQuery,
    private val getBookCoverQuery: GetBookCoverQuery,
    private val starBookQuery: StarBookQuery,
    private val unstarBookQuery: UnstarBookQuery,
    private val getStarredBooksQuery: GetStarredBooksQuery,
) {
    context(_: TransactionContext)
    fun getBooks(limit: Int, offset: Int, userId: Long?) =
        getBooksQuery.getBooks(limit, offset, userId)

    context(_: TransactionContext, _: UserSession)
    fun searchBooks(query: SearchQuery): SearchResult =
        searchBooksQuery.searchBooks(query)

    context(_: TransactionContext, _: UserSession)
    fun getBookById(bookId: Long): Book? =
        getBookByIdQuery.getBookById(bookId)

    context(_: TransactionContext, _: UserSession)
    fun getSimilarBooks(bookId: Long, genre: String?, limit: Int): List<BookSummary> =
        getSimilarBooksQuery.getSimilarBooks(bookId, genre, limit)

    context(_: TransactionContext)
    fun getBookCover(bookId: Long): ByteArray? =
        getBookCoverQuery.getBookCover(bookId)

    context(_: TransactionContext, _: UserSession)
    fun starBook(bookId: Long): Boolean =
        starBookQuery.starBook(bookId)

    context(_: TransactionContext, _: UserSession)
    fun unstarBook(bookId: Long): Boolean =
        unstarBookQuery.unstarBook(bookId)

    context(_: TransactionContext, _: UserSession)
    fun getStarredBooks(limit: Int, offset: Int): SearchResult =
        getStarredBooksQuery.getStarredBooks(limit, offset)
}
