package app.freerouting.io.specctra.parser

import app.freerouting.board.BasicBoard
import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.settings.RouterSettings

/**
 * Default parameter type used while writing a Specctra dsn-file.
 */
class WriteScopeParameter(
    @JvmField val board: BasicBoard,
    @JvmField val autoroute_settings: RouterSettings?,
    @JvmField val file: IndentFileWriter,
    p_string_quote: String,
    @JvmField val coordinate_transform: CoordinateTransform,
    @JvmField val compat_mode: Boolean
) {
    @JvmField val identifier_type: IdentifierType

    init {
        val reservedChars = arrayOf("(", ")", " ", ";", "-", "_")
        identifier_type = IdentifierType(reservedChars, p_string_quote)
    }
}
