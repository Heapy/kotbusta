package io.heapy.kotbusta.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val googleId: String,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val status: UserStatus,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class Author(
    val id: Long,
    val firstName: String?,
    val lastName: String,
    val fullName: String
)

@Serializable
data class Series(
    val id: Long,
    val name: String
)

@Serializable
data class Book(
    val id: Long,
    val title: String,
    val annotation: String?,
    val genre: String?,
    val language: String,
    val authors: List<Author>,
    val series: Series?,
    val seriesNumber: Int?,
    val filePath: String,
    val archivePath: String,
    val fileSize: Long?,
    val dateAdded: Long,
    val coverImageUrl: String?,
    val isStarred: Boolean = false,
    val userNote: String? = null
)

@Serializable
data class BookSummary(
    val id: Long,
    val title: String,
    val authors: List<String>,
    val genre: String?,
    val language: String,
    val series: String?,
    val seriesNumber: Int?,
    val coverImageUrl: String?,
    val isStarred: Boolean = false
)

@Serializable
data class UserComment(
    val id: Long,
    val userId: Long,
    val userName: String,
    val userAvatarUrl: String?,
    val bookId: Long,
    val bookTitle: String,
    val comment: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class UserNote(
    val id: Long,
    val bookId: Long,
    val note: String,
    val isPrivate: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class Download(
    val id: Long,
    val userId: Long,
    val userName: String,
    val bookId: Long,
    val bookTitle: String,
    val format: String,
    val createdAt: Long
)

@Serializable
data class SearchQuery(
    val query: String,
    val genre: String? = null,
    val language: String? = null,
    val author: String? = null,
    val limit: Int = 20,
    val offset: Int = 0
)

@Serializable
data class SearchResult(
    val books: List<BookSummary>,
    val total: Long,
    val hasMore: Boolean
)

@Serializable
sealed interface ApiResponse {
    @Serializable
    data class Success<T>(
        val data: T
    ) : ApiResponse {
        val success = true
    }

    @Serializable
    data class Error(val message: String) : ApiResponse {
        val success = false
    }
}

@Serializable
data class RecentActivity(
    val comments: List<UserComment>,
    val downloads: List<Download>
)

@Serializable
data class UserInfo(
    val userId: Long,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val status: UserStatus
)

@Serializable
data class PendingUsersResponse(
    val users: List<User>,
    val total: Long,
    val hasMore: Boolean
)

@Serializable
enum class UserStatus {
    PENDING,
    APPROVED,
    REJECTED,
    DEACTIVATED;
}
