package app.freerouting.datastructures

import app.freerouting.logger.FRLogger
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Describes legal identifiers together with the character used for string quotes.
 */
class IdentifierType(
    private val reserved_chars: Array<String>,
    private val string_quote: String
) {

    /**
     * Writes p_name after putting it into quotes, if it contains reserved characters or blanks.
     */
    fun write(p_name: String, p_file: OutputStreamWriter) {
        var name = p_name
        // remove the double quotes from the identifiers
        while (name.length > 2 && name.startsWith('"') && name.endsWith('"')) {
            name = name.substring(1, name.length - 2)
        }

        try {
            // if the name contains our quote character, we must remove it
            if (name.contains(string_quote)) {
                name = name.replace(string_quote, "")
            }

            var need_quotes = false
            // if the name contains a reserved character, we must put it into quotes
            for (reserved_char in reserved_chars) {
                if (name.contains(reserved_char)) {
                    need_quotes = true
                    break
                }
            }

            if (!need_quotes) {
                // if the name contains a non-ASCII character, we must put it into quotes
                for (ch in name.toByteArray(StandardCharsets.UTF_8)) {
                    if (ch <= 0) {
                        need_quotes = true
                        break
                    }
                }
            }

            if (!need_quotes) {
                if (name.matches("^-?\\d.*".toRegex())) {
                    need_quotes = true
                }
            }
            if (need_quotes) {
                name = quote(name)
            }
            p_file.write(name)
        } catch (_: IOException) {
            FRLogger.warn("IdentifierType.write: unable to write to file")
        }
    }

    /**
     * Looks, if p_string does not contain reserved characters or blanks.
     */
    private fun is_legal(p_string: String?): Boolean {
        if (p_string == null) {
            FRLogger.warn("IdentifierType.is_legal: p_string is null")
            return false
        }
        for (reserved_char in reserved_chars) {
            if (p_string.contains(reserved_char)) {
                return false
            }
        }
        return true
    }

    /**
     * Puts p_string into quotes.
     */
    private fun quote(p_string: String): String {
        return string_quote + p_string + string_quote
    }
}
