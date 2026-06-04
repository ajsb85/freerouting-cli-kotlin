package app.freerouting.io.specctra.parser

import app.freerouting.board.BasicBoard
import app.freerouting.io.specctra.SesWriter
import app.freerouting.logger.FRLogger
import java.io.IOException
import java.io.OutputStream

/**
 * Methods to handle a Specctra session file.
 *
 * @deprecated Use [SesWriter] instead, which exposes a typed
 *             `throws IOException` API and has no dependency on
 *             `BoardManager` or any GUI class.
 */
@Deprecated("Use SesWriter instead")
class SpecctraSesFileWriter private constructor() {
    companion object {
        /**
         * Creates a Specctra session file to update the host system from the RoutingBoard.
         *
         * @deprecated Use [SesWriter.write] instead.
         */
        @JvmStatic
        @Deprecated("Use SesWriter.write instead")
        fun write(p_board: BasicBoard, p_output_stream: OutputStream, p_design_name: String): Boolean {
            return try {
                SesWriter.write(p_board, p_output_stream, p_design_name)
                true
            } catch (e: IOException) {
                FRLogger.error("unable to write session file", e)
                false
            }
        }
    }
}
