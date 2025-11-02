package io.heapy.kotbusta.model

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.badRequestError
import io.heapy.kotbusta.model.State.KindleSendEvent
import io.heapy.kotbusta.model.State.ParsedBook
import io.heapy.kotbusta.model.State.SendEventId
import io.heapy.kotbusta.model.State.Sequences
import io.heapy.kotbusta.model.State.User
import io.heapy.kotbusta.model.State.UserComment
import io.heapy.kotbusta.model.State.UserId
import io.heapy.kotbusta.model.State.UserNote
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
        return if (state == NullState) {
            SuccessResult(newState, Unit)
        } else {
            ErrorResult("Database already loaded")
        }
    }
}

class LoadBooks(
    private val books: Map<Int, ParsedBook>,
) : DatabaseOperation<Unit> {
    override fun process(state: ApplicationState): OperationResult<Unit> {
        return SuccessResult(
            state.copy(
                books = books,
            ),
            Unit,
        )
    }
}

class JsonDatabase : Database {
    private val state = AtomicReference<ApplicationState>(NullState)
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
    val books: Map<Int, ParsedBook>
    val comments: Map<Int, List<UserComment>>
    val notes: Map<Int, List<UserNote>>
    val sendEvents: Map<SendEventId, KindleSendEvent>
}

private object NullState : ApplicationState {
    override val users: Map<UserId, User> = emptyMap()
    override val books: Map<Int, ParsedBook> = emptyMap()
    override val comments: Map<Int, List<UserComment>> = emptyMap()
    override val notes: Map<Int, List<UserNote>> = emptyMap()
    override val sendEvents: Map<SendEventId, KindleSendEvent> = emptyMap()
    override val sequences: Sequences = Sequences(-1, -1, -1, -1)
}

fun ApplicationState.copy(
    users: Map<UserId, User>? = null,
    sequences: Sequences? = null,
    comments: Map<Int, List<UserComment>>? = null,
    notes: Map<Int, List<UserNote>>? = null,
    sendEvents: Map<SendEventId, KindleSendEvent>? = null,
    books: Map<Int, ParsedBook>? = null,
): ApplicationState {
    val newUsers = users ?: this.users
    val newSequences = sequences ?: this.sequences
    val newComments = comments ?: this.comments
    val newNotes = notes ?: this.notes
    val newSendEvents = sendEvents ?: this.sendEvents
    val newBooks = books ?: this.books
    return DefaultApplicationState(
        users = newUsers,
        books = newBooks,
        sequences = newSequences,
        comments = newComments,
        notes = newNotes,
        sendEvents = newSendEvents,
    )
}

private class DefaultApplicationState(
    override val users: Map<UserId, User>,
    override val books: Map<Int, ParsedBook>,
    override val sequences: Sequences,
    override val comments: Map<Int, List<State.UserComment>>,
    override val notes: Map<Int, List<State.UserNote>>,
    override val sendEvents: Map<SendEventId, KindleSendEvent>,
) : ApplicationState

object State {
    @Serializable
    data class ParsedBook(
        val bookId: Int,
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
        val value: Int,
    )

    @Serializable
    data class User(
        val id: UserId,
        val email: String,
        val name: String,
        val googleId: String,
        val avatarUrl: String?,
        val status: UserStatus,
        val kindleDevices: List<KindleDevice>,
        val downloads: List<Download>,
        val stars: List<Star>,
        val isAdmin: Boolean,
    )

    @Serializable
    data class KindleDevice(
        val id: KindleId,
        val name: String,
        val email: String,
    )

    @Serializable
    data class Download(
        val bookId: Int,
        val format: String,
        val eventTime: Instant,
    )

    @Serializable
    data class Star(
        val bookId: Int,
        val eventTime: Instant,
    )


    @Serializable
    @JvmInline
    value class CommentId(
        val value: Int,
    )

    @Serializable
    data class UserComment(
        val id: CommentId,
        val userId: UserId,
        val bookId: Int,
        val comment: String,
        val createdAt: Instant,
        val updatedAt: Instant,
    )

    @Serializable
    data class UserNote(
        val userId: UserId,
        val bookId: Int,
        val note: String,
        val createdAt: Instant,
        val updatedAt: Instant,
    )

    @Serializable
    @JvmInline
    value class SendEventId(
        val value: Int,
    )

    @Serializable
    data class KindleSendEvent(
        val id: SendEventId,
        val userId: UserId,
        val deviceId: KindleId,
        val bookId: Int,
        val format: KindleFormat,
        val status: KindleSendStatus,
        val lastError: String?,
        val createdAt: Instant,
        val updatedAt: Instant,
    )
}
