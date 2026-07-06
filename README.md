# Kotbusta - Digital Books Library

A modern web application for browsing, searching, and downloading books that using Flibusta (MyHomeLib) digital library format. Built with Kotlin/Ktor backend and Preact frontend.

![Coverage](.github/badges/jacoco.svg)

## Features

- 📚 **Browse Books**: View books with covers, metadata, and descriptions
- 🔍 **Advanced Search**: Search by title, author, genre, language, and enriched annotations
- 🧠 **Semantic Search**: Optional local ONNX embeddings for KNN search and similar-book recommendations
- 📥 **Format Conversion**: Download books as the original FB2 or convert to EPUB (via Pandoc)
- 📤 **Send to Kindle**: Queue EPUB deliveries to registered Kindle devices
- 🔐 **Google OAuth**: Secure authentication with Google accounts
- 📈 **Operations**: Health and Prometheus metrics endpoints
- 📱 **Responsive Design**: Works on desktop and mobile devices

## Technology Stack

**Backend:**
- Kotlin + Ktor
- SQLite database with jOOQ
- Embedded Lucene search index
- Optional DJL/ONNX semantic embedding worker
- Micrometer/Prometheus metrics
- Google OAuth authentication
- RESTful API design

**Frontend:**
- Preact (no bundler)
- Modern CSS with CSS Grid/Flexbox
- Native ES modules
- Progressive Web App features

**Infrastructure:**
- Docker & Docker Compose
- Pandoc-based conversion service
- Read-Only Volume mounts for book data

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Your digital library in Flibusta format (FB2 files and INPX metadata)
- Google OAuth credentials

### Deployment

1. **Clone the repository**
   ```bash
   git clone https://github.com/Heapy/kotbusta.git
   cd kotbusta
   ```

2. **Set up environment variables**
   ```bash
   cp .env.example .env
   ```

   Edit `.env` and configure the following required variables:
   - `KOTBUSTA_GOOGLE_CLIENT_ID` - Your Google OAuth client ID
   - `KOTBUSTA_GOOGLE_CLIENT_SECRET` - Your Google OAuth client secret
   - `KOTBUSTA_GOOGLE_REDIRECT_URI` - OAuth redirect URI (e.g., `https://yourdomain.com/callback`)
   - `KOTBUSTA_SESSION_SIGN_KEY` - Session signing key. Auto-generated if not provided, but for production set it explicitly: with auto-generated keys every restart invalidates existing sessions (all users are logged out).
   - `KOTBUSTA_SESSION_ENCRYPT_KEY` - Session encryption key (same caveat as the sign key — set it explicitly for production)
   - `KOTBUSTA_ADMIN_EMAIL` - Your admin email address
   - `KOTBUSTA_DB_PATH` - Path to SQLite database file (required; e.g. `/data/db/kotbusta.db`)
   - `KOTBUSTA_LUCENE_INDEX_PATH` - Path to the Lucene search index (required; must be writable. In the prod compose it is set to `/data/db/lucene`)
   - `KOTBUSTA_BOOKS_DATA_PATH_LOCAL` - Local path to your Flibusta book archives

3. **Prepare your Flibusta data**
   ```bash
   # Create directory for book data if it doesn't exist
   mkdir -p /path/to/flibusta/books

   # Your directory should contain:
   # - fb2-*.zip archives with books
   # - flibusta_fb2_local.inpx metadata file
   ```

4. **Run the application**
   ```bash
   docker compose -f deploy/prod/docker-compose.yml up -d
   ```

5. **Monitor the startup**
   ```bash
   # Check logs to ensure services started correctly
   docker compose -f deploy/prod/docker-compose.yml logs -f

   # Verify containers are running
   docker compose -f deploy/prod/docker-compose.yml ps
   ```

6. **Initial setup**
   - Navigate to your configured URL (e.g., `https://yourdomain.com`)
   - Login with Google using your admin email
   - Go to the Admin panel
   - Run the import process to index your book collection
   - The import may take some time depending on your collection size

7. **Verify installation**
   - Check that books appear in the catalog
   - Test search functionality
   - Try downloading a book as FB2 and EPUB

### Development Setup

1. **Clone the repository**
   ```bash
   git clone git@github.com:Heapy/kotbusta.git
   cd kotbusta
   ```

2. **Set up environment**
   ```bash
   cp .env.example .env
   # Edit .env
   ```

3. **Configure Google OAuth**
   - Go to [Google Console](https://console.developers.google.com/)
   - Create a new project or select existing
   - Create OAuth 2.0 credentials
   - Add authorized redirect URI: `http://localhost:8080/callback`
   - Copy Client ID and Secret to `.env`
   - Add your email address as ad

4. **Prepare book data**
   ```bash
   # Create directory for your Flibusta data
   mkdir books-data
   # Copy your FB2 archives and INPX files here
   # Structure should match: books-data/fb2-*.zip, books-data/flibusta_fb2_local.inpx
   ```

5. **Start the application in IDEA**
   - Run Kotbusta run-configuration
   - The SQLite database will be created automatically on first run

6. **Access the application**
   - Open http://localhost:8080
   - Click "Login with Google" to authenticate
   - Go to "Admin" and run import
   - Start browsing your digital library!

## API Endpoints

### Public Endpoints
- `GET /login` - Redirects to `/oauth/google`
- `GET /oauth/google` - Redirect to Google OAuth login page
- `GET /callback` - Google OAuth callback
- `GET /logout` - Logout clearing session data
- `GET /health` - Service health and search-index state
- `GET /metrics` - Prometheus metrics, optionally protected by `KOTBUSTA_METRICS_TOKEN`

### Authenticated Endpoints
- `GET /api/me` - Get current user information
- `GET /api/books` - List books with pagination
- `GET /api/search/books` - Search books
- `GET /api/books/{id}` - Get book details
- `GET /api/books/{id}/cover` - Get book cover image
- `GET /api/books/{id}/similar` - Get similar books
- `GET /api/books/{id}/download/{format}` - Download book as `fb2` or `epub`
- `GET /api/kindle/devices` - List Kindle devices
- `POST /api/kindle/devices` - Add a Kindle device
- `PUT /api/kindle/devices/{id}` - Update a Kindle device
- `DELETE /api/kindle/devices/{id}` - Delete a Kindle device
- `POST /api/books/{id}/send-to-kindle` - Queue an EPUB delivery
- `GET /api/kindle/sends` - List Kindle send history

### Admin Endpoints
- `GET /api/admin/status` - Check admin rights status
- `POST /api/admin/import` - Start book import process
- `GET /api/admin/jobs` - Get all import jobs and their status

## Development

### Running Locally

1. **Backend development**
   ```bash
   Start `Kotbusta` run-configuration in IDEA
   # Navigate to http://localhost:8080`
   ```

2. **Frontend development**
   - Edit files in `src/main/resources/static/`
   - No build process needed, Kotbusta uses native ES modules
   - Reload browser to see changes

### Database Schema

The application uses SQLite with the following main tables:
- `books` - Book metadata and file paths
- `authors` - Author information
- `series` - Book series
- `genres` - Genre information
- `book_authors` - Book/author links
- `book_genres` - Book/genre links
- `book_enrichment` - Extracted annotations, embeddings, and enrichment status
- `users` - User accounts (from Google OAuth)
- `kindle_devices` - User Kindle addresses
- `kindle_send_queue` - Pending and historical Kindle deliveries
- `kindle_send_events` - Delivery event history

### File Structure

```
kotbusta/
├── src/main/kotlin/io/heapy/kotbusta/
│   ├── Application.kt         # Main application entry point
│   ├── ApplicationModule.kt   # Dependency injection and bean configuration
│   ├── coroutines/            # Coroutine utilities and context
│   ├── dao/                   # Data access objects (deprecated, being migrated to repository)
│   ├── database/              # Database setup and transaction management
│   ├── jooq/                  # jOOQ generated code (tables, records, enums)
│   ├── ktor/                  # Ktor routes and HTTP modules
│   ├── mapper/                # Data mapping utilities
│   ├── model/                 # Domain models and DTOs
│   ├── parser/                # FB2/INPX file parsers
│   ├── repository/            # Repository layer for data access
│   └── service/               # Business logic and services
├── src/main/resources/
│   ├── static/                # Frontend files (HTML, CSS, JS)
│   └── logback.xml            # Logging configuration
└── .env                       # Application configuration
```

## Documentation

- [INP File Format](docs/INP.md) - Detailed documentation about the INP/INPX file format used by Flibusta for metadata cataloging

## Configuration

### Environment Variables

Edit the `.env` file to configure all aspects of the application.

### Ktor Configuration

Edit `src/main/resources/application.conf` to add additional ktor modules and adjust ktor configuration.

### Logs

```bash
# Follow logs in real-time
docker-compose logs -f
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the AGPL-3.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Flibusta for the original digital library format
- Pandoc for format conversion
- Preact team for the lightweight framework
- Ktor team for the excellent Kotlin framework
