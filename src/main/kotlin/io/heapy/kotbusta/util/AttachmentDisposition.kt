package io.heapy.kotbusta.util

import io.ktor.http.ContentDisposition

/**
 * Builds an attachment Content-Disposition for [fileName].
 *
 * Pure-ASCII names get a plain `filename=`. A non-ASCII name always carries the
 * real name in an RFC 5987 `filename*`; [includeAsciiFallback] controls whether
 * a plain ASCII `filename=` fallback is sent alongside it. Kindle wants the
 * extended parameter ONLY (false) so mail parsers don't prefer the ASCII
 * fallback and show a transliterated title; HTTP downloads send both (true) so
 * clients without RFC 5987 support still get a usable name.
 */
internal fun attachmentContentDisposition(
    fileName: String,
    includeAsciiFallback: Boolean,
): ContentDisposition {
    val isAscii = fileName.all { it.code in 0x20..0x7E }
    if (isAscii) {
        return ContentDisposition.Attachment.withParameter(
            ContentDisposition.Parameters.FileName,
            fileName,
        )
    }
    val extendedOnly = ContentDisposition.Attachment.withParameter(
        ContentDisposition.Parameters.FileNameAsterisk,
        fileName,
    )
    return if (includeAsciiFallback) {
        ContentDisposition.Attachment
            .withParameter(ContentDisposition.Parameters.FileName, asciiFallbackFileName(fileName))
            .withParameter(ContentDisposition.Parameters.FileNameAsterisk, fileName)
    } else {
        extendedOnly
    }
}
