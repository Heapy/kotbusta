package io.heapy.kotbusta.ktor.routes.books

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadBookRouteTest {
    @Test
    fun `content disposition includes utf8 filename for russian downloads`() {
        val header = downloadContentDisposition("Преступление_и_наказание.fb2").toString()

        assertTrue(header.startsWith("attachment; filename=book.fb2; filename*=utf-8''"))
        assertTrue(header.contains("%D0%9F%D1%80%D0%B5%D1%81%D1%82%D1%83%D0%BF%D0%BB%D0%B5%D0%BD%D0%B8%D0%B5"))
    }

    @Test
    fun `content disposition keeps simple ascii filename without extended parameter`() {
        val header = downloadContentDisposition("Clean_Title.fb2").toString()

        assertEquals("attachment; filename=Clean_Title.fb2", header)
    }
}
