package app.freerouting.board

import app.freerouting.core.RoutingJob
import app.freerouting.gui.FileFormat
import app.freerouting.interactive.HeadlessBoardManager
import app.freerouting.logger.FRLogger

/**
 * Utility class for loading boards from routing jobs.
 */
object BoardLoader {

    /**
     * Loads a board from a routing job's input if not already loaded.
     *
     * @param job The routing job to load the board from
     * @return true if board is loaded successfully, false otherwise
     */
    @JvmStatic
    fun loadBoardIfNeeded(job: RoutingJob): Boolean {
        // Check if board is already loaded
        if (job.board != null) {
            return true
        }

        // Check if input is available
        if (job.input == null) {
            FRLogger.error("Cannot load board: job has no input", null)
            return false
        }

        // Only DSN format is supported for now
        if (job.input.format != FileFormat.DSN) {
            FRLogger.error("Cannot load board: only DSN format is supported, got " + job.input.format, null)
            return false
        }

        // Load the board
        try {
            val boardManager = HeadlessBoardManager(job)
            boardManager.loadFromSpecctraDsn(
                job.input.data,
                null,
                ItemIdentificationNumberGenerator()
            )
            job.board = boardManager.get_routing_board()
            return job.board != null
        } catch (e: Exception) {
            FRLogger.error("Failed to load board from DSN file", e)
            return false
        }
    }
}
