package migrations

import migrations.model.Migration
import org.intellij.lang.annotations.Language

@Language("SQLite")
private val sql = """
CREATE TABLE IF NOT EXISTS IMPORT_JOBS
(
    ID                  INTEGER PRIMARY KEY AUTOINCREMENT,
    JOB_TYPE            TEXT    NOT NULL CHECK (JOB_TYPE IN ('DATA_IMPORT', 'COVER_EXTRACTION')),
    STATUS              TEXT    NOT NULL CHECK (STATUS IN ('RUNNING', 'COMPLETED', 'FAILED')),
    PROGRESS            TEXT,
    INP_FILES_PROCESSED INTEGER NOT NULL,
    BOOKS_ADDED         INTEGER NOT NULL,
    BOOKS_UPDATED       INTEGER NOT NULL,
    BOOKS_DELETED       INTEGER NOT NULL,
    COVERS_ADDED        INTEGER NOT NULL,
    BOOK_ERRORS         INTEGER NOT NULL,
    COVER_ERRORS        INTEGER NOT NULL,
    ERROR_MESSAGE       TEXT,
    STARTED_AT          TEXT    NOT NULL,
    COMPLETED_AT        TEXT,
    CREATED_AT          TEXT    NOT NULL
);
$next
CREATE INDEX IF NOT EXISTS IDX_IMPORT_JOBS_STATUS ON IMPORT_JOBS (STATUS);
$next
CREATE INDEX IF NOT EXISTS IDX_IMPORT_JOBS_TYPE ON IMPORT_JOBS (JOB_TYPE);
$next
CREATE INDEX IF NOT EXISTS IDX_IMPORT_JOBS_STARTED ON IMPORT_JOBS (STARTED_AT DESC);
""".trimIndent()

val v2: Migration
    get() = Migration(
        version = 2,
        script = sql,
    )
