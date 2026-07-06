-- Test Fixtures for Kotbusta Database
-- This file contains a baseline set of test data that can be loaded before each test
-- All timestamps use ISO-8601 format as expected by KotlinInstantConverter

-- ============================================================================
-- USERS
-- ============================================================================
INSERT INTO USERS (ID, GOOGLE_ID, EMAIL, NAME, AVATAR_URL, STATUS, CREATED_AT, UPDATED_AT) VALUES
(1, 'google_123456', 'john.doe@example.com', 'John Doe', 'https://example.com/avatars/john.jpg', 'APPROVED', '2024-01-01T10:00:00Z', '2024-01-01T10:00:00Z'),
(2, 'google_789012', 'jane.smith@example.com', 'Jane Smith', 'https://example.com/avatars/jane.jpg', 'APPROVED', '2024-01-02T11:00:00Z', '2024-01-02T11:00:00Z'),
(3, 'google_345678', 'bob.pending@example.com', 'Bob Pending', NULL, 'PENDING', '2024-01-03T12:00:00Z', '2024-01-03T12:00:00Z'),
(4, 'google_901234', 'alice.rejected@example.com', 'Alice Rejected', NULL, 'REJECTED', '2024-01-04T13:00:00Z', '2024-01-04T13:00:00Z'),
(5, 'google_567890', 'charlie.deactivated@example.com', 'Charlie Deactivated', 'https://example.com/avatars/charlie.jpg', 'DEACTIVATED', '2024-01-05T14:00:00Z', '2024-01-05T15:00:00Z');

-- ============================================================================
-- AUTHORS
-- ============================================================================
INSERT INTO AUTHORS (ID, FULL_NAME) VALUES
(1, 'J.K. Rowling'),
(2, 'George R.R. Martin'),
(3, 'Brandon Sanderson'),
(4, 'Stephen King'),
(5, 'Agatha Christie'),
(6, 'Leo Tolstoy'),
(7, 'Isaac Asimov');

-- ============================================================================
-- SERIES
-- ============================================================================
INSERT INTO SERIES (ID, NAME) VALUES
(1, 'Harry Potter'),
(2, 'A Song of Ice and Fire'),
(3, 'The Stormlight Archive'),
(4, 'Foundation');

-- ============================================================================
-- GENRES
-- ============================================================================
INSERT INTO GENRES (ID, NAME) VALUES
(1, 'Fantasy'),
(2, 'Horror'),
(3, 'Mystery'),
(4, 'Historical Fiction'),
(5, 'Science Fiction');

-- ============================================================================
-- BOOKS
-- ============================================================================
INSERT INTO BOOKS (ID, TITLE, LANGUAGE, SERIES_ID, SERIES_NUMBER, FILE_FORMAT, FILE_PATH, ARCHIVE_PATH, FILE_SIZE, DATE_ADDED, COVER_IMAGE, CREATED_AT) VALUES
(1, 'Harry Potter and the Philosopher''s Stone', 'en', 1, 1, 'fb2','/books/1.epub', '/archive/1.zip', 524288, '2024-01-01T00:00:00Z', NULL, '2024-01-01T00:00:00Z'),
(2, 'Harry Potter and the Chamber of Secrets', 'en', 1, 2, 'fb2','/books/2.epub', '/archive/2.zip', 587776, '2024-01-02T00:00:00Z', NULL, '2024-01-02T00:00:00Z'),
(3, 'A Game of Thrones', 'en', 2, 1, 'fb2','/books/3.epub', '/archive/3.zip', 1048576, '2024-01-03T00:00:00Z', NULL, '2024-01-03T00:00:00Z'),
(4, 'The Way of Kings', 'en', 3, 1, 'fb2','/books/4.epub', '/archive/4.zip', 2097152, '2024-01-04T00:00:00Z', NULL, '2024-01-04T00:00:00Z'),
(5, 'The Shining', 'en', NULL, NULL, 'fb2','/books/5.epub', '/archive/5.zip', 458752, '2024-01-05T00:00:00Z', NULL, '2024-01-05T00:00:00Z'),
(6, 'Murder on the Orient Express', 'en', NULL, NULL, 'fb2','/books/6.epub', '/archive/6.zip', 327680, '2024-01-06T00:00:00Z', NULL, '2024-01-06T00:00:00Z'),
(7, 'War and Peace', 'en', NULL, NULL, 'fb2','/books/7.epub', '/archive/7.zip', 1572864, '2024-01-07T00:00:00Z', NULL, '2024-01-07T00:00:00Z'),
(8, 'Foundation', 'en', 4, 1, 'fb2','/books/8.epub', '/archive/8.zip', 393216, '2024-01-08T00:00:00Z', NULL, '2024-01-08T00:00:00Z'),
(9, 'Foundation and Empire', 'en', 4, 2, 'fb2','/books/9.epub', '/archive/9.zip', 409600, '2024-01-09T00:00:00Z', NULL, '2024-01-09T00:00:00Z'),
(10, 'Mistborn: The Final Empire', 'en', NULL, NULL, 'fb2','/books/10.epub', '/archive/10.zip', 655360, '2024-01-10T00:00:00Z', NULL, '2024-01-10T00:00:00Z');

-- ============================================================================
-- BOOK_ENRICHMENT
-- ============================================================================
INSERT INTO BOOK_ENRICHMENT (BOOK_ID, ANNOTATION, EMBEDDING, STATUS, ENRICHED_AT) VALUES
(1, 'The first book in the Harry Potter series.', NULL, 'DONE', '2024-01-01T00:00:00Z'),
(2, 'The second book in the Harry Potter series.', NULL, 'DONE', '2024-01-02T00:00:00Z'),
(3, 'The first book in A Song of Ice and Fire.', NULL, 'DONE', '2024-01-03T00:00:00Z'),
(4, 'First book of The Stormlight Archive.', NULL, 'DONE', '2024-01-04T00:00:00Z'),
(5, 'A horror novel about the Overlook Hotel.', NULL, 'DONE', '2024-01-05T00:00:00Z'),
(6, 'A classic Hercule Poirot mystery.', NULL, 'DONE', '2024-01-06T00:00:00Z'),
(7, 'Epic historical novel set during Napoleon''s invasion of Russia.', NULL, 'DONE', '2024-01-07T00:00:00Z'),
(8, 'The first book in the Foundation series.', NULL, 'DONE', '2024-01-08T00:00:00Z'),
(9, 'The second book in the Foundation series.', NULL, 'DONE', '2024-01-09T00:00:00Z'),
(10, 'First book in the Mistborn trilogy.', NULL, 'DONE', '2024-01-10T00:00:00Z');

-- ============================================================================
-- BOOK_GENRES (Many-to-Many Relationship)
-- ============================================================================
INSERT INTO BOOK_GENRES (BOOK_ID, GENRE_ID) VALUES
(1, 1),   -- Harry Potter 1 -> Fantasy
(2, 1),   -- Harry Potter 2 -> Fantasy
(3, 1),   -- Game of Thrones -> Fantasy
(4, 1),   -- The Way of Kings -> Fantasy
(5, 2),   -- The Shining -> Horror
(6, 3),   -- Murder on the Orient Express -> Mystery
(7, 4),   -- War and Peace -> Historical Fiction
(8, 5),   -- Foundation -> Science Fiction
(9, 5),   -- Foundation and Empire -> Science Fiction
(10, 1);  -- Mistborn -> Fantasy

-- ============================================================================
-- BOOK_AUTHORS (Many-to-Many Relationship)
-- ============================================================================
INSERT INTO BOOK_AUTHORS (BOOK_ID, AUTHOR_ID) VALUES
(1, 1),  -- Harry Potter 1 -> J.K. Rowling
(2, 1),  -- Harry Potter 2 -> J.K. Rowling
(3, 2),  -- Game of Thrones -> George R.R. Martin
(4, 3),  -- The Way of Kings -> Brandon Sanderson
(5, 4),  -- The Shining -> Stephen King
(6, 5),  -- Murder on the Orient Express -> Agatha Christie
(7, 6),  -- War and Peace -> Leo Tolstoy
(8, 7),  -- Foundation -> Isaac Asimov
(9, 7),  -- Foundation and Empire -> Isaac Asimov
(10, 3); -- Mistborn -> Brandon Sanderson

-- ============================================================================
-- KINDLE_DEVICES
-- ============================================================================
INSERT INTO KINDLE_DEVICES (ID, USER_ID, EMAIL, NAME, CREATED_AT, UPDATED_AT) VALUES
(1, 1, 'john.doe_kindle@kindle.com', 'John''s Kindle Paperwhite', '2024-01-01T10:00:00Z', '2024-01-01T10:00:00Z'),
(2, 1, 'john.doe_kindle2@kindle.com', 'John''s Kindle Oasis', '2024-01-02T10:00:00Z', '2024-01-02T10:00:00Z'),
(3, 2, 'jane.smith_kindle@kindle.com', 'Jane''s Kindle', '2024-01-01T11:00:00Z', '2024-01-01T11:00:00Z');

-- ============================================================================
-- KINDLE_SEND_QUEUE
-- ============================================================================
INSERT INTO KINDLE_SEND_QUEUE (ID, USER_ID, DEVICE_ID, BOOK_ID, BOOK_TITLE, FORMAT, STATUS, ATTEMPTS, NEXT_RUN_AT, LAST_ERROR, CREATED_AT, UPDATED_AT) VALUES
(1, 1, 1, 1, 'Harry Potter and the Philosopher''s Stone', 'EPUB', 'COMPLETED', 1, '2024-01-22T10:00:00Z', NULL, '2024-01-22T10:00:00Z', '2024-01-22T10:05:00Z'),
(2, 1, 1, 3, 'A Game of Thrones', 'EPUB', 'PENDING', 0, '2024-01-23T10:00:00Z', NULL, '2024-01-23T09:00:00Z', '2024-01-23T09:00:00Z'),
(3, 1, 2, 4, 'The Way of Kings', 'EPUB', 'PROCESSING', 1, '2024-01-23T10:30:00Z', NULL, '2024-01-23T10:00:00Z', '2024-01-23T10:00:00Z'),
(4, 2, 3, 6, 'Murder on the Orient Express', 'EPUB', 'FAILED', 3, '2024-01-24T11:00:00Z', 'Email delivery failed: Invalid recipient', '2024-01-23T11:00:00Z', '2024-01-23T11:15:00Z'),
(5, 2, 3, 8, 'Foundation', 'EPUB', 'COMPLETED', 1, '2024-01-24T11:30:00Z', NULL, '2024-01-24T11:00:00Z', '2024-01-24T11:05:00Z');

-- ============================================================================
-- KINDLE_SEND_EVENTS
-- ============================================================================
INSERT INTO KINDLE_SEND_EVENTS (ID, QUEUE_ID, EVENT_TYPE, DETAILS, CREATED_AT) VALUES
(1, 1, 'STARTED', 'Started processing send request', '2024-01-22T10:00:00Z'),
(2, 1, 'COMPLETED', 'Successfully sent to device', '2024-01-22T10:05:00Z'),
(3, 2, 'QUEUED', 'Added to send queue', '2024-01-23T09:00:00Z'),
(4, 3, 'STARTED', 'Started processing send request', '2024-01-23T10:00:00Z'),
(5, 4, 'STARTED', 'Started processing send request', '2024-01-23T11:00:00Z'),
(6, 4, 'RETRY', 'Retrying after failure (attempt 2)', '2024-01-23T11:05:00Z'),
(7, 4, 'RETRY', 'Retrying after failure (attempt 3)', '2024-01-23T11:10:00Z'),
(8, 4, 'FAILED', 'Failed after 3 attempts', '2024-01-23T11:15:00Z'),
(9, 5, 'STARTED', 'Started processing send request', '2024-01-24T11:00:00Z'),
(10, 5, 'COMPLETED', 'Successfully sent to device', '2024-01-24T11:05:00Z');
