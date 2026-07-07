package io.heapy.kotbusta.util

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

/** Matches a run of whitespace characters. */
internal val WHITESPACE_RUN = Regex("""\s+""")

/** Matches a run of underscores. */
internal val UNDERSCORE_RUN = Regex("""_+""")

/**
 * Characters dropped before XML parsing: control chars that are illegal in XML 1.0
 * (they break StAX), plus a byte-order mark — once decoded to text, a leading BOM
 * would be stray content ahead of the prolog for a Reader-based parser.
 */
private val XML_UNSAFE_CHARS = Regex("""[\x00-\x08\x0B\x0C\x0E-\x1F\x7F\uFEFF]""")

/**
 * Reads an FB2 byte stream and returns it as cleaned text ready for XML parsing:
 * picks the charset from a UTF-16 BOM, else UTF-8, else windows-1251, then strips
 * XML-unsafe characters. FB2 files in the Flibusta dump are mostly UTF-8 or
 * windows-1251, with a few UTF-16 and stray control bytes.
 *
 * Returns text rather than a re-encoded byte stream on purpose: the caller must parse
 * it through a character Reader so StAX ignores the `encoding=` in the prolog. Handing
 * the parser UTF-8 bytes that still declare windows-1251 makes it re-decode them as
 * windows-1251 and produce mojibake. Returns null when the stream cannot be read or
 * decodes to blank (e.g. an empty file), so the caller skips parsing it.
 */
internal fun decodeFb2Content(input: InputStream): String? {
    return try {
        val bytes = input.readAllBytes()
        val content = when {
            bytes.startsWith(0xFF, 0xFE) -> String(bytes, Charsets.UTF_16LE)
            bytes.startsWith(0xFE, 0xFF) -> String(bytes, Charsets.UTF_16BE)
            isValidUtf8(bytes) -> String(bytes, Charsets.UTF_8)
            else -> String(bytes, Charset.forName("windows-1251"))
        }
        content.replace(XML_UNSAFE_CHARS, "").takeIf(String::isNotBlank)
    } catch (_: Exception) {
        null
    }
}

private fun ByteArray.startsWith(b0: Int, b1: Int): Boolean =
    size >= 2 && this[0] == b0.toByte() && this[1] == b1.toByte()

/** Returns true when [bytes] decode as valid UTF-8. */
internal fun isValidUtf8(bytes: ByteArray): Boolean {
    return try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Takes at most [maxUtf16Units] UTF-16 code units, then drops a trailing
 * unpaired high surrogate so truncation never splits a surrogate pair.
 */
internal fun String.takeCodePointSafe(maxUtf16Units: Int): String {
    val truncated = take(maxUtf16Units)
    return if (truncated.lastOrNull()?.isHighSurrogate() == true) {
        truncated.dropLast(1)
    } else {
        truncated
    }
}
