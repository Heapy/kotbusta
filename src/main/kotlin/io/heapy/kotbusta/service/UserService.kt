package io.heapy.kotbusta.service

import io.heapy.kotbusta.model.Download
import io.heapy.kotbusta.model.RecentActivity
import io.heapy.kotbusta.model.UserComment
import io.heapy.kotbusta.model.UserNote

class UserService {
    suspend fun addComment(
        userId: Long,
        bookId: Long,
        comment: String,
    ): UserComment? {
        return queryExecutor.execute(name = "addComment") { conn ->
            val sql = """
                INSERT INTO user_comments (user_id, book_id, comment)
                VALUES (?, ?, ?)
            """

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setLong(2, bookId)
                stmt.setString(3, comment)

                if (stmt.executeUpdate() > 0) {
                    return@execute getLatestComment(userId, bookId)
                }
            }

            null
        }
    }

    suspend fun updateComment(
        userId: Long,
        commentId: Long,
        comment: String,
    ): Boolean {
        return queryExecutor.execute(name = "updateComment") { conn ->
            val sql = """
                UPDATE user_comments
                SET comment = ?, updated_at = strftime('%s', 'now')
                WHERE id = ? AND user_id = ?
            """

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, comment)
                stmt.setLong(2, commentId)
                stmt.setLong(3, userId)

                stmt.executeUpdate() > 0
            }
        }
    }

    suspend fun deleteComment(userId: Long, commentId: Long): Boolean {
        return queryExecutor.execute(name = "deleteComment") { conn ->
            val sql = "DELETE FROM user_comments WHERE id = ? AND user_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, commentId)
                stmt.setLong(2, userId)

                stmt.executeUpdate() > 0
            }
        }
    }

    suspend fun getBookComments(
        bookId: Long,
        limit: Int = 20,
        offset: Int = 0,
    ): List<UserComment> {
        return queryExecutor.execute(readOnly = true, name = "getBookComments") { conn ->
            val sql = """
                SELECT uc.id, uc.user_id, uc.book_id, uc.comment, uc.created_at, uc.updated_at,
                       u.name as user_name, u.avatar_url as user_avatar_url,
                       b.title as book_title
                FROM user_comments uc
                INNER JOIN users u ON uc.user_id = u.id
                INNER JOIN books b ON uc.book_id = b.id
                WHERE uc.book_id = ?
                ORDER BY uc.created_at DESC
                LIMIT ? OFFSET ?
            """

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, bookId)
                stmt.setInt(2, limit)
                stmt.setInt(3, offset)

                val rs = stmt.executeQuery()
                val comments = mutableListOf<UserComment>()

                while (rs.next()) {
                    comments.add(
                        UserComment(
                            id = rs.getLong("id"),
                            userId = rs.getLong("user_id"),
                            userName = rs.getString("user_name"),
                            userAvatarUrl = rs.getString("user_avatar_url"),
                            bookId = rs.getLong("book_id"),
                            bookTitle = rs.getString("book_title"),
                            comment = rs.getString("comment"),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at"),
                        ),
                    )
                }

                comments
            }
        }
    }

    suspend fun addOrUpdateNote(
        userId: Long,
        bookId: Long,
        note: String,
        isPrivate: Boolean = true,
    ): UserNote? {
        return queryExecutor.execute(name = "addOrUpdateNote") { conn ->
            // Check if note already exists
            val selectSql =
                "SELECT id FROM user_notes WHERE user_id = ? AND book_id = ?"
            conn.prepareStatement(selectSql).use { selectStmt ->
                selectStmt.setLong(1, userId)
                selectStmt.setLong(2, bookId)
                val rs = selectStmt.executeQuery()

                if (rs.next()) {
                    // Update existing note
                    val noteId = rs.getLong("id")
                    val updateSql = """
                        UPDATE user_notes
                        SET note = ?, is_private = ?, updated_at = strftime('%s', 'now')
                        WHERE id = ?
                    """

                    conn.prepareStatement(updateSql).use { updateStmt ->
                        updateStmt.setString(1, note)
                        updateStmt.setBoolean(2, isPrivate)
                        updateStmt.setLong(3, noteId)

                        if (updateStmt.executeUpdate() > 0) {
                            return@execute getUserNote(userId, bookId)
                        }
                    }
                } else {
                    // Insert new note
                    val insertSql = """
                        INSERT INTO user_notes (user_id, book_id, note, is_private)
                        VALUES (?, ?, ?, ?)
                    """

                    conn.prepareStatement(insertSql).use { insertStmt ->
                        insertStmt.setLong(1, userId)
                        insertStmt.setLong(2, bookId)
                        insertStmt.setString(3, note)
                        insertStmt.setBoolean(4, isPrivate)

                        if (insertStmt.executeUpdate() > 0) {
                            return@execute getUserNote(userId, bookId)
                        }
                    }
                }
            }

            null
        }
    }

    suspend fun deleteNote(userId: Long, bookId: Long): Boolean {
        return queryExecutor.execute(name = "deleteNote") { conn ->
            val sql = "DELETE FROM user_notes WHERE user_id = ? AND book_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setLong(2, bookId)

                stmt.executeUpdate() > 0
            }
        }
    }

    suspend fun getUserNote(userId: Long, bookId: Long): UserNote? {
        return queryExecutor.execute(readOnly = true, name = "getUserNote") { conn ->
            val sql = """
                SELECT id, book_id, note, is_private, created_at, updated_at
                FROM user_notes
                WHERE user_id = ? AND book_id = ?
            """

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setLong(2, bookId)

                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return@execute UserNote(
                        id = rs.getLong("id"),
                        bookId = rs.getLong("book_id"),
                        note = rs.getString("note"),
                        isPrivate = rs.getBoolean("is_private"),
                        createdAt = rs.getLong("created_at"),
                        updatedAt = rs.getLong("updated_at"),
                    )
                }
            }

            null
        }
    }

    suspend fun recordDownload(
        userId: Long,
        bookId: Long,
        format: String,
    ): Boolean {
        return queryExecutor.execute(name = "recordDownload") { conn ->
            val sql =
                "INSERT INTO downloads (user_id, book_id, format) VALUES (?, ?, ?)"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setLong(2, bookId)
                stmt.setString(3, format)

                stmt.executeUpdate() > 0
            }
        }
    }

    suspend fun getRecentActivity(limit: Int = 20): RecentActivity {
        return queryExecutor.execute(readOnly = true, name = "getRecentActivity") { conn ->
            // Get recent comments
            val commentsSql = """
                SELECT uc.id, uc.user_id, uc.book_id, uc.comment, uc.created_at, uc.updated_at,
                       u.name as user_name, u.avatar_url as user_avatar_url,
                       b.title as book_title
                FROM user_comments uc
                INNER JOIN users u ON uc.user_id = u.id
                INNER JOIN books b ON uc.book_id = b.id
                ORDER BY uc.created_at DESC
                LIMIT ?
            """

            val comments = conn.prepareStatement(commentsSql).use { stmt ->
                stmt.setInt(1, limit)
                val rs = stmt.executeQuery()
                val result = mutableListOf<UserComment>()

                while (rs.next()) {
                    result.add(
                        UserComment(
                            id = rs.getLong("id"),
                            userId = rs.getLong("user_id"),
                            userName = rs.getString("user_name"),
                            userAvatarUrl = rs.getString("user_avatar_url"),
                            bookId = rs.getLong("book_id"),
                            bookTitle = rs.getString("book_title"),
                            comment = rs.getString("comment"),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at"),
                        ),
                    )
                }
                result
            }

            // Get recent downloads
            val downloadsSql = """
                SELECT d.id, d.user_id, d.book_id, d.format, d.created_at,
                       u.name as user_name,
                       b.title as book_title
                FROM downloads d
                INNER JOIN users u ON d.user_id = u.id
                INNER JOIN books b ON d.book_id = b.id
                ORDER BY d.created_at DESC
                LIMIT ?
            """

            val downloads = conn.prepareStatement(downloadsSql).use { stmt ->
                stmt.setInt(1, limit)
                val rs = stmt.executeQuery()
                val result = mutableListOf<Download>()

                while (rs.next()) {
                    result.add(
                        Download(
                            id = rs.getLong("id"),
                            userId = rs.getLong("user_id"),
                            userName = rs.getString("user_name"),
                            bookId = rs.getLong("book_id"),
                            bookTitle = rs.getString("book_title"),
                            format = rs.getString("format"),
                            createdAt = rs.getLong("created_at"),
                        ),
                    )
                }
                result
            }

            RecentActivity(comments, downloads)
        }
    }

    private suspend fun getLatestComment(
        userId: Long,
        bookId: Long,
    ): UserComment? {
        return queryExecutor.execute(readOnly = true, name = "getLatestComment") { conn ->
            val sql = """
                SELECT uc.id, uc.user_id, uc.book_id, uc.comment, uc.created_at, uc.updated_at,
                       u.name as user_name, u.avatar_url as user_avatar_url,
                       b.title as book_title
                FROM user_comments uc
                INNER JOIN users u ON uc.user_id = u.id
                INNER JOIN books b ON uc.book_id = b.id
                WHERE uc.user_id = ? AND uc.book_id = ?
                ORDER BY uc.created_at DESC
                LIMIT 1
            """

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setLong(2, bookId)

                val rs = stmt.executeQuery()
                if (rs.next()) {
                    UserComment(
                        id = rs.getLong("id"),
                        userId = rs.getLong("user_id"),
                        userName = rs.getString("user_name"),
                        userAvatarUrl = rs.getString("user_avatar_url"),
                        bookId = rs.getLong("book_id"),
                        bookTitle = rs.getString("book_title"),
                        comment = rs.getString("comment"),
                        createdAt = rs.getLong("created_at"),
                        updatedAt = rs.getLong("updated_at"),
                    )
                } else null
            }
        }
    }
}
