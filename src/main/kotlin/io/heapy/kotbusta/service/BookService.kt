package io.heapy.kotbusta.service

import io.heapy.kotbusta.database.QueryExecutor
import io.heapy.kotbusta.model.Author
import io.heapy.kotbusta.model.Book
import io.heapy.kotbusta.model.BookSummary
import io.heapy.kotbusta.model.SearchQuery
import io.heapy.kotbusta.model.SearchResult
import io.heapy.kotbusta.model.Series
import java.sql.Connection
import java.sql.ResultSet

class BookService(
    private val queryExecutor: QueryExecutor,
) {
    suspend fun getBooks(limit: Int = 20, offset: Int = 0, userId: Long? = null): SearchResult {
        return queryExecutor.execute(readOnly = true, name = "getBooks") { conn ->
            val books = getBooksList(conn, limit, offset, userId)
            val total = getTotalBooksCount(conn)

            SearchResult(
                books = books,
                total = total,
                hasMore = offset + limit < total
            )
        }
    }

    suspend fun searchBooks(query: SearchQuery, userId: Long? = null): SearchResult {
        return queryExecutor.execute(readOnly = true, name = "searchBooks") { conn ->
            val books = searchBooksList(conn, query, userId)
            val total = getSearchResultsCount(conn, query)

            SearchResult(
                books = books,
                total = total,
                hasMore = query.offset + query.limit < total
            )
        }
    }

    suspend fun getBookById(bookId: Long, userId: Long? = null): Book? {
        return queryExecutor.execute(readOnly = true, name = "getBookById") { conn ->
            getBookDetails(conn, bookId, userId)
        }
    }

    suspend fun getSimilarBooks(bookId: Long, limit: Int = 10, userId: Long? = null): List<BookSummary> {
        return queryExecutor.execute(readOnly = true, name = "getSimilarBooks") { conn ->
            // Get book details to find similar books
            val book = getBookDetails(conn, bookId, userId) ?: return@execute emptyList()

            // Find books with same genre or by same authors
            val sql = """
                SELECT DISTINCT b.id, b.title, b.genre, b.language, b.series_id, b.series_number,
                       s.name as series_name,
                       ${if (userId != null) "CASE WHEN us.book_id IS NOT NULL THEN 1 ELSE 0 END as is_starred" else "0 as is_starred"}
                FROM books b
                LEFT JOIN series s ON b.series_id = s.id
                LEFT JOIN book_authors ba ON b.id = ba.book_id
                LEFT JOIN authors a ON ba.author_id = a.id
                ${if (userId != null) "LEFT JOIN user_stars us ON b.id = us.book_id AND us.user_id = ?" else ""}
                WHERE b.id != ? AND (
                    b.genre = ? OR
                    a.id IN (
                        SELECT ba2.author_id FROM book_authors ba2 WHERE ba2.book_id = ?
                    )
                )
                ORDER BY
                    CASE WHEN b.genre = ? THEN 1 ELSE 0 END DESC,
                    b.id DESC
                LIMIT ?
            """

            conn.prepareStatement(sql).use { stmt ->
                var paramIndex = 1
                if (userId != null) {
                    stmt.setLong(paramIndex++, userId)
                }
                stmt.setLong(paramIndex++, bookId)
                stmt.setString(paramIndex++, book.genre ?: "")
                stmt.setLong(paramIndex++, bookId)
                stmt.setString(paramIndex++, book.genre ?: "")
                stmt.setInt(paramIndex, limit)

                val rs = stmt.executeQuery()
                buildBookSummaryList(rs)
            }
        }
    }

    suspend fun getBookCover(bookId: Long): ByteArray? {
        return queryExecutor.execute(readOnly = true, name = "getBookCover") { conn ->
            val sql = "SELECT cover_image FROM books WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, bookId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return@execute rs.getBytes("cover_image")
                }
            }

            null
        }
    }

    suspend fun starBook(userId: Long, bookId: Long): Boolean {
        return queryExecutor.execute(name = "starBook") { conn ->
            val sql = "INSERT OR IGNORE INTO user_stars (user_id, book_id) VALUES (?, ?)"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setLong(2, bookId)
                stmt.executeUpdate() > 0
            }
        }
    }

    suspend fun unstarBook(userId: Long, bookId: Long): Boolean {
        return queryExecutor.execute(name = "unstarBook") { conn ->
            val sql = "DELETE FROM user_stars WHERE user_id = ? AND book_id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setLong(2, bookId)

                stmt.executeUpdate() > 0
            }
        }
    }

    suspend fun getStarredBooks(userId: Long, limit: Int = 20, offset: Int = 0): SearchResult {
        return queryExecutor.execute(readOnly = true, name = "getStarredBooks") { conn ->
            val sql = """
                SELECT b.id, b.title, b.genre, b.language, b.series_id, b.series_number,
                       s.name as series_name,
                       1 as is_starred
                FROM books b
                LEFT JOIN series s ON b.series_id = s.id
                INNER JOIN user_stars us ON b.id = us.book_id
                WHERE us.user_id = ?
                ORDER BY us.created_at DESC
                LIMIT ? OFFSET ?
            """

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setInt(2, limit)
                stmt.setInt(3, offset)

                val rs = stmt.executeQuery()
                val books = buildBookSummaryList(rs)

                // Get total count
                val countSql = "SELECT COUNT(*) FROM user_stars WHERE user_id = ?"
                val total = conn.prepareStatement(countSql).use { countStmt ->
                    countStmt.setLong(1, userId)
                    val countRs = countStmt.executeQuery()
                    if (countRs.next()) countRs.getLong(1) else 0L
                }

                SearchResult(
                    books = books,
                    total = total,
                    hasMore = offset + limit < total
                )
            }
        }
    }

    private suspend fun getBooksList(conn: Connection, limit: Int, offset: Int, userId: Long?): List<BookSummary> {
        val sql = """
            SELECT b.id, b.title, b.genre, b.language, b.series_id, b.series_number,
                   s.name as series_name,
                   ${if (userId != null) "CASE WHEN us.book_id IS NOT NULL THEN 1 ELSE 0 END as is_starred" else "0 as is_starred"}
            FROM books b
            LEFT JOIN series s ON b.series_id = s.id
            ${if (userId != null) "LEFT JOIN user_stars us ON b.id = us.book_id AND us.user_id = ?" else ""}
            ORDER BY b.id DESC
            LIMIT ? OFFSET ?
        """

        conn.prepareStatement(sql).use { stmt ->
            var paramIndex = 1
            if (userId != null) {
                stmt.setLong(paramIndex++, userId)
            }
            stmt.setInt(paramIndex++, limit)
            stmt.setInt(paramIndex, offset)

            val rs = stmt.executeQuery()
            return buildBookSummaryList(rs)
        }
    }

    private suspend fun searchBooksList(conn: Connection, query: SearchQuery, userId: Long?): List<BookSummary> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (query.query.isNotBlank()) {
            conditions.add("(b.title LIKE ? OR a.full_name LIKE ?)")
            val searchTerm = "%${query.query}%"
            params.add(searchTerm)
            params.add(searchTerm)
        }

        if (!query.genre.isNullOrBlank()) {
            conditions.add("b.genre = ?")
            params.add(query.genre)
        }

        if (!query.language.isNullOrBlank()) {
            conditions.add("b.language = ?")
            params.add(query.language)
        }

        if (!query.author.isNullOrBlank()) {
            conditions.add("a.full_name LIKE ?")
            params.add("%${query.author}%")
        }

        val whereClause = if (conditions.isNotEmpty()) {
            "WHERE " + conditions.joinToString(" AND ")
        } else ""

        val sql = """
            SELECT DISTINCT b.id, b.title, b.genre, b.language, b.series_id, b.series_number,
                   s.name as series_name,
                   ${if (userId != null) "CASE WHEN us.book_id IS NOT NULL THEN 1 ELSE 0 END as is_starred" else "0 as is_starred"}
            FROM books b
            LEFT JOIN series s ON b.series_id = s.id
            LEFT JOIN book_authors ba ON b.id = ba.book_id
            LEFT JOIN authors a ON ba.author_id = a.id
            ${if (userId != null) "LEFT JOIN user_stars us ON b.id = us.book_id AND us.user_id = ?" else ""}
            $whereClause
            ORDER BY b.id DESC
            LIMIT ? OFFSET ?
        """

        conn.prepareStatement(sql).use { stmt ->
            var paramIndex = 1
            if (userId != null) {
                stmt.setLong(paramIndex++, userId)
            }
            params.forEach { param ->
                when (param) {
                    is String -> stmt.setString(paramIndex++, param)
                    is Long -> stmt.setLong(paramIndex++, param)
                    is Int -> stmt.setInt(paramIndex++, param)
                }
            }
            stmt.setInt(paramIndex++, query.limit)
            stmt.setInt(paramIndex, query.offset)

            val rs = stmt.executeQuery()
            return buildBookSummaryList(rs)
        }
    }

    private fun getBookDetails(conn: Connection, bookId: Long, userId: Long?): Book? {
        val sql = """
            SELECT b.id, b.title, b.annotation, b.genre, b.language, b.series_id, b.series_number,
                   b.file_path, b.archive_path, b.file_size, b.date_added,
                   s.name as series_name,
                   ${if (userId != null) "CASE WHEN us.book_id IS NOT NULL THEN 1 ELSE 0 END as is_starred," else "0 as is_starred,"}
                   ${if (userId != null) "un.note as user_note" else "NULL as user_note"}
            FROM books b
            LEFT JOIN series s ON b.series_id = s.id
            ${if (userId != null) "LEFT JOIN user_stars us ON b.id = us.book_id AND us.user_id = ?" else ""}
            ${if (userId != null) "LEFT JOIN user_notes un ON b.id = un.book_id AND un.user_id = ?" else ""}
            WHERE b.id = ?
        """

        conn.prepareStatement(sql).use { stmt ->
            var paramIndex = 1
            if (userId != null) {
                stmt.setLong(paramIndex++, userId)
                stmt.setLong(paramIndex++, userId)
            }
            stmt.setLong(paramIndex, bookId)

            val rs = stmt.executeQuery()
            if (rs.next()) {
                val authors = getBookAuthors(conn, bookId)
                val series = rs.getString("series_name")?.let {
                    Series(rs.getLong("series_id"), it)
                }

                return Book(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    annotation = rs.getString("annotation"),
                    genre = rs.getString("genre"),
                    language = rs.getString("language"),
                    authors = authors,
                    series = series,
                    seriesNumber = rs.getInt("series_number").takeIf { it != 0 },
                    filePath = rs.getString("file_path"),
                    archivePath = rs.getString("archive_path"),
                    fileSize = rs.getLong("file_size").takeIf { it != 0L },
                    dateAdded = rs.getLong("date_added"),
                    coverImageUrl = "/api/books/${bookId}/cover",
                    isStarred = rs.getBoolean("is_starred"),
                    userNote = rs.getString("user_note")
                )
            }
        }
        return null
    }

    private fun getBookAuthors(conn: Connection, bookId: Long): List<Author> {
        val sql = """
            SELECT a.id, a.first_name, a.last_name, a.full_name
            FROM authors a
            INNER JOIN book_authors ba ON a.id = ba.author_id
            WHERE ba.book_id = ?
        """

        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, bookId)
            val rs = stmt.executeQuery()
            val authors = mutableListOf<Author>()
            while (rs.next()) {
                authors.add(
                    Author(
                        id = rs.getLong("id"),
                        firstName = rs.getString("first_name"),
                        lastName = rs.getString("last_name"),
                        fullName = rs.getString("full_name")
                    )
                )
            }
            return authors
        }
    }

    private suspend fun buildBookSummaryList(rs: ResultSet): List<BookSummary> {
        val books = mutableListOf<BookSummary>()
        val bookAuthors = mutableMapOf<Long, MutableList<String>>()

        while (rs.next()) {
            val bookId = rs.getLong("id")
            books.add(
                BookSummary(
                    id = bookId,
                    title = rs.getString("title"),
                    authors = emptyList(), // Will be filled later
                    genre = rs.getString("genre"),
                    language = rs.getString("language"),
                    series = rs.getString("series_name"),
                    seriesNumber = rs.getInt("series_number").takeIf { it != 0 },
                    coverImageUrl = "/api/books/${bookId}/cover",
                    isStarred = rs.getBoolean("is_starred")
                )
            )
        }

        // Get authors for all books
        if (books.isNotEmpty()) {
            queryExecutor.execute(readOnly = true, name = "getBookAuthors") { conn ->
                books.forEach { book ->
                    val authors = getBookAuthors(conn, book.id)
                    bookAuthors[book.id] = authors.map { it.fullName }.toMutableList()
                }
            }
        }

        // Update books with authors
        return books.map { book ->
            book.copy(authors = bookAuthors[book.id] ?: emptyList())
        }
    }

    private fun getTotalBooksCount(conn: Connection): Long {
        val sql = "SELECT COUNT(*) FROM books"
        conn.prepareStatement(sql).use { stmt ->
            val rs = stmt.executeQuery()
            return if (rs.next()) rs.getLong(1) else 0L
        }
    }

    private fun getSearchResultsCount(conn: Connection, query: SearchQuery): Long {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (query.query.isNotBlank()) {
            conditions.add("(b.title LIKE ? OR a.full_name LIKE ?)")
            val searchTerm = "%${query.query}%"
            params.add(searchTerm)
            params.add(searchTerm)
        }

        if (!query.genre.isNullOrBlank()) {
            conditions.add("b.genre = ?")
            params.add(query.genre)
        }

        if (!query.language.isNullOrBlank()) {
            conditions.add("b.language = ?")
            params.add(query.language)
        }

        if (!query.author.isNullOrBlank()) {
            conditions.add("a.full_name LIKE ?")
            params.add("%${query.author}%")
        }

        val whereClause = if (conditions.isNotEmpty()) {
            "WHERE " + conditions.joinToString(" AND ")
        } else ""

        val sql = """
            SELECT COUNT(DISTINCT b.id)
            FROM books b
            LEFT JOIN book_authors ba ON b.id = ba.book_id
            LEFT JOIN authors a ON ba.author_id = a.id
            $whereClause
        """

        conn.prepareStatement(sql).use { stmt ->
            var paramIndex = 1
            params.forEach { param ->
                when (param) {
                    is String -> stmt.setString(paramIndex++, param)
                    is Long -> stmt.setLong(paramIndex++, param)
                    is Int -> stmt.setInt(paramIndex++, param)
                }
            }

            val rs = stmt.executeQuery()
            return if (rs.next()) rs.getLong(1) else 0L
        }
    }
}
