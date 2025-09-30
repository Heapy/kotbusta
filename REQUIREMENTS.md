# Kotbusta Requirements

## Code Authoring Constitution

1. Composition over inheritance
2. Dependency Injection and Inversion of Control for testability
3. Clean architecture with SOLID principles
4. Small, focused solution better than a one-fits-all framework
5. Add tests for every feature, run tests after every change

## Main Modules

1. Dataops – Database operations, migrations, seeding, etc.
2. Importer — Import books from INPX archives and Cover Extract
3. Library — Manage books and user interactions
4. Admin — Administrative functions, calling the importer and managing users
5. Kindle – Manage Kindle devices and send books to them
6. API — REST API on top of mentioned modules

## Importer

1. Import should be idempotent, re-importing the same archive should not change anything
2. Import should be parallel to speed up the process
3. Importing new updated library should not delete physically existing books in a database, gracefully mark as deleted
4. User's personal library should not be touched: likes, notes, uploaded books should be preserved. If a book is deleted from an archive, mark it as deleted in the database, never break ~~userland~~ user data!

# Book Management Features

1. Books should be easy to browse and search. Do infinite scrolling while updating browser history
   - List all books
   - Search books – Advanced search with filters for:
     - Text query (searches titles and author names)
     - Genre filtering
     - Language filtering
     - Author filtering
     - Pagination support
2. View book details – Full information including:
    - Title, authors, genre, language
    - Series information and number
    - File size and date added
    - Annotation/description
    - Cover image
    - User's starred status and personal notes
    - Get book cover images - Separate endpoint for cover images
    - Find similar books - Recommendations based on genre and authors
3. Book Interactions
   - Star/Unstar books - Personal favorites/bookmarks system
   - View starred books - List of user's favorite books
   - Download books - Multiple format support:
       - Native FB2 format
       - Converted formats via Pandoc (EPUB)
       - Download tracking for statistics
   - Send to Kindle - Send books to Kindle device, select from list of available devices
4. Comments System
   - Add comments - Public comments on books
   - View comments - List all comments for a book
   - Update comments - Edit your own comments
   - Delete comments - Remove your own comments
5. Personal Notes
   - Add/Update notes - Private notes for books
   - Delete notes - Remove personal notes
6. Authentication & Profile
   - Login/Logout - Session-based authentication
   - Google OAuth - Social login integration
   - User info - Current user session details
   - Admin approves new users - Admin should approve new users, before they can access the system
7. Activity Tracking
   - View activity - Service star and download activity with user details
8. Kindle Setup
   - Add/Update Kindle devices - Manage your Kindle devices (email + name)
   - Instructions to add kotbusta@heapyhop.com to allowed Kindle Emails
9. Administrative Functions
   - Import books - Bulk import from INPX archives and Cover Extract
   - View import jobs - Monitor background import tasks, see history of imports and stats
   - System status - Health check and statistics

##  Key Frontend Capabilities

1. Responsive Search - Real-time search with multiple filters
2. Infinite Scroll - Pagination for smooth browsing
3. Format Flexibility - Download in user's preferred format
4. Personal Library - Star system for building personal collections
5. Community Engagement - Comments for book discussions
6. Private Notes - Personal annotations and thoughts
7. Visual Experience - Book covers for better browsing
8. Social Login - Login with Google
9. Send to Kindle - Send books to Kindle devices. Support multiple devices. Use Amazon's SES for sending emails, use AWS library, not javax.mail.
10. Upload Books - Upload own books to the system, and store them
11. Read fb2 books right in Kotbusta, without downloading them.
    - Save the current position in the book.
    - Select text to "highlight" it, add annotations
