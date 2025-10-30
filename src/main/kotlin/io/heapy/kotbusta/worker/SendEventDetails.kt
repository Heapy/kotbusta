package io.heapy.kotbusta.worker

import kotlinx.serialization.Serializable

@Serializable
sealed interface SendEventDetails

@Serializable
data class QueuedEventDetails(
    val bookId: Int,
    val deviceId: Int,
    val format: String,
) : SendEventDetails

@Serializable
data class RetryEventDetails(
    val attempt: Int,
    val nextRunAt: String,
    val error: String,
) : SendEventDetails

@Serializable
data class SentEventDetails(
    val messageId: String,
) : SendEventDetails

@Serializable
data class FailedEventDetails(
    val reason: String,
    val error: String? = null,
) : SendEventDetails
