package io.heapy.kotbusta.model

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State.UserId
import io.heapy.kotbusta.run

// Query helpers for accessing data from ApplicationState

context(applicationModule: ApplicationModule)
suspend fun getBooks(): Map<BookId, State.ParsedBook> =
    applicationModule
        .run(Read)
        .requireSuccess
        .state
        .books

context(applicationModule: ApplicationModule)
suspend fun getBook(bookId: BookId): State.ParsedBook? =
    getBooks()[bookId]

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun isBookStarred(bookId: BookId): Boolean {
    val user = getUser(userSession.userId) ?: return false
    return user.stars.any { it.bookId == bookId }
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getUserNote(bookId: BookId): String? {
    val state = applicationModule.run(Read).requireSuccess.state
    return state.notes[bookId]?.find { it.userId == userSession.userId }?.note
}

context(applicationModule: ApplicationModule)
suspend fun getBookComments(bookId: BookId): List<State.UserComment> {
    val state = applicationModule.run(Read).requireSuccess.state
    return state.comments[bookId] ?: emptyList()
}

context(applicationModule: ApplicationModule)
suspend fun getAllComments(): Map<BookId, List<State.UserComment>> {
    val state = applicationModule.run(Read).requireSuccess.state
    return state.comments
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getStarredBookIds(): List<BookId> {
    val user = getUser(userSession.userId) ?: return emptyList()
    return user.stars.map { it.bookId }
}

context(applicationModule: ApplicationModule)
suspend fun getUser(userId: UserId): State.User? {
    val state = applicationModule.run(Read).requireSuccess.state
    return state.users[userId]
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getKindleDevices(): List<State.KindleDevice> {
    val user = getUser(userSession.userId) ?: return emptyList()
    return user.kindleDevices
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getKindleDevice(deviceId: State.KindleId): State.KindleDevice? {
    val user = getUser(userSession.userId) ?: return null
    return user.kindleDevices.find { it.id == deviceId }
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getKindleSendHistory(): List<State.KindleSendEvent> {
    val state = applicationModule.run(Read).requireSuccess.state
    return state.sendEvents.values
        .filter { it.userId == userSession.userId }
        .sortedByDescending { it.createdAt }
}

context(applicationModule: ApplicationModule)
suspend fun getAllDownloads(): List<Pair<UserId, State.Download>> {
    val state = applicationModule.run(Read).requireSuccess.state
    return state.users.values.flatMap { user ->
        user.downloads.map { download -> user.id to download }
    }
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getUserDownloads(): List<State.Download> {
    val user = getUser(userSession.userId) ?: return emptyList()
    return user.downloads
}

// Search and filter helpers

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun searchBooks(
    query: String? = null,
    genre: String? = null,
    author: String? = null,
    language: String? = null,
    limit: Int = 20,
    offset: Int = 0,
): Pair<List<State.ParsedBook>, Long> {
    val allBooks = getBooks().values.toList()

    var filtered = allBooks

    // Filter by query (title or author)
    if (!query.isNullOrBlank()) {
        val lowerQuery = query.lowercase()
        filtered = filtered.filter { book ->
            book.title.lowercase().contains(lowerQuery) ||
                book.authors.any { it.lowercase().contains(lowerQuery) }
        }
    }

    // Filter by genre
    if (!genre.isNullOrBlank()) {
        filtered = filtered.filter { book ->
            book.genres.contains(genre)
        }
    }

    // Filter by author
    if (!author.isNullOrBlank()) {
        val lowerAuthor = author.lowercase()
        filtered = filtered.filter { book ->
            book.authors.any { it.lowercase().contains(lowerAuthor) }
        }
    }

    // Filter by language
    if (!language.isNullOrBlank()) {
        filtered = filtered.filter { it.language == language }
    }

    val total = filtered.size.toLong()
    val paginated = filtered
        .drop(offset)
        .take(limit)

    return paginated to total
}

context(applicationModule: ApplicationModule)
suspend fun getSimilarBooks(
    bookId: BookId,
    limit: Int = 10,
): List<State.ParsedBook> {
    val book = getBook(bookId) ?: return emptyList()
    val allBooks = getBooks().values.toList()

    // Find books with overlapping genres or authors
    return allBooks
        .filter { it.bookId != bookId }
        .filter { otherBook ->
            otherBook.genres.any { it in book.genres } ||
                otherBook.authors.any { it in book.authors }
        }
        .take(limit)
}
