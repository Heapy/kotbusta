package io.heapy.kotbusta.service

import aws.sdk.kotlin.services.ses.SesClient
import aws.sdk.kotlin.services.ses.model.RawMessage
import aws.sdk.kotlin.services.ses.model.SendRawEmailRequest
import io.heapy.komok.tech.logging.Logger
import java.io.File
import java.util.*

sealed interface EmailResult {
    data class Success(val messageId: String) : EmailResult
    data class RetryableFailure(val error: String) : EmailResult
    data class PermanentFailure(val error: String) : EmailResult
}

class EmailService(
    private val sesClient: SesClient,
    private val senderEmail: String,
) {
    suspend fun sendBookToKindle(
        recipientEmail: String,
        bookFile: File,
        bookTitle: String,
        format: String,
    ): EmailResult {
        return try {
            val mimeType = when (format.uppercase()) {
                "EPUB" -> "application/epub+zip"
                "MOBI" -> "application/x-mobipocket-ebook"
                else -> "application/octet-stream"
            }

            val rawEmail = buildRawEmail(
                from = senderEmail,
                to = recipientEmail,
                subject = "Your book: $bookTitle",
                body = "Please find your requested book attached.",
                attachment = bookFile,
                attachmentName = "${bookTitle}.${format.lowercase()}",
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

    private fun buildRawEmail(
        from: String,
        to: String,
        subject: String,
        body: String,
        attachment: File,
        attachmentName: String,
        mimeType: String,
    ): ByteArray {
        val boundary = "----=_Part_${System.currentTimeMillis()}"

        val emailBuilder = StringBuilder()

        // Headers
        emailBuilder.appendLine("From: $from")
        emailBuilder.appendLine("To: $to")
        emailBuilder.appendLine("Subject: $subject")
        emailBuilder.appendLine("MIME-Version: 1.0")
        emailBuilder.appendLine("Content-Type: multipart/mixed; boundary=\"$boundary\"")
        emailBuilder.appendLine()

        // Body part
        emailBuilder.appendLine("--$boundary")
        emailBuilder.appendLine("Content-Type: text/plain; charset=UTF-8")
        emailBuilder.appendLine("Content-Transfer-Encoding: 7bit")
        emailBuilder.appendLine()
        emailBuilder.appendLine(body)
        emailBuilder.appendLine()

        // Attachment part
        emailBuilder.appendLine("--$boundary")
        emailBuilder.appendLine("Content-Type: $mimeType; name=\"$attachmentName\"")
        emailBuilder.appendLine("Content-Transfer-Encoding: base64")
        emailBuilder.appendLine("Content-Disposition: attachment; filename=\"$attachmentName\"")
        emailBuilder.appendLine()

        val encodedAttachment =
            Base64.getMimeEncoder().encodeToString(attachment.readBytes())
        emailBuilder.appendLine(encodedAttachment)
        emailBuilder.appendLine()

        // End boundary
        emailBuilder.appendLine("--$boundary--")

        return emailBuilder.toString().toByteArray()
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
