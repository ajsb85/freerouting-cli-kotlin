package app.freerouting.io.specctra.parser

import app.freerouting.datastructures.IndentFileWriter
import java.io.IOException

/**
 * Class for reading resolution scopes from dsn-files.
 */
class Unit : ScopeKeyword("unit") {

    override fun read_scope(p_par: ReadScopeParameter): Boolean {
        return false
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_file: IndentFileWriter, p_unit: app.freerouting.board.Unit) {
            p_file.new_line()
            p_file.write("(unit ")
            p_file.write(p_unit.toString())
            p_file.write(")")
        }
    }
}
