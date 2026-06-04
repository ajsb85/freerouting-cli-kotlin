package app.freerouting.io.specctra.parser

import app.freerouting.board.BasicBoard
import app.freerouting.io.specctra.SesImportSummary
import app.freerouting.io.specctra.SesReader
import app.freerouting.logger.FRLogger
import java.io.IOException
import java.io.InputStream

/**
 * Reads a Specctra session file (.ses) and imports the routing data (wires and
 * vias) into the board.
 *
 * @deprecated Use [SesReader] instead, which returns a typed
 *             [SesImportSummary] and throws [IOException] instead of
 *             returning a bare `boolean`.
 */
@Deprecated("Use SesReader instead")
class SesFileReader private constructor() {
    companion object {
        /**
         * Reads a SES file and imports the routing data into the board.
         *
         * @param p_session Input stream of the SES file
         * @param p_board   The board to import routing data into
         * @return true if successful, false if an error occurred
         * @deprecated Use [SesReader.read] instead.
         */
        @JvmStatic
        @Deprecated("Use SesReader.read instead")
        fun read(p_session: InputStream, p_board: BasicBoard): Boolean {
            return try {
                val summary = SesReader.read(p_session, p_board)
                summary.errorsEncountered == 0 || summary.wiresImported > 0 || summary.viasImported > 0
            } catch (e: IOException) {
                FRLogger.error("Unable to process SES file", e)
                false
            }
        }
    }
}
