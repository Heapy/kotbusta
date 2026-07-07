package io.heapy.kotbusta.util

/** Matches a run of whitespace characters. */
internal val WHITESPACE_RUN = Regex("""\s+""")

/** Matches a run of underscores. */
internal val UNDERSCORE_RUN = Regex("""_+""")

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
