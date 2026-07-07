package io.heapy.kotbusta.util

/**
 * Reduces [fileName] to a safe pure-ASCII name for clients that cannot handle
 * RFC 2231/5987 extended parameters. Non-ASCII names collapse to a generic
 * fallback, so callers should also send the real name via `filename*`.
 */
internal fun asciiFallbackFileName(fileName: String): String {
    val trimmed = fileName.trim()
    val extensionStart = trimmed.lastIndexOf('.')
        .takeIf { it > 0 && it < trimmed.lastIndex }
    val base = extensionStart?.let { trimmed.substring(0, it) } ?: trimmed
    val extension = extensionStart?.let { trimmed.substring(it + 1) }.orEmpty()

    val safeBase = base
        .map { char ->
            when {
                char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' -> char
                char == '.' || char == '-' -> char
                char == '_' || char.isWhitespace() -> '_'
                else -> '_'
            }
        }
        .joinToString("")
        .replace(UNDERSCORE_RUN, "_")
        .trim('.', '-', '_')
        .ifBlank { "book" }
        .take(100)

    val safeExtension = extension
        .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        .lowercase()
        .take(16)

    return if (safeExtension.isBlank()) safeBase else "$safeBase.$safeExtension"
}
