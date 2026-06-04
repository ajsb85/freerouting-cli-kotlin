package app.freerouting.io.specctra

import app.freerouting.board.BasicBoard
import java.io.IOException

/**
 * Sealed result type for all outcomes of a DSN read operation.
 *
 * <p>Callers should use a pattern-matching {@code switch} to handle every case exhaustively:
 */
sealed interface DsnReadResult {

    /**
     * Full board + metadata are available. The board is fully constructed and routable.
     *
     * @param warnings non-fatal issues encountered during loading (e.g. degenerate wires, duplicate
     *                 vias, missing nets). Never {@code null}; may be empty.
     */
    @JvmRecord
    data class Success(
        val board: BasicBoard?,
        val metadata: DsnMetadata?,
        val warnings: List<String>
    ) : DsnReadResult

    /**
     * The board was constructed but the {@code (structure (boundary ...))} (outline) scope was
     * absent from the DSN file. The board reference is still valid and may be used with caution.
     *
     * @param warnings non-fatal issues encountered during loading. Never {@code null}; may be empty.
     */
    @JvmRecord
    data class OutlineMissing(
        val board: BasicBoard?,
        val metadata: DsnMetadata?,
        val warnings: List<String>
    ) : DsnReadResult

    /**
     * The token stream did not conform to the expected Specctra DSN grammar.
     *
     * @param location a human-readable description of where in the file the error was detected
     *                 (e.g. the enclosing scope keyword such as {@code "(pcb"})
     * @param detail   a short description of the specific problem
     */
    @JvmRecord
    data class ParseError(
        val location: String,
        val detail: String
    ) : DsnReadResult

    /** An {@link IOException} occurred while reading the underlying stream. */
    @JvmRecord
    data class IoError(
        val cause: IOException
    ) : DsnReadResult
}
