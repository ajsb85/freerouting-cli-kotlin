package app.freerouting.io.specctra.parser

import app.freerouting.logger.FRLogger
import java.io.IOException

/**
 * Keywords defining a scope object
 */
open class ScopeKeyword(p_name: String) : Keyword(p_name) {

    /**
     * Reads the next scope of this keyword from dsn file.
     */
    open fun read_scope(p_par: ReadScopeParameter): Boolean {
        var next_token: Any? = null
        while (true) {
            val prev_token = next_token
            try {
                next_token = p_par.scanner.next_token()
            } catch (e: IOException) {
                FRLogger.error("ScopeKeyword.read_scope: IO error scanning file", e)
                return false
            }
            if (next_token == null) {
                // end of file
                return true
            }
            if (next_token === CLOSED_BRACKET) {
                // end of scope
                break
            }

            if (prev_token === OPEN_BRACKET) {
                // a new scope is expected
                if (next_token is ScopeKeyword) {
                    // read the next scope, which is the "structure" part of the DSN file
                    if (!next_token.read_scope(p_par)) {
                        return false
                    }
                } else {
                    // skip unknown scope
                    skip_scope(p_par.scanner)
                }
            }
        }
        return true
    }

    companion object {
        /**
         * Skips the current scope while reading a dsn file. Returns false, if no legal scope was found.
         */
        @JvmStatic
        fun skip_scope(p_scanner: IJFlexScanner): Boolean {
            var open_bracket_count = 1
            while (open_bracket_count > 0) {
                p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                val curr_token: Any?
                try {
                    curr_token = p_scanner.next_token()
                } catch (e: Exception) {
                    FRLogger.error("ScopeKeyword.skip_scope: Error while scanning file", e)
                    return false
                }
                if (curr_token == null) {
                    return false // end of file
                }
                if (curr_token === OPEN_BRACKET) {
                    ++open_bracket_count
                } else if (curr_token === CLOSED_BRACKET) {
                    --open_bracket_count
                }
            }
            return true
        }
    }
}
