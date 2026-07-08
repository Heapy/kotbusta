package io.heapy.kotbusta.service

import io.heapy.kotbusta.model.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caches [ParsedBook]s so page/TOC/search requests for the same book share one parse
 * instead of each re-opening and re-rendering the archive. Bounded LRU, in-memory, keyed
 * by book id only — content is identical for every user, so this also dedupes work when
 * multiple readers open the same book concurrently, which per-user keying wouldn't.
 *
 * Parses run on a dedicated [scope] rather than the calling request's own coroutine: if a
 * client disconnects mid-parse, that must not cancel some *other* request that's awaiting
 * the same in-flight parse.
 */
class BookContentCacheService(
    private val parser: BookContentParser,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    // accessOrder=true: iteration order tracks least- to most-recently-accessed, so the
    // first key is always the LRU entry.
    private val entries = LinkedHashMap<Int, Deferred<ParsedBook>>(16, 0.75f, true)

    suspend fun get(book: Book): ParsedBook {
        val deferred = lock.withLock {
            entries[book.id]?.let { return@withLock it }
            val started = scope.async { parser.render(book) }
            entries[book.id] = started
            if (entries.size > maxEntries) {
                entries.keys.firstOrNull()?.let(entries::remove)
            }
            started
        }
        return try {
            deferred.await()
        } catch (e: Throwable) {
            // Don't cache a failed parse. Only remove this exact attempt: a slower
            // failing call must not clobber a newer, already-succeeded one.
            lock.withLock {
                if (entries[book.id] === deferred) entries.remove(book.id)
            }
            throw e
        }
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 24
    }
}
