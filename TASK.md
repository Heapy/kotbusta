# This document describes requirements used to vibe-code this website

You're building kotbusta – a UI for viewing, searching and downloading books
that obtained from flibusta torrent.

Flibusta torrent consists of files listed in `all-files.txt` (result if ls -l
in root folder)
It seems that most of the files zip archives with books in fb2 format
(example in project folder: `fb2-168103-172702.zip`, I also unpack it into
`fb2-168103-172702` folder).
One archive is named `flibusta_fb2_local.inpx`, it's basically a zip archive
with a bunch of files, see `flibusta_fb2_local.inpx.txt` for a list of files. I
also unpack it into `flibusta_fb2_local` folder.

## Task 1

Analyze mentioned files and folders, what useful information kotbusta could
present to the user for each book?

### Answer

Book Information Available:

From FB2 Files (actual books):

- Title - book title (e.g., "Wicked Uncle")
- Author - first name and last name (e.g., "Patricia Wentworth")
- Genre - categorized genres (e.g., "det_classic", "thriller", "sf")
- Language - book language code (e.g., "en", "ru", "uk")
- Annotation - book description/summary
- Publication Date - when digitized/added
- Series Information - series name and number if applicable
- Cover Image - embedded cover art
- File Size - from archive structure
- Book ID - unique identifier (e.g., 168104)

From INPX Metadata Files:

- Complete Catalog - structured metadata for all books
- Author Information - full author names with proper formatting
- Series Data - series titles and book numbers
- Genre Classification - standardized genre codes
- File Locations - which ZIP archive contains each book
- Collection Statistics - total books, authors, series

Archive Structure:

- ~200 ZIP files containing books in ID ranges (e.g., fb2-168103-172702.zip)
- ~4,600 individual FB2 files per archive
- Metadata index (flibusta_fb2_local.inpx) with catalog information

UI Features kotbusta Could Offer:

1. Search by author, title, genre, or series
2. Browse by genre categories
3. Series view with reading order
4. Book details with cover, description, and metadata
5. Download links to specific ZIP archives
6. Reading progress tracking
7. Collection statistics and browsing
8. Multi-language support (Russian, English, Ukrainian)

## Task 2

So what functionality do we need?

1. Login with Google
2. View books with their covers, author, description, etc. similar books.
3. Search for books (by title, author, genre)
4. Download books (convert to epub/mobi on the fly)
5. Star books, add notes and comments
6. Page with recent comments and downloads

In terms of technology, I would like to use:

Infra:
- docker (or docker compose if multiple containers are needed)

Backend:
- kotlin, ktor version 3.2.3 for backend
- package io.heapy.kotbusta
- anything self-hosted most reliable for fb2 to epub/mobi conversion
  (ideally java/kotlin, but any language will do in separate container)
- sqlite for db (one anything embedded, really)
- for database access use plain jdbc or jooq

Frontend:
- no bundler, just plain html/css/js
- preact (version 10.27.0)
- css/js/html with the latest features (no compatibility requirements, just make
  sure it works in the latest firefox)
- use existing ktor server for serving static files

## Plan by Claude Sonnet 4

Kotbusta Implementation Plan

### System Architecture

- Main App: Kotlin + Ktor 3.2.3 server with embedded SQLite
- Conversion Service: Separate container for FB2→EPUB/MOBI conversion
- Frontend: Preact 10.27.0 with modern HTML/CSS/JS served by Ktor
- Infrastructure: Docker Compose setup

### Implementation Steps

1. Project Setup & Database
   - Create Kotlin/Ktor project structure
   - Design SQLite schema for books, users, stars, comments
   - Build FB2/INPX parsers to populate database
2. Backend APIs
   - Google OAuth authentication
   - REST endpoints for books, search, user actions
   - File serving for book downloads
3. Conversion Service
   - Research FB2→EPUB/MOBI conversion tools
   - Setup separate microservice container
   - Integrate with main app
4. Frontend
   - Modern Preact components for book browsing
   - Search interface and user features
   - No-bundler setup with latest JS/CSS features
5. Deployment
   - Docker Compose configuration
   - Volume mounts for book data and database
