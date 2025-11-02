package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.model.Books
import io.heapy.kotbusta.parser.InpxParser
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicReference

class ImportJobService(
    private val inpxParser: InpxParser,
) {
    val data = AtomicReference(
        Books(
            books = emptyMap(),
            titles = emptyMap(),
            authors = emptyMap(),
        ),
    )

    fun importBooks() {
        runBlocking {
            val books = inpxParser.parseAndImport()
            val titles = books
                .asSequence()
                .associateBy(
                    { (_, book) -> book.title.lowercase() },
                    { (bookId, _) -> bookId },
                )
            val authors = books
                .asSequence()
                .flatMap { (bookId, book) ->
                    book.authors.map { author -> author to bookId }
                }
                .groupBy(
                    { (author, _) -> author.lowercase() },
                    { (_, bookId) -> bookId },
                )

            data.store(
                Books(
                    books = books,
                    titles = titles,
                    authors = authors,
                )
            )
        }
    }

    private companion object : Logger()
}
