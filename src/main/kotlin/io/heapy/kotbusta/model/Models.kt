package io.heapy.kotbusta.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class User(
    val id: Int,
    val googleId: String,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val status: UserStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class Author(
    val id: Int,
    val fullName: String,
)

@Serializable
data class Series(
    val id: Int,
    val name: String,
)

@Serializable
data class Book(
    val id: Int,
    val title: String,
    val annotation: String?,
    val genres: List<String>,
    val language: String,
    val authors: List<Author>,
    val series: Series?,
    val seriesNumber: Int?,
    val filePath: String,
    val archivePath: String,
    val fileSize: Int?,
    val dateAdded: Instant,
    val coverImageUrl: String?,
)

@Serializable
sealed interface FbNode

@Serializable
@SerialName("el")
data class FbElement(
    val tag: String,
    val className: String? = null,
    val id: String? = null,
    val href: String? = null,
    val src: String? = null,
    val children: List<FbNode> = emptyList(),
) : FbNode

@Serializable
@SerialName("text")
data class FbText(
    val value: String,
) : FbNode

@Serializable
data class BookContent(
    val id: Int,
    val title: String,
    val page: Int,
    val totalPages: Int,
    val hasMore: Boolean,
    val nodes: List<FbNode>,
    val hasImages: Boolean = false,
)

@Serializable
data class TocEntry(
    val title: String,
    val level: Int,
    val page: Int,
)

@Serializable
data class BookToc(
    val id: Int,
    val totalPages: Int,
    val entries: List<TocEntry>,
    val anchors: Map<String, Int>,
)

@Serializable
data class PageMatchCount(
    val page: Int,
    val count: Int,
)

@Serializable
data class BookSearchResult(
    val id: Int,
    val query: String,
    val totalMatches: Int,
    val pages: List<PageMatchCount>,
)

@Serializable
data class BookSummary(
    val id: Int,
    val title: String,
    val authors: List<String>,
    val genres: List<String>,
    val language: String,
    val series: String?,
    val seriesNumber: Int?,
    val coverImageUrl: String?,
    val rating: Double? = null,
    val ratingSource: String? = null,
)

@Serializable
data class SearchQuery(
    val query: String,
    val genre: String? = null,
    val language: String? = null,
    val author: String? = null,
    val limit: Int = 20,
    val offset: Int = 0,
)

@Serializable
data class SearchResult(
    val books: List<BookSummary>,
    val total: Long,
    val hasMore: Boolean,
)

@Serializable
sealed interface ApiResponse {
    @Serializable
    data class Success<T>(
        val data: T,
    ) : ApiResponse {
        val success = true
    }

    @Serializable
    data class Error(val message: String) : ApiResponse {
        val success = false
    }
}

@Serializable
data class UserInfo(
    val userId: Int,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val status: UserStatus,
)

@Serializable
data class PendingUsersResponse(
    val users: List<User>,
    val total: Long,
    val hasMore: Boolean,
)

@Serializable
enum class UserStatus {
    PENDING,
    APPROVED,
    REJECTED,
    DEACTIVATED;
}

@Serializable
enum class KindleFormat {
    // Only EPUB: modern "Send to Kindle" accepts EPUB directly.
    EPUB,
}

@Serializable
enum class KindleSendStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;
}

@Serializable
data class KindleDevice(
    val id: Int,
    val userId: Int,
    val email: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class CreateDeviceRequest(
    val email: String,
    val name: String,
)

@Serializable
data class UpdateDeviceRequest(
    val name: String,
)

@Serializable
data class SendToKindleRequest(
    val deviceId: Int,
    val format: KindleFormat = KindleFormat.EPUB,
)

@Serializable
data class DeviceResponse(
    val id: Int,
    val email: String,
    val name: String,
    val createdAt: Instant,
)

@Serializable
data class SendHistoryResponse(
    val id: Int,
    val deviceName: String,
    val bookTitle: String,
    val format: KindleFormat,
    val status: KindleSendStatus,
    val createdAt: Instant,
    val lastError: String? = null,
)

@Serializable
data class SendHistoryResult(
    val items: List<SendHistoryResponse>,
    val total: Long,
    val hasMore: Boolean,
)

@Serializable
data class EnqueueResponse(
    val queueId: Int,
)
