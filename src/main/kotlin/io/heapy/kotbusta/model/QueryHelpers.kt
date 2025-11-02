package io.heapy.kotbusta.model

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State.UserId
import io.heapy.kotbusta.run

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

// Convert State.UserComment to API UserComment
context(applicationModule: ApplicationModule)
suspend fun toUserCommentAPI(comment: State.UserComment): UserComment {
    val user = getUser(comment.userId)
    val book = getBook(comment.bookId)

    return UserComment(
        id = comment.id.id,
        userId = comment.userId.value,
        userName = user?.name ?: "Unknown",
        userAvatarUrl = user?.avatarUrl,
        bookId = comment.bookId.id,
        bookTitle = book?.title ?: "Unknown",
        comment = comment.comment,
        createdAt = comment.createdAt,
        updatedAt = comment.updatedAt,
    )
}

// Convert State.UserNote to API UserNote
context(applicationModule: ApplicationModule)
suspend fun toUserNoteAPI(note: State.UserNote): UserNote {
    return UserNote(
        id = note.userId.value, // Using userId as id since notes don't have separate IDs
        bookId = note.bookId.id,
        note = note.note,
        createdAt = note.createdAt,
        updatedAt = note.updatedAt,
    )
}

context(applicationModule: ApplicationModule)
suspend fun getBookComments(
    bookId: BookId,
    limit: Int = 20,
    offset: Int = 0,
): List<UserComment> {
    val state = applicationModule.run(Read).requireSuccess.state
    val comments = state.comments[bookId] ?: emptyList()

    return comments
        .sortedByDescending { it.createdAt }
        .drop(offset)
        .take(limit)
        .map { toUserCommentAPI(it) }
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

// Validate user session - check if user exists
context(applicationModule: ApplicationModule)
suspend fun validateUserSession(userId: UserId): Boolean {
    return getUser(userId) != null
}

// Convert State.User to API UserInfo
fun State.User.toUserInfo(): UserInfo {
    return UserInfo(
        userId = id,
        email = email,
        name = name,
        isAdmin = isAdmin,
        avatarUrl = avatarUrl,
        status = status,
    )
}

// Get user info for current session
context(applicationModule: ApplicationModule, userSession: io.heapy.kotbusta.ktor.UserSession)
suspend fun getUserInfo(): UserInfo? {
    val user = getUser(userSession.userId) ?: return null
    return user.toUserInfo()
}

// Convert State.KindleDevice to API DeviceResponse
fun toDeviceResponse(device: State.KindleDevice): DeviceResponse {
    return DeviceResponse(
        id = device.id.id,
        email = device.email,
        name = device.name,
        createdAt = kotlin.time.Clock.System.now(), // TODO: Add createdAt to State.KindleDevice if needed
    )
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getKindleDevices(): List<DeviceResponse> {
    val user = getUser(userSession.userId) ?: return emptyList()
    return user.kindleDevices.map { toDeviceResponse(it) }
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getKindleDevice(deviceId: State.KindleId): State.KindleDevice? {
    val user = getUser(userSession.userId) ?: return null
    return user.kindleDevices.find { it.id == deviceId }
}

// Convert State.KindleSendEvent to API SendHistoryResponse
context(applicationModule: ApplicationModule)
suspend fun toSendHistoryResponse(event: State.KindleSendEvent): SendHistoryResponse {
    val device = getUser(event.userId)?.kindleDevices?.find { it.id == event.deviceId }
    val book = getBook(event.bookId)

    return SendHistoryResponse(
        id = event.id.id,
        deviceName = device?.name ?: "Unknown Device",
        bookTitle = book?.title ?: "Unknown Book",
        format = event.format,
        status = event.status,
        createdAt = event.createdAt,
        lastError = event.lastError,
    )
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getKindleSendHistory(
    limit: Int = 20,
    offset: Int = 0,
): SendHistoryResult {
    val state = applicationModule.run(Read).requireSuccess.state
    val userEvents = state.sendEvents.values
        .filter { it.userId == userSession.userId }
        .sortedByDescending { it.createdAt }

    val total = userEvents.size.toLong()
    val paginated = userEvents
        .drop(offset)
        .take(limit)
        .map { toSendHistoryResponse(it) }

    return SendHistoryResult(
        items = paginated,
        total = total,
        hasMore = offset + limit < total,
    )
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

// Convert State.Download to API Download
context(applicationModule: ApplicationModule)
suspend fun toDownloadAPI(userId: UserId, download: State.Download): Download {
    val user = getUser(userId)
    val book = getBook(download.bookId)

    return Download(
        id = 0, // Downloads don't have IDs in the new model
        userId = userId.value,
        userName = user?.name ?: "Unknown",
        bookId = download.bookId.id,
        bookTitle = book?.title ?: "Unknown",
        format = "epub", // TODO: Add format to State.Download if needed
        createdAt = download.eventTime,
    )
}

// Get recent activity (comments and downloads)
context(applicationModule: ApplicationModule)
suspend fun getRecentActivity(limit: Int = 20): RecentActivity {
    val state = applicationModule.run(Read).requireSuccess.state

    // Get all comments sorted by creation time
    val allComments = state.comments.values.flatten()
        .sortedByDescending { it.createdAt }
        .take(limit)
        .map { toUserCommentAPI(it) }

    // Get all downloads sorted by event time
    val allDownloads = state.users.values.flatMap { user ->
        user.downloads.map { download -> user.id to download }
    }
        .sortedByDescending { it.second.eventTime }
        .take(limit)
        .map { (userId, download) -> toDownloadAPI(userId, download) }

    return RecentActivity(
        comments = allComments,
        downloads = allDownloads,
    )
}

// Search and filter helpers

// Convert ParsedBook to full Book model with user-specific data
context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun toBook(parsedBook: State.ParsedBook): Book {
    val isStarred = isBookStarred(parsedBook.bookId)
    val userNote = getUserNote(parsedBook.bookId)

    // Convert author strings to Author objects (with dummy IDs since we don't have author IDs in ParsedBook)
    val authors = parsedBook.authors.mapIndexed { index, name ->
        Author(id = index, fullName = name)
    }

    // Convert series string to Series object if present
    val series = parsedBook.series?.let { seriesName ->
        Series(id = 0, name = seriesName) // Using 0 as dummy ID
    }

    return Book(
        id = parsedBook.bookId.id,
        title = parsedBook.title,
        annotation = null, // ParsedBook doesn't have annotation
        genres = parsedBook.genres,
        language = parsedBook.language,
        authors = authors,
        series = series,
        seriesNumber = parsedBook.seriesNumber,
        filePath = parsedBook.filePath,
        archivePath = parsedBook.archivePath,
        fileSize = parsedBook.fileSize,
        dateAdded = kotlin.time.Clock.System.now(), // TODO: Add dateAdded to ParsedBook if needed
        coverImageUrl = "/api/books/${parsedBook.bookId.id}/cover",
        isStarred = isStarred,
        userNote = userNote,
    )
}

// Convert ParsedBook to BookSummary with user-specific data
context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun toBookSummary(book: State.ParsedBook): BookSummary {
    val isStarred = isBookStarred(book.bookId)
    return BookSummary(
        id = book.bookId.id,
        title = book.title,
        authors = book.authors,
        genres = book.genres,
        language = book.language,
        series = book.series,
        seriesNumber = book.seriesNumber,
        coverImageUrl = "/api/books/${book.bookId.id}/cover",
        isStarred = isStarred,
    )
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getBooksPage(
    limit: Int = 20,
    offset: Int = 0,
): SearchResult {
    val allBooks = getBooks().values.sortedByDescending { it.bookId.id }
    val total = allBooks.size.toLong()

    val paginated = allBooks
        .drop(offset)
        .take(limit)
        .map { toBookSummary(it) }

    return SearchResult(
        books = paginated,
        total = total,
        hasMore = offset + limit < total,
    )
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun searchBooks(
    query: String? = null,
    genre: String? = null,
    author: String? = null,
    language: String? = null,
    limit: Int = 20,
    offset: Int = 0,
): SearchResult {
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
        .sortedByDescending { it.bookId.id }
        .drop(offset)
        .take(limit)
        .map { toBookSummary(it) }

    return SearchResult(
        books = paginated,
        total = total,
        hasMore = offset + limit < total,
    )
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getStarredBooks(
    limit: Int = 20,
    offset: Int = 0,
): SearchResult {
    val starredBookIds = getStarredBookIds()
    val allBooks = getBooks()

    val starredBooks = starredBookIds
        .mapNotNull { bookId -> allBooks[bookId] }
        .map { toBookSummary(it) }

    val total = starredBooks.size.toLong()
    val paginated = starredBooks
        .drop(offset)
        .take(limit)

    return SearchResult(
        books = paginated,
        total = total,
        hasMore = offset + limit < total,
    )
}

context(applicationModule: ApplicationModule, userSession: UserSession)
suspend fun getSimilarBooks(
    bookId: BookId,
    limit: Int = 10,
): List<BookSummary> {
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
        .map { toBookSummary(it) }
}
