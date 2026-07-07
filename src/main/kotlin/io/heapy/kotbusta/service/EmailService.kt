package io.heapy.kotbusta.service

import aws.sdk.kotlin.services.ses.SesClient
import aws.sdk.kotlin.services.ses.model.RawMessage
import aws.sdk.kotlin.services.ses.model.SendRawEmailRequest
import io.heapy.komok.tech.logging.Logger
import io.heapy.kotbusta.util.asciiFallbackFileName
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

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
        format: String,
    ): EmailResult
}

class SesEmailService(
    private val sesClient: SesClient,
    private val senderEmail: String,
) : EmailService {
    override suspend fun sendBookToKindle(
        recipientEmail: String,
        bookFile: File,
        bookTitle: String,
        format: String,
    ): EmailResult {
        return try {
            val mimeType = when (format.uppercase()) {
                "EPUB" -> "application/epub+zip"
                else -> "application/octet-stream"
            }

            val title = sanitizeBookTitle(bookTitle)
            val rawEmail = buildRawEmail(
                from = senderEmail,
                to = recipientEmail,
                subject = "Your book: $title",
                body = "Please find your requested book attached.",
                attachmentBytes = bookFile.readBytes(),
                attachmentName = "$title.${format.lowercase()}",
                mimeType = mimeType,
            )

            val response = sesClient.sendRawEmail(
                SendRawEmailRequest {
                    source = senderEmail
                    destinations = listOf(recipientEmail)
                    rawMessage = RawMessage {
                        data = rawEmail
                    }
                },
            )

            log.info("Email sent successfully to $recipientEmail, messageId: ${response.messageId}")
            EmailResult.Success(response.messageId)
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
private val WHITESPACE = Regex("""\s+""")
private const val MAX_SANITIZED_BOOK_TITLE_LENGTH = 100

internal fun sanitizeBookTitle(title: String): String =
    title
        .replace(CONTROL_OR_QUOTE, " ")
        .replace(WHITESPACE, " ")
        .trim()
        .let { sanitizedTitle ->
            val truncated = sanitizedTitle.take(MAX_SANITIZED_BOOK_TITLE_LENGTH)
            if (truncated.lastOrNull()?.let { Character.isHighSurrogate(it) } == true) {
                truncated.dropLast(1)
            } else {
                truncated
            }.trim()
        }
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
    // For non-ASCII names send ONLY the RFC 2231 filename*. Common parsers
    // (Python's email, and likely Amazon's ingestion) prefer a plain
    // filename= when both are present, which would replace the real title
    // with the ASCII fallback; extended-only is what calibre sends to
    // Kindle and is known to decode correctly there.
    val disposition = if (attachmentName.all { it.code in 0x20..0x7E }) {
        """attachment; filename="$attachmentName""""
    } else {
        "attachment; filename*=${rfc2231FileName(attachmentName)}"
    }

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

/**
 * RFC 2231/5987 extended parameter value (`UTF-8''%D0%9C...`) carrying the
 * real Unicode file name alongside the ASCII fallback `filename`.
 */
internal fun rfc2231FileName(fileName: String): String =
    fileName.toByteArray().joinToString(separator = "", prefix = "UTF-8''") { byte ->
        val char = byte.toInt().toChar()
        if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "!#$&+-.^_`|~") {
            char.toString()
        } else {
            "%%%02X".format(byte.toInt() and 0xFF)
        }
    }
