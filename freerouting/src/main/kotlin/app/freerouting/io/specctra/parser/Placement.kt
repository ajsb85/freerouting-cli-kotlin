package app.freerouting.io.specctra.parser

import java.io.IOException

/**
 * Class for writing placement scopes from dsn-files.
 */
class Placement : ScopeKeyword("placement") {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_par: WriteScopeParameter) {
            p_par.file.start_scope()
            p_par.file.write("placement")
            if (p_par.board.components.get_flip_style_rotate_first()) {
                p_par.file.new_line()
                p_par.file.write("(place_control (flip_style rotate_first))")
            }

            val packages = p_par.board.library.packages
            if (packages != null) {
                for (i in 1..packages.count()) {
                    val pkg = packages.get(i)
                    if (pkg != null) {
                        `Package`.write_placement_scope(p_par, pkg)
                    }
                }
            }
            p_par.file.end_scope()
        }
    }
}
