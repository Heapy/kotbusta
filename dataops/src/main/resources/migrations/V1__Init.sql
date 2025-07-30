CREATE TABLE IF NOT EXISTS users
(
    id         BIGSERIAL PRIMARY KEY,
    google_id  TEXT UNIQUE NOT NULL,
    email      TEXT        NOT NULL,
    name       TEXT        NOT NULL,
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS authors
(
    id         BIGSERIAL PRIMARY KEY,
    first_name TEXT,
    last_name  TEXT    NOT NULL,
    full_name  TEXT    NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS series
(
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT    NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS books
(
    id            BIGINT PRIMARY KEY,
    title         TEXT    NOT NULL,
    annotation    TEXT,
    genre         TEXT,
    language      TEXT    NOT NULL DEFAULT 'ru',
    series_id     BIGINT,
    series_number INTEGER,
    file_path     TEXT    NOT NULL,
    archive_path  TEXT    NOT NULL,
    file_size     BIGINT,
    date_added    TIMESTAMPTZ NOT NULL,
    cover_image   BYTEA,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_books_series FOREIGN KEY (series_id) REFERENCES series (id)
);

CREATE TABLE IF NOT EXISTS book_authors
(
    book_id   BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    PRIMARY KEY (book_id, author_id),
    CONSTRAINT fk_book_authors_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
    CONSTRAINT fk_book_authors_author FOREIGN KEY (author_id) REFERENCES authors (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_stars
(
    user_id    BIGINT NOT NULL,
    book_id    BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, book_id),
    CONSTRAINT fk_user_stars_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_stars_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_comments
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    book_id    BIGINT NOT NULL,
    comment    TEXT   NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_comments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_comments_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_notes
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT  NOT NULL,
    book_id    BIGINT  NOT NULL,
    note       TEXT    NOT NULL,
    is_private BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_notes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_notes_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS downloads
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    book_id    BIGINT NOT NULL,
    format     TEXT   NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_downloads_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_downloads_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_books_title ON books (title);
CREATE INDEX IF NOT EXISTS idx_books_genre ON books (genre);
CREATE INDEX IF NOT EXISTS idx_books_language ON books (language);
CREATE INDEX IF NOT EXISTS idx_books_series ON books (series_id);
CREATE INDEX IF NOT EXISTS idx_authors_name ON authors (last_name, first_name);
CREATE INDEX IF NOT EXISTS idx_user_comments_book ON user_comments (book_id);
CREATE INDEX IF NOT EXISTS idx_user_comments_user ON user_comments (user_id);
CREATE INDEX IF NOT EXISTS idx_downloads_recent ON downloads (created_at DESC);
