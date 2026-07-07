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
 * Hard cap on bytes read from a single FB2 entry. Bounds per-request memory so a
 * decompression bomb (a tiny deflated entry that inflates to gigabytes) or a
 * pathologically large file cannot OOM the process: we inflate at most this much and
 * reject anything larger. Comfortably above real FB2 files, which top out at a few
 * tens of MB even with embedded cover art and illustrations.
 */
internal const val MAX_FB2_BYTES = 48 * 1024 * 1024

/**
 * Reads an FB2 byte stream and returns it as cleaned text ready for XML parsing:
 * picks the charset from a UTF-16 BOM, else UTF-8, else windows-1251, then strips
 * XML-unsafe characters and neutralizes non-XML entity references. FB2 files in the
 * Flibusta dump are mostly UTF-8 or windows-1251, with a few UTF-16 and stray control
 * bytes, and HTML-converted ones sprinkle in named entities like `&nbsp;`.
 *
 * Returns text rather than a re-encoded byte stream on purpose: the caller must parse
 * it through a character Reader so StAX ignores the `encoding=` in the prolog. Handing
 * the parser UTF-8 bytes that still declare windows-1251 makes it re-decode them as
 * windows-1251 and produce mojibake. Returns null when the stream cannot be read,
 * exceeds [MAX_FB2_BYTES], or decodes to blank (e.g. an empty file), so the caller
 * skips parsing it.
 */
internal fun decodeFb2Content(input: InputStream): String? {
    return try {
        // Read at most MAX_FB2_BYTES + 1: if the extra byte materializes the entry is
        // over budget, so we reject it without ever inflating the whole thing.
        val bytes = input.readNBytes(MAX_FB2_BYTES + 1)
        if (bytes.size > MAX_FB2_BYTES) {
            return null
        }
        val content = when {
            bytes.startsWith(0xFF, 0xFE) -> String(bytes, Charsets.UTF_16LE)
            bytes.startsWith(0xFE, 0xFF) -> String(bytes, Charsets.UTF_16BE)
            isValidUtf8(bytes) -> String(bytes, Charsets.UTF_8)
            else -> String(bytes, Charset.forName("windows-1251"))
        }
        content
            .replace(XML_UNSAFE_CHARS, "")
            .let(::neutralizeNonXmlEntities)
            .takeIf(String::isNotBlank)
    } catch (_: Exception) {
        null
    }
}

/** The only named entities XML predefines; every other `&name;` needs a DTD to be legal. */
private val XML_PREDEFINED_ENTITIES = setOf("lt", "gt", "amp", "apos", "quot")

/**
 * Common HTML named entities that show up in FB2 files converted from HTML. Their
 * replacements are plain characters (never `<`, `>`, or `&`), so substituting them into
 * XML text is safe and needs no re-escaping.
 */
private val HTML_ENTITIES = mapOf(
    "nbsp" to "\u00A0", "shy" to "\u00AD",
    "mdash" to "\u2014", "ndash" to "\u2013", "minus" to "\u2212",
    "hellip" to "\u2026",
    "laquo" to "\u00AB", "raquo" to "\u00BB",
    "ldquo" to "\u201C", "rdquo" to "\u201D", "lsquo" to "\u2018", "rsquo" to "\u2019",
    "bdquo" to "\u201E", "sbquo" to "\u201A",
    "bull" to "\u2022", "middot" to "\u00B7", "sect" to "\u00A7", "para" to "\u00B6",
    "dagger" to "\u2020", "Dagger" to "\u2021",
    "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122",
    "deg" to "\u00B0", "plusmn" to "\u00B1", "times" to "\u00D7", "divide" to "\u00F7",
    "frac12" to "\u00BD", "frac14" to "\u00BC", "frac34" to "\u00BE",
    "euro" to "\u20AC", "pound" to "\u00A3", "cent" to "\u00A2", "yen" to "\u00A5",
    "prime" to "\u2032", "Prime" to "\u2033",
)

/** Matches an entity-like token `&name;` / `&#123;` / `&#xAB;` (name captured), or a bare `&`. */
private val ENTITY_REF = Regex("""&(#[0-9]+|#[xX][0-9A-Fa-f]+|[A-Za-z][A-Za-z0-9]*);|&""")

/**
 * Makes FB2 text safe for a strict (DTD-less) StAX parser. With `SUPPORT_DTD=false` the
 * parser throws on the first undeclared entity, so a single `&nbsp;` in the body aborts the
 * parse and silently truncates the rendered book (and, since `<binary>` images come after
 * the body, drops cover/inline images too). This rewrites every ampersand-token so the parser
 * only ever sees a valid reference: XML's five predefined names and numeric refs pass through,
 * known HTML entities become their character, and anything else \u2014 an unknown name or a bare
 * `&` \u2014 is escaped to `&amp;` so it renders literally instead of aborting the parse.
 */
private fun neutralizeNonXmlEntities(content: String): String {
    if ('&' !in content) return content
    return ENTITY_REF.replace(content) { match ->
        if (match.value == "&") return@replace "&amp;"
        val name = match.groupValues[1]
        when {
            name.startsWith("#") -> match.value
            name in XML_PREDEFINED_ENTITIES -> match.value
            else -> HTML_ENTITIES[name] ?: "&amp;$name;"
        }
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
