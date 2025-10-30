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
INSERT INTO AUTHORS (ID, FIRST_NAME, LAST_NAME, FULL_NAME, CREATED_AT) VALUES
(1, 'J.K.', 'Rowling', 'J.K. Rowling', '2024-01-01T00:00:00Z'),
(2, 'George R.R.', 'Martin', 'George R.R. Martin', '2024-01-01T00:00:00Z'),
(3, 'Brandon', 'Sanderson', 'Brandon Sanderson', '2024-01-01T00:00:00Z'),
(4, 'Stephen', 'King', 'Stephen King', '2024-01-01T00:00:00Z'),
(5, 'Agatha', 'Christie', 'Agatha Christie', '2024-01-01T00:00:00Z'),
(6, NULL, 'Tolstoy', 'Leo Tolstoy', '2024-01-01T00:00:00Z'),
(7, 'Isaac', 'Asimov', 'Isaac Asimov', '2024-01-01T00:00:00Z');

-- ============================================================================
-- SERIES
-- ============================================================================
INSERT INTO SERIES (ID, NAME, CREATED_AT) VALUES
(1, 'Harry Potter', '2024-01-01T00:00:00Z'),
(2, 'A Song of Ice and Fire', '2024-01-01T00:00:00Z'),
(3, 'The Stormlight Archive', '2024-01-01T00:00:00Z'),
(4, 'Foundation', '2024-01-01T00:00:00Z');

-- ============================================================================
-- BOOKS
-- ============================================================================
INSERT INTO BOOKS (ID, TITLE, ANNOTATION, GENRE, LANGUAGE, SERIES_ID, SERIES_NUMBER, FILE_PATH, ARCHIVE_PATH, FILE_SIZE, DATE_ADDED, COVER_IMAGE, CREATED_AT) VALUES
(1, 'Harry Potter and the Philosopher''s Stone', 'The first book in the Harry Potter series.', 'Fantasy', 'en', 1, 1, '/books/1.epub', '/archive/1.zip', 524288, '2024-01-01T00:00:00Z', NULL, '2024-01-01T00:00:00Z'),
(2, 'Harry Potter and the Chamber of Secrets', 'The second book in the Harry Potter series.', 'Fantasy', 'en', 1, 2, '/books/2.epub', '/archive/2.zip', 587776, '2024-01-02T00:00:00Z', NULL, '2024-01-02T00:00:00Z'),
(3, 'A Game of Thrones', 'The first book in A Song of Ice and Fire.', 'Fantasy', 'en', 2, 1, '/books/3.epub', '/archive/3.zip', 1048576, '2024-01-03T00:00:00Z', NULL, '2024-01-03T00:00:00Z'),
(4, 'The Way of Kings', 'First book of The Stormlight Archive.', 'Fantasy', 'en', 3, 1, '/books/4.epub', '/archive/4.zip', 2097152, '2024-01-04T00:00:00Z', NULL, '2024-01-04T00:00:00Z'),
(5, 'The Shining', 'A horror novel about the Overlook Hotel.', 'Horror', 'en', NULL, NULL, '/books/5.epub', '/archive/5.zip', 458752, '2024-01-05T00:00:00Z', NULL, '2024-01-05T00:00:00Z'),
(6, 'Murder on the Orient Express', 'A classic Hercule Poirot mystery.', 'Mystery', 'en', NULL, NULL, '/books/6.epub', '/archive/6.zip', 327680, '2024-01-06T00:00:00Z', NULL, '2024-01-06T00:00:00Z'),
(7, 'War and Peace', 'Epic historical novel set during Napoleon''s invasion of Russia.', 'Historical Fiction', 'en', NULL, NULL, '/books/7.epub', '/archive/7.zip', 1572864, '2024-01-07T00:00:00Z', NULL, '2024-01-07T00:00:00Z'),
(8, 'Foundation', 'The first book in the Foundation series.', 'Science Fiction', 'en', 4, 1, '/books/8.epub', '/archive/8.zip', 393216, '2024-01-08T00:00:00Z', NULL, '2024-01-08T00:00:00Z'),
(9, 'Foundation and Empire', 'The second book in the Foundation series.', 'Science Fiction', 'en', 4, 2, '/books/9.epub', '/archive/9.zip', 409600, '2024-01-09T00:00:00Z', NULL, '2024-01-09T00:00:00Z'),
(10, 'Mistborn: The Final Empire', 'First book in the Mistborn trilogy.', 'Fantasy', 'en', NULL, NULL, '/books/10.epub', '/archive/10.zip', 655360, '2024-01-10T00:00:00Z', NULL, '2024-01-10T00:00:00Z');

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
-- USER_STARS (User Favorites)
-- ============================================================================
INSERT INTO USER_STARS (USER_ID, BOOK_ID, CREATED_AT) VALUES
(1, 1, '2024-01-10T10:00:00Z'),  -- John starred Harry Potter 1
(1, 3, '2024-01-11T10:00:00Z'),  -- John starred Game of Thrones
(1, 4, '2024-01-12T10:00:00Z'),  -- John starred The Way of Kings
(2, 1, '2024-01-10T11:00:00Z'),  -- Jane starred Harry Potter 1
(2, 6, '2024-01-13T11:00:00Z'),  -- Jane starred Murder on the Orient Express
(2, 8, '2024-01-14T11:00:00Z');  -- Jane starred Foundation

-- ============================================================================
-- USER_COMMENTS
-- ============================================================================
INSERT INTO USER_COMMENTS (ID, USER_ID, BOOK_ID, COMMENT, CREATED_AT, UPDATED_AT) VALUES
(1, 1, 1, 'Amazing start to the series!', '2024-01-15T10:00:00Z', '2024-01-15T10:00:00Z'),
(2, 1, 3, 'Epic fantasy at its finest.', '2024-01-16T10:00:00Z', '2024-01-16T10:00:00Z'),
(3, 2, 1, 'A classic that never gets old.', '2024-01-15T11:00:00Z', '2024-01-15T11:00:00Z'),
(4, 2, 6, 'Brilliant detective work by Poirot!', '2024-01-17T11:00:00Z', '2024-01-17T11:00:00Z');

-- ============================================================================
-- USER_NOTES
-- ============================================================================
INSERT INTO USER_NOTES (ID, USER_ID, BOOK_ID, NOTE, IS_PRIVATE, CREATED_AT, UPDATED_AT) VALUES
(1, 1, 1, 'Remember to recommend this to my nephew.', 1, '2024-01-18T10:00:00Z', '2024-01-18T10:00:00Z'),
(2, 1, 4, 'Start reading book 2 when it arrives.', 1, '2024-01-19T10:00:00Z', '2024-01-19T10:00:00Z'),
(3, 2, 8, 'Check out the sequels in the series.', 0, '2024-01-18T11:00:00Z', '2024-01-18T11:00:00Z');

-- ============================================================================
-- DOWNLOADS
-- ============================================================================
INSERT INTO DOWNLOADS (ID, USER_ID, BOOK_ID, FORMAT, CREATED_AT) VALUES
(1, 1, 1, 'EPUB', '2024-01-20T10:00:00Z'),
(2, 1, 3, 'EPUB', '2024-01-20T10:30:00Z'),
(3, 2, 1, 'MOBI', '2024-01-20T11:00:00Z'),
(4, 2, 6, 'EPUB', '2024-01-20T11:30:00Z'),
(5, 1, 4, 'EPUB', '2024-01-21T10:00:00Z');

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
INSERT INTO KINDLE_SEND_QUEUE (ID, USER_ID, DEVICE_ID, BOOK_ID, FORMAT, STATUS, ATTEMPTS, NEXT_RUN_AT, LAST_ERROR, CREATED_AT, UPDATED_AT) VALUES
(1, 1, 1, 1, 'EPUB', 'COMPLETED', 1, '2024-01-22T10:00:00Z', NULL, '2024-01-22T10:00:00Z', '2024-01-22T10:05:00Z'),
(2, 1, 1, 3, 'EPUB', 'PENDING', 0, '2024-01-23T10:00:00Z', NULL, '2024-01-23T09:00:00Z', '2024-01-23T09:00:00Z'),
(3, 1, 2, 4, 'EPUB', 'PROCESSING', 1, '2024-01-23T10:30:00Z', NULL, '2024-01-23T10:00:00Z', '2024-01-23T10:00:00Z'),
(4, 2, 3, 6, 'MOBI', 'FAILED', 3, '2024-01-24T11:00:00Z', 'Email delivery failed: Invalid recipient', '2024-01-23T11:00:00Z', '2024-01-23T11:15:00Z'),
(5, 2, 3, 8, 'EPUB', 'COMPLETED', 1, '2024-01-24T11:30:00Z', NULL, '2024-01-24T11:00:00Z', '2024-01-24T11:05:00Z');

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

-- ============================================================================
-- IMPORT_JOBS
-- ============================================================================
INSERT INTO IMPORT_JOBS (ID, JOB_TYPE, STATUS, PROGRESS, INP_FILES_PROCESSED, BOOKS_ADDED, BOOKS_UPDATED, BOOKS_DELETED, COVERS_ADDED, BOOK_ERRORS, COVER_ERRORS, ERROR_MESSAGE, STARTED_AT, COMPLETED_AT, CREATED_AT) VALUES
(1, 'DATA_IMPORT', 'COMPLETED', 'Completed successfully', 10, 10, 0, 0, 8, 0, 2, NULL, '2024-01-01T00:00:00Z', '2024-01-01T00:10:00Z', '2024-01-01T00:00:00Z'),
(2, 'COVER_EXTRACTION', 'COMPLETED', 'Extracted covers for all books', 10, 0, 0, 0, 10, 0, 0, NULL, '2024-01-01T00:15:00Z', '2024-01-01T00:20:00Z', '2024-01-01T00:15:00Z'),
(3, 'DATA_IMPORT', 'RUNNING', 'Processing file 5 of 20', 5, 5, 0, 0, 3, 0, 0, NULL, '2024-01-25T10:00:00Z', NULL, '2024-01-25T10:00:00Z'),
(4, 'DATA_IMPORT', 'FAILED', 'Failed to process input', 3, 2, 0, 0, 1, 1, 0, 'IOException: Unable to read archive file', '2024-01-24T15:00:00Z', '2024-01-24T15:05:00Z', '2024-01-24T15:00:00Z');

