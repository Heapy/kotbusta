package io.heapy.kotbusta.service

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.dao.getBookSummariesByIds
import io.heapy.kotbusta.dao.getSearchIndexBooksPage
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.model.SearchIndexBook
import io.heapy.kotbusta.model.SearchQuery
import io.heapy.kotbusta.model.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field.Store.NO
import org.apache.lucene.document.Field.Store.YES
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.FuzzyQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.io.Closeable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.time.Clock

class LuceneBookSearchService(
    private val transactionProvider: TransactionProvider,
    private val indexPath: Path,
) : BookSearchService {
    private val analyzer: Analyzer = StandardAnalyzer()
    private val rebuildMutex = Mutex()
    private val rebuildRequested = AtomicBoolean(false)
    private val state = AtomicReference(SearchIndexState.BUILDING)
    private val activeIndexLock = java.util.concurrent.locks.ReentrantReadWriteLock()

    @Volatile
    private var activeIndex: ActiveIndex? = null

    @Volatile
    private var closed = false

    override fun initialize(scope: CoroutineScope) {
        val existingIndex = loadIndexIfCurrent(indexPath)
        if (existingIndex != null) {
            installActiveIndex(existingIndex)
            state.set(SearchIndexState.READY)
            log.info("Loaded Lucene search index from $indexPath")
            scheduleRebuild(scope)
            return
        }

        state.set(SearchIndexState.BUILDING)
        scheduleRebuild(scope)
    }

    override fun scheduleRebuild(scope: CoroutineScope) {
        if (closed) {
            return
        }
        rebuildRequested.set(true)
        scope.launch {
            if (!rebuildMutex.tryLock()) {
                return@launch
            }
            try {
                if (!closed) {
                    drainRebuildQueue()
                }
            } finally {
                rebuildMutex.unlock()
            }
        }
    }

    override suspend fun rebuildNow() {
        if (closed) {
            return
        }
        rebuildRequested.set(true)
        rebuildMutex.withLock {
            if (!closed) {
                drainRebuildQueue()
            }
        }
    }

    override suspend fun search(query: SearchQuery, userId: Int): SearchResult {
        val sanitizedLimit = query.limit.coerceAtLeast(0)
        val sanitizedOffset = query.offset.coerceAtLeast(0)
        val normalizedQuery = query.copy(
            limit = sanitizedLimit,
            offset = sanitizedOffset,
        )

        val luceneQuery = buildLuceneQuery(normalizedQuery)
        val searchSnapshot = activeIndexLock.read {
            val searcher = activeIndex?.searcher
                ?: throw SearchIndexNotReadyException()

            val total = searcher.count(luceneQuery).toLong()
            if (total == 0L || sanitizedLimit == 0 || sanitizedOffset >= total) {
                return@read SearchSnapshot(
                    ids = emptyList(),
                    total = total,
                )
            }

            val requestedHits = sanitizedOffset + sanitizedLimit
            val topDocs = searcher.search(luceneQuery, requestedHits)
            val storedFields = searcher.storedFields()
            val ids = topDocs.scoreDocs
                .drop(sanitizedOffset)
                .take(sanitizedLimit)
                .map { scoreDoc ->
                    storedFields.document(scoreDoc.doc).get(FIELD_BOOK_ID).toInt()
                }

            SearchSnapshot(
                ids = ids,
                total = total,
            )
        }

        val books = if (searchSnapshot.ids.isEmpty()) {
            emptyList()
        } else {
            transactionProvider.transaction(READ_ONLY) {
                getBookSummariesByIds(
                    bookIds = searchSnapshot.ids,
                    userId = userId,
                )
            }
        }

        return SearchResult(
            books = books,
            total = searchSnapshot.total,
            hasMore = sanitizedOffset + sanitizedLimit < searchSnapshot.total,
        )
    }

    override fun close() {
        closed = true
        // Wait for any in-flight rebuild (which uses the analyzer via the index
        // writer) to finish before tearing down the shared analyzer and index, so
        // we never close the analyzer out from under a running rebuild.
        runBlocking {
            rebuildMutex.withLock {
                activeIndexLock.write {
                    activeIndex?.close()
                    activeIndex = null
                }
                analyzer.close()
            }
        }
    }

    fun state(): SearchIndexState = state.get()

    private suspend fun drainRebuildQueue() {
        do {
            rebuildRequested.set(false)
            rebuildIndexOnce()
        } while (rebuildRequested.getAndSet(false))
    }

    private suspend fun rebuildIndexOnce() {
        state.set(SearchIndexState.BUILDING)

        val tempIndexPath = createTempIndexPath()

        try {
            val indexedBooks = buildIndex(tempIndexPath)
            swapIndexFiles(tempIndexPath)

            val newIndex = loadIndexIfCurrent(indexPath)
                ?: throw IllegalStateException("Lucene index build completed without a readable commit")

            installActiveIndex(newIndex)
            state.set(SearchIndexState.READY)
            log.info("Lucene search index rebuilt with $indexedBooks books")
        } catch (e: Exception) {
            tempIndexPath.deleteRecursivelyIfExists()
            state.set(SearchIndexState.FAILED)
            log.error("Failed to rebuild Lucene search index", e)
            throw e
        }
    }

    /**
     * Builds the index by streaming the catalog in keyset-paginated pages so the
     * whole book set is never held in memory at once. Returns the number of books
     * indexed.
     */
    private suspend fun buildIndex(
        path: Path,
    ): Int = withContext(Dispatchers.IO) {
        Files.createDirectories(path.parent)
        path.deleteRecursivelyIfExists()
        Files.createDirectories(path)

        var total = 0

        FSDirectory.open(path).use { directory ->
            val config = IndexWriterConfig(analyzer).apply {
                openMode = IndexWriterConfig.OpenMode.CREATE
            }

            IndexWriter(directory, config).use { writer ->
                var afterId = 0
                while (true) {
                    val page = transactionProvider.transaction(READ_ONLY) {
                        getSearchIndexBooksPage(afterId, INDEX_PAGE_SIZE)
                    }
                    if (page.isEmpty()) {
                        break
                    }
                    page.forEach { book ->
                        writer.addDocument(book.toDocument())
                    }
                    afterId = page.last().bookId
                    total += page.size
                }
                writer.setLiveCommitData(
                    listOf(
                        java.util.AbstractMap.SimpleEntry(COMMIT_SCHEMA_VERSION_KEY, SCHEMA_VERSION),
                        java.util.AbstractMap.SimpleEntry(COMMIT_BUILT_AT_KEY, Clock.System.now().toString()),
                    ),
                )
                writer.commit()
            }
        }

        total
    }

    private fun swapIndexFiles(tempIndexPath: Path) {
        val backupPath = indexPath.resolveSibling("${indexPath.name}-backup")
        backupPath.deleteRecursivelyIfExists()

        val hadPrevious = Files.exists(indexPath)
        if (hadPrevious) {
            Files.move(indexPath, backupPath, StandardCopyOption.REPLACE_EXISTING)
        }

        try {
            Files.move(tempIndexPath, indexPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            if (hadPrevious && Files.exists(backupPath) && Files.notExists(indexPath)) {
                Files.move(backupPath, indexPath, StandardCopyOption.REPLACE_EXISTING)
            }
            throw e
        }

        backupPath.deleteRecursivelyIfExists()
    }

    private fun buildLuceneQuery(query: SearchQuery): Query {
        val root = BooleanQuery.Builder()
        var hasClause = false

        buildTextQuery(query.query)?.let {
            root.add(it, BooleanClause.Occur.MUST)
            hasClause = true
        }

        buildAuthorFilter(query.author)?.let {
            root.add(it, BooleanClause.Occur.MUST)
            hasClause = true
        }

        query.genre
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { genre ->
                root.add(
                    TermQuery(Term(FIELD_GENRE, normalizeKeyword(genre))),
                    BooleanClause.Occur.FILTER,
                )
                hasClause = true
            }

        query.language
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { language ->
                root.add(
                    TermQuery(Term(FIELD_LANGUAGE, normalizeKeyword(language))),
                    BooleanClause.Occur.FILTER,
                )
                hasClause = true
            }

        return if (hasClause) {
            root.build()
        } else {
            MatchAllDocsQuery.INSTANCE
        }
    }

    private fun buildTextQuery(input: String): Query? {
        val tokens = analyzeTokens(FIELD_TITLE, input)
        if (tokens.isEmpty()) {
            return null
        }

        return BooleanQuery.Builder().apply {
            tokens.forEach { token ->
                add(buildTokenQuery(token), BooleanClause.Occur.MUST)
            }
        }.build()
    }

    private fun buildAuthorFilter(input: String?): Query? {
        val author = input?.trim().orEmpty()
        if (author.isEmpty()) {
            return null
        }

        val tokens = analyzeTokens(FIELD_AUTHORS, author)
        if (tokens.isEmpty()) {
            return null
        }

        return BooleanQuery.Builder().apply {
            tokens.forEach { token ->
                add(buildFieldTokenQuery(FIELD_AUTHORS, token, AUTHOR_EXACT_BOOST, AUTHOR_PREFIX_BOOST, includeFuzzy = false), BooleanClause.Occur.MUST)
            }
        }.build()
    }

    private fun buildTokenQuery(token: String): Query {
        return BooleanQuery.Builder().apply {
            add(buildFieldTokenQuery(FIELD_TITLE, token, TITLE_EXACT_BOOST, TITLE_PREFIX_BOOST, includeFuzzy = true), BooleanClause.Occur.SHOULD)
            add(buildFieldTokenQuery(FIELD_AUTHORS, token, AUTHORS_EXACT_BOOST, AUTHORS_PREFIX_BOOST, includeFuzzy = true), BooleanClause.Occur.SHOULD)
            add(buildFieldTokenQuery(FIELD_SERIES, token, SERIES_EXACT_BOOST, SERIES_PREFIX_BOOST, includeFuzzy = true), BooleanClause.Occur.SHOULD)
            setMinimumNumberShouldMatch(1)
        }.build()
    }

    private fun buildFieldTokenQuery(
        field: String,
        token: String,
        exactBoost: Float,
        prefixBoost: Float,
        includeFuzzy: Boolean,
    ): Query {
        return BooleanQuery.Builder().apply {
            add(BoostQuery(TermQuery(Term(field, token)), exactBoost), BooleanClause.Occur.SHOULD)
            add(BoostQuery(PrefixQuery(Term(field, token)), prefixBoost), BooleanClause.Occur.SHOULD)

            if (includeFuzzy && token.length >= FUZZY_MIN_LENGTH) {
                add(
                    BoostQuery(
                        FuzzyQuery(Term(field, token), FUZZY_MAX_EDITS, FUZZY_PREFIX_LENGTH),
                        when (field) {
                            FIELD_TITLE -> TITLE_FUZZY_BOOST
                            FIELD_AUTHORS -> AUTHORS_FUZZY_BOOST
                            else -> SERIES_FUZZY_BOOST
                        },
                    ),
                    BooleanClause.Occur.SHOULD,
                )
            }

            setMinimumNumberShouldMatch(1)
        }.build()
    }

    private fun analyzeTokens(
        field: String,
        input: String,
    ): List<String> {
        if (input.isBlank()) {
            return emptyList()
        }

        val tokens = mutableListOf<String>()
        analyzer.tokenStream(field, input).use { tokenStream ->
            val termAttribute = tokenStream.addAttribute(CharTermAttribute::class.java)
            tokenStream.reset()
            while (tokenStream.incrementToken()) {
                tokens += termAttribute.toString()
            }
            tokenStream.end()
        }
        return tokens
    }

    private fun loadIndexIfCurrent(path: Path): ActiveIndex? {
        if (path.notExists()) {
            return null
        }

        val directory = try {
            FSDirectory.open(path)
        } catch (_: IOException) {
            return null
        }

        try {
            if (!DirectoryReader.indexExists(directory)) {
                directory.close()
                return null
            }

            val reader = DirectoryReader.open(directory)
            val schemaVersion = reader.indexCommit.userData[COMMIT_SCHEMA_VERSION_KEY]
            if (schemaVersion != SCHEMA_VERSION) {
                reader.close()
                directory.close()
                return null
            }

            return ActiveIndex(
                directory = directory,
                reader = reader,
                searcher = IndexSearcher(reader),
            )
        } catch (e: Exception) {
            log.warn("Ignoring unreadable Lucene index at $path: ${e.message}")
            directory.close()
            return null
        }
    }

    private fun installActiveIndex(index: ActiveIndex) {
        val previous = activeIndexLock.write {
            val old = activeIndex
            activeIndex = index
            old
        }
        if (previous != null) {
            try {
                previous.close()
            } catch (e: Exception) {
                log.warn("Failed to close previous Lucene index", e)
            }
        }
    }

    private fun createTempIndexPath(): Path {
        Files.createDirectories(indexPath.parent)
        return indexPath.resolveSibling("${indexPath.name}-build-${UUID.randomUUID()}")
    }

    private fun SearchIndexBook.toDocument(): Document {
        return Document().apply {
            add(StringField(FIELD_BOOK_ID, bookId.toString(), YES))
            add(TextField(FIELD_TITLE, title, NO))

            authors.forEach { author ->
                add(TextField(FIELD_AUTHORS, author, NO))
            }

            series?.takeIf(String::isNotBlank)?.let { seriesName ->
                add(TextField(FIELD_SERIES, seriesName, NO))
            }

            add(StringField(FIELD_LANGUAGE, normalizeKeyword(language), NO))
            genres.forEach { genre ->
                add(StringField(FIELD_GENRE, normalizeKeyword(genre), NO))
            }
        }
    }

    private fun normalizeKeyword(value: String): String = value.trim().lowercase(Locale.ROOT)

    private data class SearchSnapshot(
        val ids: List<Int>,
        val total: Long,
    )

    private data class ActiveIndex(
        val directory: Directory,
        val reader: DirectoryReader,
        val searcher: IndexSearcher,
    ) : Closeable {
        override fun close() {
            reader.close()
            directory.close()
        }
    }

    private companion object : Logger() {
        private const val SCHEMA_VERSION = "1"
        private const val COMMIT_SCHEMA_VERSION_KEY = "schema_version"
        private const val COMMIT_BUILT_AT_KEY = "built_at"

        private const val FIELD_BOOK_ID = "bookId"
        private const val FIELD_TITLE = "title"
        private const val FIELD_AUTHORS = "authors"
        private const val FIELD_SERIES = "series"
        private const val FIELD_LANGUAGE = "language"
        private const val FIELD_GENRE = "genre"

        private const val INDEX_PAGE_SIZE = 5000

        private const val FUZZY_MIN_LENGTH = 5
        private const val FUZZY_MAX_EDITS = 1
        private const val FUZZY_PREFIX_LENGTH = 2

        private const val TITLE_EXACT_BOOST = 8f
        private const val TITLE_PREFIX_BOOST = 4f
        private const val TITLE_FUZZY_BOOST = 2f
        private const val AUTHORS_EXACT_BOOST = 5f
        private const val AUTHORS_PREFIX_BOOST = 3f
        private const val AUTHORS_FUZZY_BOOST = 1.5f
        private const val SERIES_EXACT_BOOST = 3f
        private const val SERIES_PREFIX_BOOST = 2f
        private const val SERIES_FUZZY_BOOST = 1f
        private const val AUTHOR_EXACT_BOOST = 3f
        private const val AUTHOR_PREFIX_BOOST = 1.5f
    }
}

private fun Path.deleteRecursivelyIfExists() {
    if (Files.notExists(this)) {
        return
    }

    Files.walk(this).use { stream ->
        stream
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }
}
