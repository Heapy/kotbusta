# Kotbusta - Digital Library for Flibusta Books

A modern web application for browsing, searching, and downloading books from Flibusta torrent archives. Built with Kotlin/Ktor backend and Preact frontend.

## Features

- 📚 **Browse Books**: View books with covers, metadata, and descriptions
- 🔍 **Advanced Search**: Search by title, author, genre, and language
- ⭐ **Favorites**: Star books and create personal collections
- 📝 **Notes & Comments**: Add private notes and public comments
- 📥 **Format Conversion**: Download books in FB2, EPUB, or MOBI formats
- 🔐 **Google OAuth**: Secure authentication with Google accounts
- 📱 **Responsive Design**: Works on desktop and mobile devices

## Technology Stack

**Backend:**
- Kotlin + Ktor
- Postgres database with jOOQ
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
- Flibusta torrent data (FB2 files and INPX metadata)
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
   - `KOTBUSTA_SESSION_SIGN_KEY` - Session signing key (will be auto-generated if not provided)
   - `KOTBUSTA_SESSION_ENCRYPT_KEY` - Session encryption key (will be auto-generated if not provided)
   - `KOTBUSTA_ADMIN_EMAIL` - Your admin email address
   - `KOTBUSTA_POSTGRES_HOST` - PostgreSQL host
   - `KOTBUSTA_POSTGRES_PORT` - PostgreSQL port
   - `KOTBUSTA_POSTGRES_USER` - PostgreSQL username
   - `KOTBUSTA_POSTGRES_PASSWORD` - PostgreSQL password
   - `KOTBUSTA_POSTGRES_DATABASE` - PostgreSQL database name
   - `KOTBUSTA_DB_DATA_PATH_LOCAL` - Local path for PostgreSQL data storage
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
   - Try downloading a book in different formats

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

5. **Start postgres container**
   ```bash
   docker-compose up -d
   ```
6. **Start the application in IDEA***
   - Run Kotbusta run-configuration

7. **Access the application**
   - Open http://localhost:8080
   - Click "Login with Google" to authenticate
   - Go to "Admin" and run import
   - Start browsing your digital library!

## API Endpoints

### Public Endpoints
- `GET /api/books` - List books with pagination
- `GET /api/books/search` - Search books
- `GET /api/books/{id}` - Get book details
- `GET /api/books/{id}/cover` - Get book cover image
- `GET /api/books/{id}/comments` - Get book comments
- `GET /api/activity` - Get recent activity

### Authenticated Endpoints
- `POST/DELETE /api/books/{id}/star` - Star/unstar books
- `GET /api/books/starred` - Get starred books
- `POST /api/books/{id}/comments` - Add comment
- `POST /api/books/{id}/notes` - Add/update note
- `GET /api/books/{id}/download/{format}` - Download book

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
- `users` - User accounts (from Google OAuth)
- `user_stars` - User's starred books
- `user_comments` - Public comments
- `user_notes` - Private notes
- `downloads` - Download history

### File Structure

```
kotbusta/
├── src/main/kotlin/io/heapy/kotbusta/
│   ├── Application.kt         # Main application
│   ├── ApplicationFactory.kt  # All services, daos and configurations are created here
│   ├── ktor/                  # Ktor routes and modules
│   ├── database/              # Database setup
│   ├── jooq/                  # jOOQ code generation
│   ├── model/                 # Data models
│   ├── parser/                # FB2/INPX parsers
│   └── service/               # Business logic
├── src/main/resources/
│   ├── static/                # Frontend files
│   └── application.conf       # Ktor Configuration
└── .env                       # Application Configuration
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

- Flibusta library for book archives
- Pandoc for format conversion
- Preact team for the lightweight framework
- Ktor team for the excellent Kotlin framework
