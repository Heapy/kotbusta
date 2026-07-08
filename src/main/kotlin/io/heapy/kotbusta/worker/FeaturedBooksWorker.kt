package io.heapy.kotbusta.worker

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.dao.FeaturedBookInsert
import io.heapy.kotbusta.dao.LIVELIB_TOP_SOURCE
import io.heapy.kotbusta.dao.replaceFeaturedBooks
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.model.BookSummary
import io.heapy.kotbusta.model.SearchQuery
import io.heapy.kotbusta.service.BookSearchService
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import kotlin.time.Clock

/**
 * Periodically scrapes LiveLib's "Топ 100 — лучшие книги за все время" list —
 * an all-time top ranking, not a month-scoped one. LiveLib's actual
 * month-scoped top list is disallowed for crawling by its robots.txt, so we
 * deliberately don't touch it. An all-time list was also chosen over
 * LiveLib's "novelties"/"popular" feeds: those skew toward brand-new
 * releases, which are unlikely to already be present in a local Flibusta
 * catalog snapshot (in practice, they produced zero verified matches against
 * a several-months-old snapshot) — enduring classics are far more likely to
 * already be in any given snapshot regardless of its age.
 *
 * Scraped title/author pairs are matched against the local catalog via the
 * existing Lucene search index (candidate retrieval), then verified with a
 * normalized title/author comparison — the index has no exposed relevance
 * score, so verification is what keeps a bad candidate from being stored as
 * a "match" for the wrong book.
 */
class FeaturedBooksWorker(
    private val httpClient: HttpClient,
    private val bookSearchService: BookSearchService,
    private val transactionProvider: TransactionProvider,
    private val pageCount: Int = 1,
    private val maxFeatured: Int = 40,
) {
    private var job: Job? = null

    fun start(
        scope: CoroutineScope,
        intervalMillis: Long,
    ) {
        job = scope.launch {
            while (isActive) {
                try {
                    runOnce()
                } catch (e: Exception) {
                    log.error("Error in featured books worker", e)
                }
                delay(intervalMillis)
            }
        }
        log.info("Featured books worker started with interval ${intervalMillis}ms")
    }

    fun stop() {
        job?.cancel()
        log.info("Featured books worker stopped")
    }

    suspend fun runOnce() {
        val candidates = scrapeCandidates()
        if (candidates.isEmpty()) {
            log.warn("No candidates scraped from $LIVELIB_TOP_SOURCE; keeping previous featured snapshot")
            return
        }

        val matches = matchCandidates(candidates)
        if (matches.isEmpty()) {
            log.warn("No local matches among ${candidates.size} scraped candidates from $LIVELIB_TOP_SOURCE")
            return
        }

        val fetchedAt = Clock.System.now()
        transactionProvider.transaction(READ_WRITE) {
            replaceFeaturedBooks(
                source = LIVELIB_TOP_SOURCE,
                fetchedAt = fetchedAt,
                items = matches,
            )
        }
        log.info("Stored ${matches.size} featured book(s) from $LIVELIB_TOP_SOURCE")
    }

    private suspend fun scrapeCandidates(): List<ScrapedCandidate> {
        val seen = LinkedHashMap<Pair<String, String>, ScrapedCandidate>()

        for (page in 1..pageCount) {
            val url = if (page == 1) BASE_URL else "$BASE_URL/listview/biglist/~$page"
            val pageCandidates = try {
                val html = httpClient.get(url) {
                    header("User-Agent", USER_AGENT)
                }.bodyAsText()
                parsePage(html)
            } catch (e: Exception) {
                log.warn("Failed to fetch/parse $url", e)
                emptyList()
            }

            if (pageCandidates.isEmpty()) {
                // Ran out of pages, or the site's markup changed — stop early
                // rather than keep hitting the site for nothing.
                break
            }
            pageCandidates.forEach { candidate ->
                seen.putIfAbsent(candidate.title.lowercase() to candidate.author.lowercase(), candidate)
            }

            if (page < pageCount) {
                delay(REQUEST_DELAY_MS)
            }
        }

        return seen.values.sortedByDescending { it.rating }
    }

    private fun parsePage(html: String): List<ScrapedCandidate> {
        val doc = Jsoup.parse(html)
        // Scoped to the common `<li class="book-item__item">` row rather than
        // `.book-item__wrapper`: on this page's layout, title/author live in a
        // sibling `.book-item__inner` block, not inside `.book-item__wrapper`
        // (which here holds only the rating) — the `<li>` is the one container
        // both live under.
        return doc.select(".book-item__item").mapNotNull { item ->
            val title = item.selectFirst(".book-item__title")?.text()?.trim()
            val author = item.selectFirst(".book-item__author")?.text()?.trim()
            val rating = item.selectFirst(".book-item__rating")
                ?.text()
                ?.trim()
                ?.replace(',', '.')
                ?.toDoubleOrNull()

            if (title.isNullOrBlank() || author.isNullOrBlank() || rating == null) {
                null
            } else {
                ScrapedCandidate(title = title, author = author, rating = rating)
            }
        }
    }

    private suspend fun matchCandidates(
        candidates: List<ScrapedCandidate>,
    ): List<FeaturedBookInsert> {
        val matches = mutableListOf<Pair<ScrapedCandidate, Int>>()
        val usedBookIds = mutableSetOf<Int>()

        for (candidate in candidates.take(MAX_CANDIDATES_TO_MATCH)) {
            if (matches.size >= maxFeatured) break

            val hits = try {
                bookSearchService.search(
                    SearchQuery(
                        query = "${candidate.title} ${candidate.author}",
                        limit = LUCENE_CANDIDATE_LIMIT,
                    ),
                ).books
            } catch (e: Exception) {
                log.warn("Search failed while matching \"${candidate.title}\"", e)
                emptyList()
            }

            val verified = hits.firstOrNull { book ->
                book.id !in usedBookIds && verifyMatch(candidate, book)
            } ?: continue

            usedBookIds += verified.id
            matches += candidate to verified.id
        }

        return matches.mapIndexed { index, (candidate, bookId) ->
            FeaturedBookInsert(
                bookId = bookId,
                externalTitle = candidate.title,
                externalAuthor = candidate.author,
                rating = candidate.rating,
                sourceRank = index + 1,
            )
        }
    }

    /**
     * Deliberately conservative: a missed match just means one fewer featured
     * book, but a wrong match would show someone else's rating on this book.
     */
    private fun verifyMatch(candidate: ScrapedCandidate, book: BookSummary): Boolean {
        val normalizedCandidateTitle = normalize(candidate.title)
        val normalizedBookTitle = normalize(book.title)
        if (normalizedCandidateTitle.isEmpty() || normalizedBookTitle.isEmpty()) {
            return false
        }

        val titleMatches = normalizedCandidateTitle == normalizedBookTitle ||
            titlesOverlapEnough(normalizedCandidateTitle, normalizedBookTitle)
        if (!titleMatches) {
            return false
        }

        val normalizedCandidateAuthor = normalize(candidate.author)
        return book.authors.any { author ->
            val surname = normalize(author).substringAfterLast(' ')
            surname.isNotEmpty() && normalizedCandidateAuthor.contains(surname)
        }
    }

    private fun titlesOverlapEnough(a: String, b: String): Boolean {
        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        if (shorter.isEmpty()) {
            return false
        }
        return longer.contains(shorter) && shorter.length.toDouble() / longer.length >= MIN_TITLE_LENGTH_RATIO
    }

    private fun normalize(value: String): String =
        value
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private data class ScrapedCandidate(
        val title: String,
        val author: String,
        val rating: Double,
    )

    private companion object : Logger() {
        private const val BASE_URL = "https://www.livelib.ru/books/top"
        private const val USER_AGENT =
            "KotbustaFeaturedBooksBot/1.0 (self-hosted personal library; contact the instance admin)"
        private const val REQUEST_DELAY_MS = 1_500L
        private const val MAX_CANDIDATES_TO_MATCH = 100
        private const val LUCENE_CANDIDATE_LIMIT = 5
        private const val MIN_TITLE_LENGTH_RATIO = 0.8
    }
}
