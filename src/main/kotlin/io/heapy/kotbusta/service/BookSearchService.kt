package io.heapy.kotbusta.service

import io.heapy.kotbusta.model.SearchQuery
import io.heapy.kotbusta.model.SearchResult
import io.heapy.kotbusta.model.BookSummary
import kotlinx.coroutines.CoroutineScope

interface BookSearchService {
    fun initialize(scope: CoroutineScope)

    fun scheduleRebuild(scope: CoroutineScope)

    suspend fun rebuildNow()

    suspend fun search(query: SearchQuery): SearchResult

    suspend fun findSimilar(bookId: Int, limit: Int): List<BookSummary>

    fun state(): SearchIndexState = SearchIndexState.READY

    fun close() {}
}

enum class SearchIndexState {
    READY,
    BUILDING,
    FAILED,
}

class SearchIndexNotReadyException(
    message: String = "Search index is not ready yet. Please retry shortly.",
) : RuntimeException(message)
