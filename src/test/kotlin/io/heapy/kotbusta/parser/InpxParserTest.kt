package io.heapy.kotbusta.parser

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.AUTHORS
import io.heapy.kotbusta.jooq.tables.references.BOOK_AUTHORS
import io.heapy.kotbusta.jooq.tables.references.BOOK_GENRES
import io.heapy.kotbusta.jooq.tables.references.BOOKS
import io.heapy.kotbusta.jooq.tables.references.BOOK_ENRICHMENT
import io.heapy.kotbusta.jooq.tables.references.GENRES
import io.heapy.kotbusta.model.ImportStats
import io.heapy.kotbusta.test.DatabaseExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@ExtendWith(DatabaseExtension::class)
class InpxParserTest {
    @Test
    fun `import replaces live catalog and is idempotent`(applicationModule: ApplicationModule) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val parser = applicationModule.inpxParser.value
        val booksDir = Files.createTempDirectory("inpx-test-")
        val stats = ImportStats()

        writeInpx(booksDir, listOf(book(9001, "Alpha"), book(9002, "Beta"), book(9003, "Gamma")))
        parser.parseAndImport(booksDir, stats)

        assertEquals(3, countSynthetic(tx))
        assertEquals(1, stats.inpFilesTotal.load())
        assertEquals(1, stats.inpFilesProcessed.load())
        assertEquals(null, stats.currentInpFile.load())
        val firstSnapshot = syntheticTitles(tx)

        parser.parseAndImport(booksDir, ImportStats())
        assertEquals(firstSnapshot, syntheticTitles(tx))
        assertEquals(3, countSynthetic(tx))
    }

    @Test
    fun `removed books disappear while enrichment survives`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val parser = applicationModule.inpxParser.value
        val booksDir = Files.createTempDirectory("inpx-test-")

        writeInpx(booksDir, listOf(book(9001, "Alpha"), book(9002, "Beta"), book(9003, "Gamma")))
        parser.parseAndImport(booksDir, ImportStats())
        insertEnrichment(tx, bookId = 9002, annotation = "Keep this enrichment")

        writeInpx(booksDir, listOf(book(9001, "Alpha"), book(9003, "Gamma")))
        parser.parseAndImport(booksDir, ImportStats())

        assertFalse(bookExists(tx, 9002))
        assertTrue(bookExists(tx, 9001))
        assertTrue(bookExists(tx, 9003))
        assertEquals("Keep this enrichment", enrichmentAnnotation(tx, 9002))

        writeInpx(booksDir, listOf(book(9001, "Alpha"), book(9002, "Beta"), book(9003, "Gamma")))
        parser.parseAndImport(booksDir, ImportStats())
        assertTrue(bookExists(tx, 9002))
        assertEquals("Keep this enrichment", enrichmentAnnotation(tx, 9002))
    }

    @Test
    fun `duplicate book ids prefer archive range metadata across inp files`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val parser = applicationModule.inpxParser.value
        val booksDir = Files.createTempDirectory("inpx-test-")

        writeInpxEntries(
            booksDir,
            listOf(
                "f.fb2-8990-8999.inp" to listOf(
                    book(9001, "Out of range first", author = "First,Author", genre = "first"),
                ),
                "f.fb2-9001-9003.inp" to listOf(
                    book(9001, "In range later", author = "Later,Author", genre = "later"),
                    book(9002, "Draft", author = "Draft,Author", genre = "draft"),
                    book(9002, "Beta"),
                    book(9003, "In range first", author = "Canonical,Author", genre = "canonical"),
                ),
                "f.fb2-9004-9005.inp" to listOf(
                    book(9003, "Out of range later", author = "Wrong,Author", genre = "wrong"),
                ),
            ),
        )
        parser.parseAndImport(booksDir, ImportStats())

        assertEquals(listOf("In range later", "Beta", "In range first"), syntheticTitles(tx))
        assertEquals("f.fb2-9001-9003", archivePathForBook(tx, 9001))
        assertEquals("f.fb2-9001-9003", archivePathForBook(tx, 9003))
        assertEquals(listOf("Later Author"), authorsForBook(tx, 9001))
        assertEquals(listOf("later"), genresForBook(tx, 9001))
        assertEquals(listOf("Canonical Author"), authorsForBook(tx, 9003))
        assertEquals(listOf("canonical"), genresForBook(tx, 9003))
        assertEquals(3, countSynthetic(tx))
        assertEquals(0, stagingTableCount(tx))
    }

    @Test
    fun `failed staging load leaves live catalog unchanged`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val parser = applicationModule.inpxParser.value
        val booksDir = Files.createTempDirectory("inpx-test-")

        writeInpx(booksDir, listOf(book(9001, "Alpha"), book(9002, "Beta")))
        parser.parseAndImport(booksDir, ImportStats())
        val before = syntheticTitles(tx)

        Files.writeString(booksDir.resolve("flibusta_fb2_local.inpx"), "not a zip")

        assertThrows(Exception::class.java) {
            runBlocking {
                parser.parseAndImport(booksDir, ImportStats())
            }
        }

        assertEquals(before, syntheticTitles(tx))
        assertEquals(0, stagingTableCount(tx))
    }

    @Test
    fun `import aborts after one hundred sequential invalid book records`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val parser = applicationModule.inpxParser.value
        val booksDir = Files.createTempDirectory("inpx-test-")

        writeInpx(booksDir, listOf(book(9001, "Alpha"), book(9002, "Beta")))
        parser.parseAndImport(booksDir, ImportStats())
        val before = syntheticTitles(tx)

        val stats = ImportStats()
        writeRawInpxEntries(booksDir, listOf("bad.inp" to List(100) { "not enough fields" }))

        val error = assertThrows(Exception::class.java) {
            runBlocking {
                parser.parseAndImport(booksDir, stats)
            }
        }

        assertTrue(error.message!!.contains("100 sequential invalid book records"))
        assertEquals(100, stats.bookErrors.load())
        assertEquals(100, stats.sequentialBookErrors.load())
        assertEquals(1, stats.inpFilesTotal.load())
        assertEquals(0, stats.inpFilesProcessed.load())
        assertEquals(null, stats.currentInpFile.load())
        assertEquals(before, syntheticTitles(tx))
        assertEquals(0, stagingTableCount(tx))
    }

    @Test
    fun `sequential invalid book streak resets between inp files`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val parser = applicationModule.inpxParser.value
        val booksDir = Files.createTempDirectory("inpx-test-")
        val invalidLines = List(60) { "not enough fields" }

        writeRawInpxEntries(
            booksDir,
            listOf(
                "a-first.inp" to listOf(rawBookLine(book(9001, "Alpha"))) + invalidLines,
                "b-second.inp" to invalidLines + listOf(rawBookLine(book(9002, "Beta"))),
            ),
        )

        parser.parseAndImport(booksDir, ImportStats())

        assertEquals(2, countSynthetic(tx))
        assertEquals(listOf("Alpha", "Beta"), syntheticTitles(tx))
        assertEquals(0, stagingTableCount(tx))
    }

    private data class InpBook(
        val id: Int,
        val title: String,
        val author: String = "Author,Test",
        val genre: String = "fiction",
        val deleted: Boolean = false,
    )

    private fun book(
        id: Int,
        title: String,
        author: String = "Author,Test",
        genre: String = "fiction",
    ) = InpBook(
        id = id,
        title = title,
        author = author,
        genre = genre,
    )

    private fun writeInpx(dir: Path, books: List<InpBook>) {
        writeInpxEntries(dir, listOf("books.inp" to books))
    }

    private fun writeInpxEntries(dir: Path, entries: List<Pair<String, List<InpBook>>>) {
        val inpxFile = dir.resolve("flibusta_fb2_local.inpx").toFile()
        ZipOutputStream(inpxFile.outputStream()).use { zip ->
            entries.forEach { (entryName, books) ->
                val lines = books.joinToString("\n", transform = ::rawBookLine)
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(lines.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun rawBookLine(b: InpBook): String = listOf(
        b.author,
        b.genre,
        b.title,
        "",
        "",
        b.id.toString(),
        "1024",
        b.id.toString(),
        if (b.deleted) "1" else "0",
        "fb2",
        "2024-01-01",
        "ru",
    ).joinToString("\u0004")

    private fun writeRawInpxEntries(dir: Path, entries: List<Pair<String, List<String>>>) {
        val inpxFile = dir.resolve("flibusta_fb2_local.inpx").toFile()
        ZipOutputStream(inpxFile.outputStream()).use { zip ->
            entries.forEach { (entryName, lines) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private suspend fun countSynthetic(tx: TransactionProvider): Int =
        tx.transaction(READ_ONLY) {
            useTx { dsl ->
                dsl.selectCount()
                    .from(BOOKS)
                    .where(BOOKS.ID.ge(SYNTHETIC_ID_FLOOR))
                    .fetchOne(0, Int::class.java) ?: 0
            }
        }

    private suspend fun syntheticTitles(tx: TransactionProvider): List<String> =
        tx.transaction(READ_ONLY) {
            useTx { dsl ->
                dsl.select(BOOKS.TITLE)
                    .from(BOOKS)
                    .where(BOOKS.ID.ge(SYNTHETIC_ID_FLOOR))
                    .orderBy(BOOKS.ID)
                    .fetch(BOOKS.TITLE)
                    .filterNotNull()
            }
        }

    private suspend fun bookExists(tx: TransactionProvider, bookId: Int): Boolean =
        tx.transaction(READ_ONLY) {
            useTx { dsl ->
                (dsl.selectCount()
                    .from(BOOKS)
                    .where(BOOKS.ID.eq(bookId))
                    .fetchOne(0, Int::class.java) ?: 0) > 0
            }
        }

    private suspend fun archivePathForBook(tx: TransactionProvider, bookId: Int): String? =
        tx.transaction(READ_ONLY) {
            useTx { dsl ->
                dsl.select(BOOKS.ARCHIVE_PATH)
                    .from(BOOKS)
                    .where(BOOKS.ID.eq(bookId))
                    .fetchOne(BOOKS.ARCHIVE_PATH)
            }
        }

    private suspend fun authorsForBook(tx: TransactionProvider, bookId: Int): List<String> =
        tx.transaction(READ_ONLY) {
            useTx { dsl ->
                dsl.select(AUTHORS.FULL_NAME)
                    .from(AUTHORS)
                    .join(BOOK_AUTHORS).on(AUTHORS.ID.eq(BOOK_AUTHORS.AUTHOR_ID))
                    .where(BOOK_AUTHORS.BOOK_ID.eq(bookId))
                    .orderBy(AUTHORS.FULL_NAME)
                    .fetch(AUTHORS.FULL_NAME)
                    .filterNotNull()
            }
        }

    private suspend fun genresForBook(tx: TransactionProvider, bookId: Int): List<String> =
        tx.transaction(READ_ONLY) {
            useTx { dsl ->
                dsl.select(GENRES.NAME)
                    .from(GENRES)
                    .join(BOOK_GENRES).on(GENRES.ID.eq(BOOK_GENRES.GENRE_ID))
                    .where(BOOK_GENRES.BOOK_ID.eq(bookId))
                    .orderBy(GENRES.NAME)
                    .fetch(GENRES.NAME)
                    .filterNotNull()
            }
        }

    private suspend fun insertEnrichment(tx: TransactionProvider, bookId: Int, annotation: String) {
        tx.transaction(READ_WRITE) {
            useTx { dsl ->
                dsl.insertInto(BOOK_ENRICHMENT)
                    .set(BOOK_ENRICHMENT.BOOK_ID, bookId)
                    .set(BOOK_ENRICHMENT.ANNOTATION, annotation)
                    .set(BOOK_ENRICHMENT.STATUS, "DONE")
                    .execute()
            }
        }
    }

    private suspend fun enrichmentAnnotation(tx: TransactionProvider, bookId: Int): String? =
        tx.transaction(READ_ONLY) {
            useTx { dsl ->
                dsl.select(BOOK_ENRICHMENT.ANNOTATION)
                    .from(BOOK_ENRICHMENT)
                    .where(BOOK_ENRICHMENT.BOOK_ID.eq(bookId))
                    .fetchOne(BOOK_ENRICHMENT.ANNOTATION)
            }
        }

    private suspend fun stagingTableCount(tx: TransactionProvider): Int =
        tx.transaction(READ_ONLY) {
            useTx { dsl ->
                dsl.resultQuery("SELECT COUNT(*) AS c FROM sqlite_master WHERE type = 'table' AND name LIKE 'IMPORT_%'")
                    .fetchOne("c", Int::class.java) ?: 0
            }
        }

    private companion object {
        private const val SYNTHETIC_ID_FLOOR = 9000
    }
}
