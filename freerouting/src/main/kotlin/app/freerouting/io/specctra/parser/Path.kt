package app.freerouting.io.specctra.parser

import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import java.io.IOException

/**
 * Class for writing path scopes from dsn-files.
 */
abstract class Path(
    p_layer: Layer,
    @JvmField val width: Double,
    @JvmField val coordinate_arr: DoubleArray
) : Shape(p_layer) {

    /**
     * Writes this path as a scope to an output dsn-file.
     */
    @Throws(IOException::class)
    abstract override fun write_scope(p_file: IndentFileWriter, p_identifier: IdentifierType)
}
