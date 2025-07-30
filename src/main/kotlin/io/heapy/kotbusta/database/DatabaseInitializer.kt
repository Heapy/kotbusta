package io.heapy.kotbusta.database

import java.io.File
import java.sql.Connection

class DatabaseInitializer(
    private val queryExecutor: QueryExecutor,
    private val databasePath: String,
) {
    suspend fun initialize() {
        File(databasePath).mkdirs()
        queryExecutor.execute { connection ->
            createTables(connection)
        }
    }

    private fun createTables(connection: Connection) {
        connection.createStatement().use { statement ->
            // Users table
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    google_id TEXT UNIQUE NOT NULL,
                    email TEXT NOT NULL,
                    name TEXT NOT NULL,
                    avatar_url TEXT,
                    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                    updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                )
                """,
            )

            // Authors table
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS authors (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    first_name TEXT,
                    last_name TEXT NOT NULL,
                    full_name TEXT NOT NULL,
                    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                )
                """,
            )

            // Series table
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS series (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                )
                """,
            )

            // Books table
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS books (
                    id INTEGER PRIMARY KEY,
                    title TEXT NOT NULL,
                    annotation TEXT,
                    genre TEXT,
                    language TEXT NOT NULL DEFAULT 'ru',
                    series_id INTEGER,
                    series_number INTEGER,
                    file_path TEXT NOT NULL,
                    archive_path TEXT NOT NULL,
                    file_size INTEGER,
                    date_added INTEGER NOT NULL,
                    cover_image BLOB,
                    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                    FOREIGN KEY (series_id) REFERENCES series(id)
                )
                """,
            )

            // Book authors junction table
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS book_authors (
                    book_id INTEGER NOT NULL,
                    author_id INTEGER NOT NULL,
                    PRIMARY KEY (book_id, author_id),
                    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
                    FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE
                )
                """,
            )

            // User stars table
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS user_stars (
                    user_id INTEGER NOT NULL,
                    book_id INTEGER NOT NULL,
                    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                    PRIMARY KEY (user_id, book_id),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                )
                """,
            )

            // User comments table
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS user_comments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    book_id INTEGER NOT NULL,
                    comment TEXT NOT NULL,
                    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                    updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                )
                """,
            )

            // User notes table
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS user_notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    book_id INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    is_private INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                    updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                )
                """,
            )

            // Downloads table for tracking
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS downloads (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    book_id INTEGER NOT NULL,
                    format TEXT NOT NULL,
                    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                )
                """,
            )

            // Create indexes for performance
            statement.execute("CREATE INDEX IF NOT EXISTS idx_books_title ON books(title)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_books_genre ON books(genre)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_books_language ON books(language)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_books_series ON books(series_id)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_authors_name ON authors(last_name, first_name)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_user_comments_book ON user_comments(book_id)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_user_comments_user ON user_comments(user_id)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_downloads_recent ON downloads(created_at DESC)")
        }
    }
}
