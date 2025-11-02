package io.heapy.kotbusta.model

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.badRequestError
import io.heapy.kotbusta.model.State.ParsedBook
import io.heapy.kotbusta.model.State.Sequences
import io.heapy.kotbusta.model.State.User
import io.heapy.kotbusta.model.State.UserId
import io.heapy.kotbusta.run
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Instant

@Serializable
enum class KindleFormat {
    EPUB,
    MOBI,
}

@Serializable
enum class KindleSendStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}

interface Database {
    suspend fun <T> run(operation: DatabaseOperation<T>): OperationResult<T>
}

sealed interface OperationResult<out T>
data class SuccessResult<T>(
    val state: ApplicationState,
    val result: T,
) : OperationResult<T>

data class ErrorResult(
    val message: String,
) : OperationResult<Nothing>

context(applicationModule: ApplicationModule)
suspend fun getUsers(): Map<UserId, User> =
    applicationModule
        .run(Read)
        .requireSuccess
        .state
        .users

val <T> OperationResult<T>.requireSuccess: SuccessResult<T>
    get() = when (this) {
        is SuccessResult -> this
        is ErrorResult -> badRequestError("Database error: $message")
    }

interface DatabaseOperation<T> {
    fun process(state: ApplicationState): OperationResult<T>
}

class Load(
    private val newState: ApplicationState,
) : DatabaseOperation<Unit> {
    override fun process(state: ApplicationState): OperationResult<Unit> {
        return if (state == EmptyState) {
            SuccessResult(newState, Unit)
        } else {
            ErrorResult("Database already loaded")
        }
    }
}

class JsonDatabase : Database {
    private val state = AtomicReference<ApplicationState>(EmptyState)
    private val mutex = Mutex()
    override suspend fun <T> run(operation: DatabaseOperation<T>): OperationResult<T> {
        if (operation is Read) {
            return operation.process(state.load())
        }

        return mutex.withLock {
            when (val result = operation.process(state.load())) {
                is SuccessResult -> {
                    state.store(result.state)
                    result
                }

                is ErrorResult -> result
            }
        }
    }
}

interface ApplicationState {
    val sequences: Sequences
    val users: Map<UserId, User>
    val books: Map<BookId, ParsedBook>
    val titles: Map<String, BookId>
    val authors: Map<String, List<BookId>>
    val comments: Map<BookId, List<State.UserComment>>
    val notes: Map<BookId, List<State.UserNote>>
    val sendEvents: Map<SendEventId, State.KindleSendEvent>
}

private object EmptyState : ApplicationState {
    override val users: Map<UserId, User> = emptyMap()
    override val books: Map<BookId, ParsedBook> = emptyMap()
    override val titles: Map<String, BookId> = emptyMap()
    override val authors: Map<String, List<BookId>> = emptyMap()
    override val comments: Map<BookId, List<State.UserComment>> = emptyMap()
    override val notes: Map<BookId, List<State.UserNote>> = emptyMap()
    override val sendEvents: Map<SendEventId, State.KindleSendEvent> = emptyMap()
    override val sequences: Sequences = Sequences(-1, -1, -1, -1)
}

fun ApplicationState.copy(
    users: Map<UserId, User>? = null,
    sequences: Sequences? = null,
    comments: Map<BookId, List<State.UserComment>>? = null,
    notes: Map<BookId, List<State.UserNote>>? = null,
    sendEvents: Map<SendEventId, State.KindleSendEvent>? = null,
): ApplicationState {
    val newUsers = users ?: this.users
    val newSequences = sequences ?: this.sequences
    val newComments = comments ?: this.comments
    val newNotes = notes ?: this.notes
    val newSendEvents = sendEvents ?: this.sendEvents
    return DefaultApplicationState(
        users = newUsers,
        books = books,
        titles = titles,
        authors = authors,
        sequences = newSequences,
        comments = newComments,
        notes = newNotes,
        sendEvents = newSendEvents,
    )
}

private class DefaultApplicationState(
    override val users: Map<UserId, User>,
    override val books: Map<BookId, ParsedBook>,
    override val titles: Map<String, BookId>,
    override val authors: Map<String, List<BookId>>,
    override val sequences: Sequences,
    override val comments: Map<BookId, List<State.UserComment>>,
    override val notes: Map<BookId, List<State.UserNote>>,
    override val sendEvents: Map<SendEventId, State.KindleSendEvent>,
) : ApplicationState

object State {
    @Serializable
    data class ParsedBook(
        val bookId: BookId,
        val title: String,
        val authors: List<String>,
        val series: String?,
        val seriesNumber: Int?,
        val genres: List<String>,
        val language: String,
        val fileFormat: String,
        val filePath: String,
        val archivePath: String,
        val fileSize: Int?,
    )

    @Serializable
    data class Sequences(
        val userId: Int,
        val kindleId: Int,
        val commentId: Int,
        val sendEventId: Int,
    )

    @Serializable
    @JvmInline
    value class UserId(
        val value: Int,
    )

    @Serializable
    @JvmInline
    value class KindleId(
        val id: Int,
    )

    @Serializable
    data class User(
        val id: UserId,
        val email: String,
        val name: String,
        val googleId: String,
        val avatarUrl: String?,
        val idAdmin: Boolean,
        val status: UserStatus,
        val kindleDevices: List<KindleDevice>,
        val downloads: List<Download>,
        val stars: List<Star>,
    )

    @Serializable
    data class KindleDevice(
        val id: KindleId,
        val name: String,
        val email: String,
    )

    @Serializable
    data class Download(
        val bookId: BookId,
        val eventTime: Instant,
    )

    @Serializable
    data class Star(
        val bookId: BookId,
        val eventTime: Instant,
    )

    @Serializable
    data class UserComment(
        val id: CommentId,
        val userId: UserId,
        val bookId: BookId,
        val comment: String,
        val createdAt: Instant,
        val updatedAt: Instant,
    )

    @Serializable
    data class UserNote(
        val userId: UserId,
        val bookId: BookId,
        val note: String,
        val createdAt: Instant,
        val updatedAt: Instant,
    )

    @Serializable
    data class KindleSendEvent(
        val id: SendEventId,
        val userId: UserId,
        val deviceId: KindleId,
        val bookId: BookId,
        val format: KindleFormat,
        val status: KindleSendStatus,
        val lastError: String?,
        val createdAt: Instant,
        val updatedAt: Instant,
    )
}

@Serializable
@JvmInline
value class CommentId(
    val id: Int,
)

@Serializable
@JvmInline
value class SendEventId(
    val id: Int,
)

@Serializable
@JvmInline
value class BookId(
    val id: Int,
)

data class Books(
    val books: Map<BookId, ParsedBook>,
    val titles: Map<String, BookId>,
    val authors: Map<String, List<BookId>>,
)
