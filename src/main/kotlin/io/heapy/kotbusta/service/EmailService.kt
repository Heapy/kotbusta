package io.heapy.kotbusta.service

import aws.sdk.kotlin.services.sesv2.SesV2Client
import aws.sdk.kotlin.services.sesv2.model.Destination
import aws.sdk.kotlin.services.sesv2.model.EmailContent
import aws.sdk.kotlin.services.sesv2.model.RawMessage
import aws.sdk.kotlin.services.sesv2.model.SendEmailRequest
import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.util.WHITESPACE_RUN
import io.heapy.kotbusta.util.asciiFallbackFileName
import io.heapy.kotbusta.util.attachmentContentDisposition
import io.heapy.kotbusta.util.takeCodePointSafe
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed interface EmailResult {
    data class Success(val messageId: String) : EmailResult
    data class RetryableFailure(val error: String) : EmailResult
    data class PermanentFailure(val error: String) : EmailResult
}

interface EmailService {
    suspend fun sendBookToKindle(
        recipientEmail: String,
        bookFile: File,
        bookTitle: String,
        attachmentFileName: String,
        format: String,
    ): EmailResult
}

class SesEmailService(
    private val sesClient: SesV2Client,
    private val senderEmail: String,
    private val maxRawMessageBytes: Long = SES_MAX_RAW_MESSAGE_BYTES,
) : EmailService {
    override suspend fun sendBookToKindle(
        recipientEmail: String,
        bookFile: File,
        bookTitle: String,
        attachmentFileName: String,
        format: String,
    ): EmailResult {
        return try {
            val title = sanitizeBookTitle(bookTitle)

            // Kindle's email service auto-extracts a .zip and converts the EPUB
            // inside, so we ship the book zipped. EPUB is already a compressed
            // container, so this only shaves a little off the wire — but it lets
            // the size limit be enforced on the exact bytes we send.
            val zipBytes = zipSingleEntry(entryName = attachmentFileName, content = bookFile.readBytes())

            val rawEmail = buildRawEmail(
                from = senderEmail,
                to = recipientEmail,
                subject = "Your book: $title",
                body = "Please find your requested book attached.",
                attachmentBytes = zipBytes,
                attachmentName = "$attachmentFileName.zip",
                mimeType = "application/zip",
            )

            // Reject oversized messages before hitting SES: the API rejects them
            // anyway (with a cryptic byte-count error) after we've uploaded the
            // whole thing, so catching it here saves the upload and yields a
            // message the user can act on (it flows to the send history as
            // `lastError`).
            oversizedBookError(zipBytes.size, rawEmail.size, maxRawMessageBytes)?.let { error ->
                log.warn("Skipping oversized email to $recipientEmail: $error")
                return EmailResult.PermanentFailure(error)
            }

            val response = sesClient.sendEmail(
                SendEmailRequest {
                    fromEmailAddress = senderEmail
                    destination = Destination {
                        toAddresses = listOf(recipientEmail)
                    }
                    content = EmailContent {
                        raw = RawMessage {
                            data = rawEmail
                        }
                    }
                },
            )

            log.info("Email sent successfully to $recipientEmail, messageId: ${response.messageId}")
            EmailResult.Success(response.messageId ?: "")
        } catch (e: Exception) {
            log.error("Failed to send email to $recipientEmail", e)
            classifyError(e)
        }
    }

    private fun classifyError(exception: Exception): EmailResult {
        val errorMessage = exception.message ?: exception.toString()

        // Check for retryable errors
        val retryablePatterns = listOf(
            "throttl",
            "rate limit",
            "service unavailable",
            "timeout",
            "connection",
            "temporarily",
        )

        if (retryablePatterns.any {
                errorMessage.contains(
                    it,
                    ignoreCase = true,
                )
            }) {
            return EmailResult.RetryableFailure(errorMessage)
        }

        // All other errors are considered permanent
        return EmailResult.PermanentFailure(errorMessage)
    }

    private companion object : Logger()
}

private val CONTROL_OR_QUOTE = Regex("""[\p{Cntrl}"]+""")
private const val MAX_SANITIZED_BOOK_TITLE_LENGTH = 100

/**
 * SES v2's default maximum raw message size (after base64 encoding), in bytes.
 * The legacy SendRawEmail (v1) API capped this at 10 MiB, which rejected larger
 * books; v2's SendEmail accepts up to 40 MiB by default. This is the hard wall:
 * a 45 MB zip would base64-encode to ~61 MB and be rejected, so the largest book
 * we can actually email works out to ~30 MB after compression (see below).
 */
internal const val SES_MAX_RAW_MESSAGE_BYTES: Long = 40L * 1024 * 1024

/**
 * base64 (+ MIME line breaks) inflates an attachment by ~4/3; the rest of the
 * message (headers, body) adds only a few hundred bytes. Used to express the
 * message-size ceiling back to the user in terms of the book's compressed size.
 */
private const val BASE64_MESSAGE_INFLATION = 1.37

/**
 * Zips [content] into a single-entry archive named [entryName], using plain
 * DEFLATE at maximum level (no zopfli). Kindle's email service auto-extracts the
 * archive and converts the EPUB inside.
 */
internal fun zipSingleEntry(entryName: String, content: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.setLevel(Deflater.BEST_COMPRESSION)
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content)
        zip.closeEntry()
    }
    return out.toByteArray()
}

/**
 * Returns a user-facing error when a message of [rawMessageBytes] would exceed
 * [maxRawMessageBytes] (SES's hard ceiling), or null when it fits. The message is
 * phrased in terms the reader understands — the book's [compressedBytes] size and
 * the largest book that can be emailed — and is surfaced in the send history.
 */
internal fun oversizedBookError(
    compressedBytes: Int,
    rawMessageBytes: Int,
    maxRawMessageBytes: Long,
): String? {
    if (rawMessageBytes <= maxRawMessageBytes) return null
    val maxCompressed = (maxRawMessageBytes / BASE64_MESSAGE_INFLATION).toLong()
    return "Book is too large to send to Kindle by email: it is " +
        "${mebibytes(compressedBytes.toLong())} MB compressed, over the " +
        "~${mebibytes(maxCompressed)} MB limit for email delivery."
}

private fun mebibytes(bytes: Long): String =
    "%.1f".format(Locale.ROOT, bytes / 1024.0 / 1024.0)

internal fun sanitizeBookTitle(title: String): String =
    title
        .replace(CONTROL_OR_QUOTE, " ")
        .replace(WHITESPACE_RUN, " ")
        .trim()
        .takeCodePointSafe(MAX_SANITIZED_BOOK_TITLE_LENGTH)
        .trim()
        .ifBlank { "book" }

internal fun buildRawEmail(
    from: String,
    to: String,
    subject: String,
    body: String,
    attachmentBytes: ByteArray,
    attachmentName: String,
    mimeType: String,
): ByteArray {
    require(to.none { it.isISOControl() }) { "recipient address must not contain CR or LF" }
    require(from.none { it.isISOControl() }) { "sender address must not contain CR or LF" }

    val boundary = "----=_Part_${System.currentTimeMillis()}"
    val fallbackName = asciiFallbackFileName(attachmentName)
    val disposition = attachmentContentDisposition(attachmentName, includeAsciiFallback = false)

    val out = ByteArrayOutputStream()
    fun write(text: String) = out.write(text.toByteArray())
    fun line(text: String = "") {
        // RFC 5322 requires CRLF line endings in raw messages.
        write(text)
        write("\r\n")
    }

    // Headers
    line("From: $from")
    line("To: $to")
    line("Subject: ${encodeMimeHeaderValue(subject)}")
    line("MIME-Version: 1.0")
    line("Content-Type: multipart/mixed; boundary=\"$boundary\"")
    line()

    // Body part
    line("--$boundary")
    line("Content-Type: text/plain; charset=UTF-8")
    line("Content-Transfer-Encoding: 7bit")
    line()
    line(body)
    line()

    // Attachment part
    line("--$boundary")
    line("Content-Type: $mimeType; name=\"$fallbackName\"")
    line("Content-Transfer-Encoding: base64")
    line("Content-Disposition: $disposition")
    line()
    // Stream-encode the attachment straight into `out` to avoid holding a
    // separate full-size base64 String. wrap(...).close() flushes the final
    // base64 group; ByteArrayOutputStream.close() is a no-op, so `out` stays
    // usable afterwards.
    Base64.getMimeEncoder().wrap(out).use { it.write(attachmentBytes) }
    line()  // terminate the final base64 line
    line()  // blank line before the closing boundary

    // End boundary
    line("--$boundary--")

    return out.toByteArray()
}

/**
 * MIME headers are ASCII-only; Kindle shows raw UTF-8 header bytes as
 * Latin-1 mojibake. Non-ASCII values (and control characters, which would
 * otherwise allow header injection) are wrapped in RFC 2047 B-encoded words
 * of at most 75 characters, folded with CRLF + space.
 */
internal fun encodeMimeHeaderValue(text: String): String {
    if (text.all { it.code in 0x20..0x7E }) return text

    // "=?UTF-8?B?" + base64 + "?=" must fit in 75 chars, so at most
    // 60 base64 chars = 45 input bytes per encoded word.
    val maxBytesPerWord = 45
    val chunks = mutableListOf<String>()
    val chunk = StringBuilder()
    var chunkBytes = 0
    var i = 0
    while (i < text.length) {
        val codePoint = text.codePointAt(i)
        val chars = Character.toChars(codePoint)
        val byteCount = String(chars).toByteArray().size
        if (chunkBytes + byteCount > maxBytesPerWord && chunk.isNotEmpty()) {
            chunks += chunk.toString()
            chunk.setLength(0)
            chunkBytes = 0
        }
        chunk.append(chars)
        chunkBytes += byteCount
        i += chars.size
    }
    if (chunk.isNotEmpty()) chunks += chunk.toString()

    return chunks.joinToString("\r\n ") {
        "=?UTF-8?B?${Base64.getEncoder().encodeToString(it.toByteArray())}?="
    }
}
