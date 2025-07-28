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
- Kotlin + Ktor 3.2.2
- SQLite database with JDBC
- Google OAuth authentication
- RESTful API design

**Frontend:**
- Preact 10.27.0 (no bundler)
- Modern CSS with CSS Grid/Flexbox
- Native ES modules
- Progressive Web App features

**Infrastructure:**
- Docker & Docker Compose
- Calibre-based conversion service
- Volume mounts for book data

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Flibusta torrent data (FB2 files and INPX metadata)
- Google OAuth credentials

### Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd kotbusta
   ```

2. **Set up environment**
   ```bash
   cp .env.example .env
   # Edit .env with your Google OAuth credentials
   ```

3. **Configure Google OAuth**
   - Go to [Google Console](https://console.developers.google.com/)
   - Create a new project or select existing
   - Enable Google+ API
   - Create OAuth 2.0 credentials
   - Add authorized redirect URI: `http://localhost:8080/callback`
   - Copy Client ID and Secret to `.env`

4. **Prepare book data**
   ```bash
   # Create directory for your Flibusta data
   mkdir books-data
   # Copy your FB2 archives and INPX files here
   # Structure should match: books-data/fb2-*.zip, books-data/flibusta_fb2_local.inpx
   ```

5. **Start the application**
   ```bash
   docker-compose up -d
   ```

6. **Import book data**
   ```bash
   # Access the main container
   docker-compose exec kotbusta-app /bin/bash
   
   # Run the INPX parser (one-time setup)
   java -cp app.jar io.heapy.kotbusta.parser.InpxParserKt \
     /app/books-data/flibusta_fb2_local.inpx \
     /app/books-data
   ```

7. **Access the application**
   - Open http://localhost:8080
   - Click "Login with Google" to authenticate
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
   ./gradlew run
   ```

2. **Frontend development**
   - Edit files in `src/main/resources/static/`
   - No build process needed - uses native ES modules
   - Browser will reload automatically

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
│   ├── Application.kt          # Main application
│   ├── config/                 # Configuration modules
│   ├── database/              # Database setup
│   ├── model/                 # Data models
│   ├── parser/                # FB2/INPX parsers
│   └── service/               # Business logic
├── src/main/resources/
│   ├── static/                # Frontend files
│   └── application.conf       # Configuration
├── conversion-service/        # Python conversion service
└── docker-compose.yml        # Docker setup
```

## Configuration

### Environment Variables

- `GOOGLE_CLIENT_ID` - Google OAuth client ID
- `GOOGLE_CLIENT_SECRET` - Google OAuth client secret
- `DATABASE_PATH` - SQLite database file path
- `BOOKS_DATA_PATH` - Path to Flibusta book archives
- `CONVERSION_SERVICE_URL` - URL of conversion service

### Application Configuration

Edit `src/main/resources/application.conf` for advanced configuration:
- Server port and host
- Database settings
- OAuth settings
- File paths

## Deployment

### Production Deployment

1. **Secure Configuration**
   ```bash
   # Use secure cookie settings
   # Set proper CORS origins
   # Use HTTPS with reverse proxy
   ```

2. **Docker Production**
   ```bash
   # Use production environment file
   cp .env.example .env.prod
   # Set production values
   
   # Deploy
   docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
   ```

3. **Reverse Proxy Setup**
   ```nginx
   server {
       listen 443 ssl;
       server_name your-domain.com;
       
       location / {
           proxy_pass http://localhost:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
       }
   }
   ```

### Data Backup

```bash
# Backup database
docker-compose exec kotbusta-app cp /app/data/kotbusta.db /app/data/backup-$(date +%Y%m%d).db

# Backup user data
tar -czf kotbusta-backup-$(date +%Y%m%d).tar.gz data/
```

## Troubleshooting

### Common Issues

1. **Books not appearing**
   - Check INPX import completed successfully
   - Verify book data path is correctly mounted
   - Check database permissions

2. **Authentication failing**
   - Verify Google OAuth credentials
   - Check authorized redirect URIs
   - Ensure cookies are enabled

3. **Download/conversion issues**
   - Check conversion service is running
   - Verify book archive files are accessible
   - Check calibre installation in conversion container

4. **Performance issues**
   - Index database for large collections
   - Monitor container resources
   - Consider database optimization

### Logs

```bash
# View application logs
docker-compose logs kotbusta-app

# View conversion service logs
docker-compose logs conversion-service

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

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Flibusta library for book archives
- Calibre for format conversion
- Preact team for the lightweight framework
- Ktor team for the excellent Kotlin framework