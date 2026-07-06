package io.heapy.kotbusta.parser

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.TransactionType.READ_ONLY
import io.heapy.kotbusta.database.TransactionType.READ_WRITE
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.BOOKS
import io.heapy.kotbusta.jooq.tables.references.BOOK_ENRICHMENT
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

        writeInpx(booksDir, listOf(book(9001, "Alpha"), book(9002, "Beta"), book(9003, "Gamma")))
        parser.parseAndImport(booksDir, ImportStats())

        assertEquals(3, countSynthetic(tx))
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
    fun `failed staging load leaves live catalog unchanged`(
        applicationModule: ApplicationModule,
    ) = runBlocking {
        val tx = applicationModule.transactionProvider.value
        val parser = applicationModule.inpxParser.value
        val booksDir = Files.createTempDirectory("inpx-test-")

        writeInpx(booksDir, listOf(book(9001, "Alpha"), book(9002, "Beta")))
        parser.parseAndImport(booksDir, ImportStats())
        val before = syntheticTitles(tx)

        writeInpx(booksDir, listOf(book(9001, "Duplicate A"), book(9001, "Duplicate B")))
        assertThrows(Exception::class.java) {
            runBlocking {
                parser.parseAndImport(booksDir, ImportStats())
            }
        }

        assertEquals(before, syntheticTitles(tx))
        assertEquals(0, stagingTableCount(tx))
    }

    private data class InpBook(
        val id: Int,
        val title: String,
        val author: String = "Author,Test",
        val genre: String = "fiction",
        val deleted: Boolean = false,
    )

    private fun book(id: Int, title: String) = InpBook(id = id, title = title)

    private fun writeInpx(dir: Path, books: List<InpBook>) {
        val sep = ''
        val lines = books.joinToString("\n") { b ->
            listOf(
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
            ).joinToString(sep.toString())
        }
        val inpxFile = dir.resolve("flibusta_fb2_local.inpx").toFile()
        ZipOutputStream(inpxFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("books.inp"))
            zip.write(lines.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
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
