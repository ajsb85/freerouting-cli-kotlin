package app.freerouting.io.specctra.parser

import app.freerouting.logger.FRLogger
import java.io.IOException

/**
 * Class for reading place_control scopes from dsn-files.
 */
class PlaceControl : ScopeKeyword("place_control") {

    override fun read_scope(p_par: ReadScopeParameter): Boolean {
        var flip_style_rotate_first = false
        var next_token: Any? = null
        while (true) {
            val prev_token = next_token
            try {
                next_token = p_par.scanner.next_token()
            } catch (e: IOException) {
                FRLogger.error("PlaceControl.read_scope: IO error scanning file", e)
                return false
            }
            if (next_token == null) {
                FRLogger.warn("PlaceControl.read_scope: unexpected end of file at '${p_par.scanner.get_scope_identifier()}'")
                return false
            }
            if (next_token === Keyword.CLOSED_BRACKET) {
                // end of scope
                break
            }
            if (prev_token === Keyword.OPEN_BRACKET) {
                if (next_token === Keyword.FLIP_STYLE) {
                    flip_style_rotate_first = read_flip_style_rotate_first(p_par.scanner)
                }
            }
        }
        if (flip_style_rotate_first) {
            p_par.board_handling.get_routing_board()?.components?.set_flip_style_rotate_first(true)
        }
        return true
    }

    companion object {
        /**
         * Returns true, if rotate_first is read, else false.
         */
        @JvmStatic
        fun read_flip_style_rotate_first(p_scanner: IJFlexScanner): Boolean {
            try {
                var result = false
                var next_token = p_scanner.next_token()
                if (next_token === Keyword.ROTATE_FIRST) {
                    result = true
                }
                next_token = p_scanner.next_token()
                if (next_token !== Keyword.CLOSED_BRACKET) {
                    FRLogger.warn("Structure.read_flip_style: closing bracket expected at '${p_scanner.get_scope_identifier()}'")
                    return false
                }
                return result
            } catch (e: IOException) {
                FRLogger.error("Structure.read_flip_style: IO error scanning file", e)
                return false
            }
        }
    }
}
