package io.heapy.kotbusta.service

import io.heapy.kotbusta.model.KindleUploadSourceFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class KindleUploadStorageTest {
    @Test
    fun `store writes the file and reports its size`(@TempDir dir: Path) {
        val storage = storage(dir, maxUploadBytes = 1024)

        val stored = storage.store("hello".byteInputStream(), "epub")

        assertEquals(5, stored.sizeBytes)
        assertTrue(stored.fileName.endsWith(".epub"))
        assertEquals("hello", storage.resolve(stored.fileName).readText())
    }

    @Test
    fun `store rejects a file over the limit and leaves nothing behind`(@TempDir dir: Path) {
        val storage = storage(dir, maxUploadBytes = 4)

        val _ = assertThrows<UploadTooLargeException> {
            storage.store("too many bytes".byteInputStream(), "pdf")
        }

        assertEquals(emptyList<Path>(), dir.listDirectoryEntries())
    }

    @Test
    fun `resolve refuses a name that carries a path`(@TempDir dir: Path) {
        val storage = storage(dir, maxUploadBytes = 1024)

        val _ = assertThrows<IllegalArgumentException> {
            storage.resolve("../escaped.epub")
        }
    }

    @Test
    fun `detect accepts content that matches the extension`() {
        assertEquals(
            KindleUploadSourceFormat.EPUB,
            detectUploadFormat("book.epub", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)),
        )
        assertEquals(
            KindleUploadSourceFormat.PDF,
            detectUploadFormat("book.pdf", "%PDF-1.7 rest".toByteArray()),
        )
        assertEquals(
            KindleUploadSourceFormat.FB2,
            detectUploadFormat("book.fb2", """<?xml version="1.0"?><FictionBook>""".toByteArray()),
        )
    }

    @Test
    fun `detect accepts fb2 written in utf-16`() {
        val utf16 = UTF16LE_BOM + """<?xml version="1.0"?><FictionBook>""".toByteArray(Charsets.UTF_16LE)

        assertEquals(KindleUploadSourceFormat.FB2, detectUploadFormat("book.fb2", utf16))
    }

    @Test
    fun `a limit that cannot be stored is refused`(@TempDir dir: Path) {
        val _ = assertThrows<IllegalArgumentException> {
            storage(dir, maxUploadBytes = Int.MAX_VALUE.toLong() + 1)
        }
    }

    @Test
    fun `detect rejects content that contradicts the extension`() {
        assertNull(detectUploadFormat("book.epub", "%PDF-1.7".toByteArray()))
        assertNull(detectUploadFormat("book.pdf", byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertNull(detectUploadFormat("book.fb2", byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
    }

    @Test
    fun `detect rejects an extension that is not supported`() {
        assertNull(detectUploadFormat("book.mobi", byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertNull(detectUploadFormat("book", byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
    }

    @Test
    fun `sanitize keeps a usable name and drops directories`() {
        assertEquals("book.epub", sanitizeUploadFileName("../../etc/book.epub"))
        assertEquals("my_book.epub", sanitizeUploadFileName("my book.epub"))
        assertEquals("book.epub", sanitizeUploadFileName("""C:\temp\book.epub"""))
        assertEquals("upload", sanitizeUploadFileName("   "))
    }

    @Test
    fun `sweep deletes only old files that nothing refers to`(@TempDir dir: Path) {
        val storage = storage(dir, maxUploadBytes = 1024)
        val referenced = storage.store("a".byteInputStream(), "epub")
        val orphan = storage.store("b".byteInputStream(), "epub")
        val fresh = storage.store("c".byteInputStream(), "epub")
        age(dir.resolve(referenced.fileName))
        age(dir.resolve(orphan.fileName))

        val deleted = storage.sweepOrphans(
            referenced = setOf(referenced.fileName),
            olderThan = Clock.System.now() - 1.hours,
            partialsOlderThan = Clock.System.now() - 24.hours,
        )

        assertEquals(1, deleted)
        assertTrue(dir.resolve(referenced.fileName).exists())
        assertTrue(dir.resolve(fresh.fileName).exists())
        assertFalse(dir.resolve(orphan.fileName).exists())
    }

    @Test
    fun `sweep never touches a file this class did not store`(@TempDir dir: Path) {
        val storage = storage(dir, maxUploadBytes = 1024)
        val foreign = dir.resolve("kotbusta.db")
        foreign.writeText("database")
        val leftover = dir.resolve("11111111-2222-3333-4444-555555555555.epub.part")
        leftover.writeText("interrupted upload")
        age(foreign)
        age(leftover)

        val deleted = storage.sweepOrphans(
            referenced = emptySet(),
            olderThan = Clock.System.now() - 1.hours,
            partialsOlderThan = Clock.System.now() - 1.hours,
        )

        assertEquals(1, deleted)
        assertTrue(foreign.exists())
        assertFalse(leftover.exists())
    }

    @Test
    fun `sweep spares a partial that may still be streaming`(@TempDir dir: Path) {
        val storage = storage(dir, maxUploadBytes = 1024)
        val partial = dir.resolve("11111111-2222-3333-4444-555555555555.epub.part")
        partial.writeText("still arriving")
        age(partial)

        val deleted = storage.sweepOrphans(
            referenced = emptySet(),
            olderThan = Clock.System.now() - 1.hours,
            partialsOlderThan = Clock.System.now() - 24.hours,
        )

        assertEquals(0, deleted)
        assertTrue(partial.exists())
    }

    @Test
    fun `sanitize keeps the extension of a very long name`() {
        val sanitized = sanitizeUploadFileName("a".repeat(300) + ".epub")

        assertTrue(sanitized.endsWith(".epub"))
        assertEquals(KindleUploadSourceFormat.EPUB, detectUploadFormat(sanitized, EPUB_HEADER))
    }

    private fun storage(
        dir: Path,
        maxUploadBytes: Long,
    ) = KindleUploadStorage(
        uploadPath = dir,
        maxUploadBytes = maxUploadBytes,
    )

    private val UTF16LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val EPUB_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    private fun age(path: Path) {
        val old = Clock.System.now() - 2.hours
        val _ = Files.setLastModifiedTime(path, FileTime.fromMillis(old.toEpochMilliseconds()))
    }
}
