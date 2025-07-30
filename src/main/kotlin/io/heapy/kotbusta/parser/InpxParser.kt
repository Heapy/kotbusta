package io.heapy.kotbusta.parser

import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.database.QueryExecutor
import io.heapy.kotbusta.model.Author
import io.heapy.kotbusta.model.Series
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

class InpxParser(
    private val queryExecutor: QueryExecutor,
) {
    suspend fun parseAndImport(booksDataPath: Path) {
        val inpxFilePath = booksDataPath.resolve("flibusta_fb2_local.inpx")
        log.info("Starting INPX parsing from: $inpxFilePath")

        queryExecutor.execute { conn ->
            conn.autoCommit = false

            ZipFile(inpxFilePath.toString()).use { zipFile ->
                val entries = zipFile.entries().asSequence()
                    .filter { it.name.endsWith(".inp") }
                    .toList()

                log.info("Found ${entries.size} .inp files to process")

                entries.forEachIndexed { index, entry ->
                    log.info("Processing ${entry.name} (${index + 1}/${entries.size})")

                    zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.lineSequence().forEach { line ->
                            parseBookLine(line, conn, entry.name.removeSuffix(".inp"))
                        }
                    }

                    if ((index + 1) % 10 == 0) {
                        conn.commit()
                        log.info("Committed batch ${index + 1}")
                    }
                }

                conn.commit()
                log.info("INPX parsing completed successfully")
            }
        }
    }

    private fun parseBookLine(line: String, connection: Connection, archivePath: String) {
        try {
            val parts = line.split('\u0004') // Field separator in INP files
            if (parts.size < 8) return

            val authorPart = parts[0]
            val genre = parts[1]
            val title = parts[2]
            val seriesPart = parts[3]
            val seriesNumber = parts[4].toIntOrNull()
            val bookId = parts[5].toLongOrNull() ?: return
            val fileSize = parts[6].toLongOrNull()
            val libId = parts[7] // seems that it's the same as bookId
            val deleted = parts.getOrNull(8)
            val fileFormat = parts.getOrNull(9)
            val dateAdded = parts.getOrNull(10)
            val language = parts.getOrNull(11) ?: "ru"
            val keywords = parts.getOrNull(12) // too many empty, so not useful for anything

            if (deleted == "1") return // Skip deleted books

            // Parse authors
            val authors = parseAuthors(authorPart)
            if (authors.isEmpty()) return

            // Parse series
            val series = if (seriesPart.isNotBlank()) {
                Series(0, seriesPart.trim())
            } else null

            // Determine file paths
            val filePath = "${bookId}.${fileFormat}"

            // Insert book into database
            insertBook(
                connection = connection,
                bookId = bookId,
                title = title.trim(),
                authors = authors,
                series = series,
                seriesNumber = seriesNumber,
                genre = genre.trim().takeIf { it.isNotBlank() },
                language = language,
                filePath = filePath,
                archivePath = archivePath,
                fileSize = fileSize,
                dateAdded = parseDateAdded(dateAdded)
            )
        } catch (e: Exception) {
            log.error("Error parsing line: $line", e)
        }
    }

    private fun parseAuthors(authorPart: String): List<Author> {
        if (authorPart.isBlank()) return emptyList()

        return authorPart.split(':').mapNotNull { authorStr ->
            val parts = authorStr.split(',').map { it.trim() }
            if (parts.isEmpty() || parts[0].isBlank()) return@mapNotNull null

            val lastName = parts[0]
            val firstName = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            val middleName = parts.getOrNull(2)?.takeIf { it.isNotBlank() }

            val fullName = buildString {
                append(lastName)
                if (firstName != null) {
                    append(", $firstName")
                    if (middleName != null) {
                        append(" $middleName")
                    }
                }
            }

            Author(0, firstName, lastName, fullName)
        }
    }

    private fun parseDateAdded(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis() / 1000

        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val date = LocalDate.parse(dateStr, formatter)
            date.toEpochDay() * 24 * 60 * 60 // Convert to seconds
        } catch (e: Exception) {
            log.error("Error parsing date: $dateStr", e)
            System.currentTimeMillis() / 1000
        }
    }

    private fun insertBook(
        connection: Connection,
        bookId: Long,
        title: String,
        authors: List<Author>,
        series: Series?,
        seriesNumber: Int?,
        genre: String?,
        language: String,
        filePath: String,
        archivePath: String,
        fileSize: Long?,
        dateAdded: Long
    ) {
        // Insert or get series
        val seriesId = series?.let { insertOrGetSeries(connection, it.name) }

        // Insert or get authors
        val authorIds = authors.map { insertOrGetAuthor(connection, it) }

        // Insert book
        val bookSql = """
            INSERT OR REPLACE INTO books
            (id, title, genre, language, series_id, series_number, file_path, archive_path, file_size, date_added)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        connection.prepareStatement(bookSql).use { stmt ->
            stmt.setLong(1, bookId)
            stmt.setString(2, title)
            stmt.setString(3, genre)
            stmt.setString(4, language)
            if (seriesId != null) stmt.setLong(5, seriesId) else stmt.setNull(5, java.sql.Types.INTEGER)
            if (seriesNumber != null) stmt.setInt(6, seriesNumber) else stmt.setNull(6, java.sql.Types.INTEGER)
            stmt.setString(7, filePath)
            stmt.setString(8, archivePath)
            if (fileSize != null) stmt.setLong(9, fileSize) else stmt.setNull(9, java.sql.Types.INTEGER)
            stmt.setLong(10, dateAdded)
            stmt.executeUpdate()
        }

        // Insert book-author relationships
        val bookAuthorSql = "INSERT OR REPLACE INTO book_authors (book_id, author_id) VALUES (?, ?)"
        connection.prepareStatement(bookAuthorSql).use { stmt ->
            authorIds.forEach { authorId ->
                stmt.setLong(1, bookId)
                stmt.setLong(2, authorId)
                stmt.executeUpdate()
            }
        }
    }

    private fun insertOrGetSeries(connection: Connection, name: String): Long {
        // Try to get existing series
        val selectSql = "SELECT id FROM series WHERE name = ?"
        connection.prepareStatement(selectSql).use { stmt ->
            stmt.setString(1, name)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.getLong("id")
            }
        }

        // Insert new series
        val insertSql = "INSERT INTO series (name) VALUES (?)"
        connection.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setString(1, name)
            stmt.executeUpdate()

            val keys = stmt.generatedKeys
            if (keys.next()) {
                return keys.getLong(1)
            }
        }

        throw RuntimeException("Failed to insert series: $name")
    }

    private fun insertOrGetAuthor(connection: Connection, author: Author): Long {
        // Try to get existing author
        val selectSql = "SELECT id FROM authors WHERE full_name = ?"
        connection.prepareStatement(selectSql).use { stmt ->
            stmt.setString(1, author.fullName)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.getLong("id")
            }
        }

        // Insert new author
        val insertSql = "INSERT INTO authors (first_name, last_name, full_name) VALUES (?, ?, ?)"
        connection.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setString(1, author.firstName)
            stmt.setString(2, author.lastName)
            stmt.setString(3, author.fullName)
            stmt.executeUpdate()

            val keys = stmt.generatedKeys
            if (keys.next()) {
                return keys.getLong(1)
            }
        }

        throw RuntimeException("Failed to insert author: ${author.fullName}")
    }

    private companion object : Logger()
}
