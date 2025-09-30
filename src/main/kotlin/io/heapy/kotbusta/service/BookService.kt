package io.heapy.kotbusta.service

import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.model.*
import io.heapy.kotbusta.repository.BookRepository

class BookService(
    private val bookRepository: BookRepository,
) {
    context(_: TransactionContext)
    fun getBooks(limit: Int = 20, offset: Int = 0, userId: Long? = null): SearchResult =
        bookRepository.getBooks(limit, offset, userId)

    context(_: TransactionContext, _: UserSession)
    fun searchBooks(query: SearchQuery): SearchResult =
        bookRepository.searchBooks(query)

    context(_: TransactionContext, _: UserSession)
    fun getBookById(bookId: Long): Book? =
        bookRepository.getBookById(bookId)

    context(_: TransactionContext, _: UserSession)
    fun getSimilarBooks(bookId: Long, limit: Int = 10): List<BookSummary> {
        val book = bookRepository.getBookById(bookId)
        return bookRepository.getSimilarBooks(bookId, book?.genre, limit)
    }

    context(_: TransactionContext)
    fun getBookCover(bookId: Long): ByteArray? =
        bookRepository.getBookCover(bookId)

    context(_: TransactionContext, _: UserSession)
    fun starBook(bookId: Long): Boolean =
        bookRepository.starBook(bookId)

    context(_: TransactionContext, _: UserSession)
    fun unstarBook(bookId: Long): Boolean =
        bookRepository.unstarBook(bookId)

    context(_: TransactionContext, _: UserSession)
    fun getStarredBooks(limit: Int = 20, offset: Int = 0): SearchResult =
        bookRepository.getStarredBooks(limit, offset)
}