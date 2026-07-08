package migrations

import migrations.model.Migration
import org.intellij.lang.annotations.Language

@Language("SQLite")
private val sql = """
CREATE TABLE IF NOT EXISTS FEATURED_BOOKS
(
    ID              INTEGER PRIMARY KEY AUTOINCREMENT,
    BOOK_ID         INTEGER NOT NULL,
    SOURCE          TEXT    NOT NULL,
    EXTERNAL_TITLE  TEXT    NOT NULL,
    EXTERNAL_AUTHOR TEXT    NOT NULL,
    RATING          REAL    NOT NULL,
    SOURCE_RANK     INTEGER NOT NULL,
    FETCHED_AT      TEXT    NOT NULL,
    FOREIGN KEY (BOOK_ID) REFERENCES BOOKS (ID) ON DELETE CASCADE
);
$next
CREATE UNIQUE INDEX IF NOT EXISTS IDX_FEATURED_BOOKS_SOURCE_BOOK ON FEATURED_BOOKS (SOURCE, BOOK_ID);
$next
CREATE INDEX IF NOT EXISTS IDX_FEATURED_BOOKS_RATING ON FEATURED_BOOKS (SOURCE, RATING);
""".trimIndent()

val v2: Migration
    get() = Migration(
        version = 2,
        script = sql,
    )
