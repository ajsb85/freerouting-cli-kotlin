package app.freerouting.io.specctra

import app.freerouting.board.BasicBoard
import app.freerouting.board.BoardObserverAdaptor
import app.freerouting.board.BoardObservers
import app.freerouting.board.ItemIdentificationNumberGenerator
import app.freerouting.datastructures.IdentificationNumberGenerator
import app.freerouting.io.specctra.parser.DsnFile
import app.freerouting.io.specctra.parser.Keyword
import app.freerouting.io.specctra.parser.ReadScopeParameter
import app.freerouting.io.specctra.parser.ScopeKeyword
import app.freerouting.io.specctra.parser.SpecctraDsnStreamReader
import app.freerouting.logger.FRLogger

import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Reads a Specctra DSN file and returns a fully constructed
 * [BasicBoard] wrapped in a typed [DsnReadResult].
 *
 * <p>This class has <em>no</em> dependency on {@link app.freerouting.interactive.BoardManager},
 * {@link app.freerouting.core.RoutingJob}, or any GUI class. Board construction happens
 * internally via an anonymous minimal shim embedded in {@link ReadScopeParameter}.
 *
 * <p>Replaces the read path previously found in
 * {@link app.freerouting.io.specctra.parser.DsnFile#read} (now {@link Deprecated}).
 */
class DsnReader private constructor() {

    companion object {

        /**
         * Reads a DSN stream and returns a fully constructed board or a typed failure.
         *
         * <p>The stream is <em>closed</em> by this method once reading completes (successfully or not).
         */
        @JvmStatic
        @JvmOverloads
        fun readBoard(
            inputStream: InputStream?,
            observers: BoardObservers? = null,
            idGenerator: IdentificationNumberGenerator? = null,
            designName: String? = null
        ): DsnReadResult {
            var obs = observers
            var idGen = idGenerator

            if (inputStream == null) {
                return DsnReadResult.ParseError("(pcb", "inputStream must not be null")
            }

            // Apply default implementations for nullable parameters so the board's
            // Communication object is fully initialised even in lightweight test scenarios.
            if (obs == null) {
                obs = BoardObserverAdaptor()
            }
            if (idGen == null) {
                idGen = ItemIdentificationNumberGenerator()
            }

            val scanner = SpecctraDsnStreamReader(inputStream)

            // -----------------------------------------------------------------------
            // Validate the "(pcb <name>" header — identical check to DsnFile.read
            // -----------------------------------------------------------------------
            var pcbTokenName: String? = null
            for (i in 0 until 3) {
                val token: Any?
                try {
                    token = scanner.next_token()
                } catch (e: IOException) {
                    closeQuietly(inputStream)
                    return DsnReadResult.IoError(e)
                }
                var ok = true
                if (i == 0) {
                    ok = (token === Keyword.OPEN_BRACKET)
                } else if (i == 1) {
                    ok = (token === Keyword.PCB_SCOPE)
                    // switch the scanner to NAME mode so the pcb-name token is consumed cleanly
                    scanner.yybegin(SpecctraDsnStreamReader.NAME)
                } else {
                    // i == 2: the design name string immediately following "(pcb"
                    if (token is String) {
                        pcbTokenName = token
                    }
                }
                if (!ok) {
                    closeQuietly(inputStream)
                    return DsnReadResult.ParseError(
                        "(pcb",
                        "Not a Specctra DSN file: expected '(pcb <name>' header"
                    )
                }
            }

            // Resolve the effective design name for log messages:
            // prefer the caller-supplied filename, then the pcb-name token, then a fallback.
            val effectiveDesignName: String = if (!designName.isNullOrBlank()) {
                try {
                    Path.of(designName).fileName.toString()
                } catch (_: Exception) {
                    designName
                }
            } else if (!pcbTokenName.isNullOrBlank()) {
                pcbTokenName
            } else {
                "unknown"
            }

            // -----------------------------------------------------------------------
            // Parse the body — board is constructed inside ReadScopeParameter's shim
            // -----------------------------------------------------------------------
            val par = ReadScopeParameter(scanner, obs, idGen)
            val readOk = Keyword.PCB_SCOPE.read_scope(par)

            val board = par.board

            closeQuietly(inputStream)

            if (readOk) {
                // Apply power-plane autoroute settings if the DSN had no (autoroute ...) scope
                if (par.autoroute_settings == null) {
                    DsnFile.adjustPlaneAutorouteSettings(board)
                }
                val warnings = par.warnings
                if (warnings.isNotEmpty()) {
                    FRLogger.warn("DSN file '$effectiveDesignName' was loaded with ${warnings.size} warning(s).")
                }
                return DsnReadResult.Success(board, null, warnings)
            } else if (!par.board_outline_ok) {
                val warnings = par.warnings
                if (warnings.isNotEmpty()) {
                    FRLogger.warn("DSN file '$effectiveDesignName' was loaded with ${warnings.size} warning(s).")
                }
                return DsnReadResult.OutlineMissing(board, null, warnings)
            } else {
                return DsnReadResult.ParseError("(pcb", "DSN structure parsing failed")
            }
        }

        /**
         * Parses only the {@code (parser ...)}, {@code (resolution ...)}, and
         * {@code (structure (layer ...))} / {@code (structure (rule ...))} /
         * {@code (structure (autoroute_settings ...))} scopes. Does <em>not</em> construct full
         * board geometry, component placements, netlist items, or route traces.
         */
        @JvmStatic
        fun readMetadata(inputStream: InputStream?): DsnReadResult {
            if (inputStream == null) {
                return DsnReadResult.ParseError("(pcb", "inputStream must not be null")
            }

            val observers = BoardObserverAdaptor()
            val idGenerator = ItemIdentificationNumberGenerator()
            val scanner = SpecctraDsnStreamReader(inputStream)

            // -----------------------------------------------------------------------
            // Validate the "(pcb <name>" header — same three-token check as readBoard
            // -----------------------------------------------------------------------
            for (i in 0 until 3) {
                val token: Any?
                try {
                    token = scanner.next_token()
                } catch (e: IOException) {
                    closeQuietly(inputStream)
                    return DsnReadResult.IoError(e)
                }
                var ok = true
                if (i == 0) {
                    ok = (token === Keyword.OPEN_BRACKET)
                } else if (i == 1) {
                    ok = (token === Keyword.PCB_SCOPE)
                    scanner.yybegin(SpecctraDsnStreamReader.NAME)
                }
                if (!ok) {
                    closeQuietly(inputStream)
                    return DsnReadResult.ParseError(
                        "(pcb",
                        "Not a Specctra DSN file: expected '(pcb <name>' header"
                    )
                }
            }

            // -----------------------------------------------------------------------
            // Custom PCB-level loop — only parse metadata-relevant scopes.
            // We stop reading (and close the stream) as soon as (structure ...) ends,
            // skipping all subsequent heavy scopes.
            // -----------------------------------------------------------------------
            val par = ReadScopeParameter(scanner, observers, idGenerator)
            var nextToken: Any? = null
            outer@ while (true) {
                val prevToken = nextToken
                try {
                    nextToken = scanner.next_token()
                } catch (e: IOException) {
                    closeQuietly(inputStream)
                    return DsnReadResult.IoError(e)
                }
                if (nextToken == null || nextToken === Keyword.CLOSED_BRACKET) {
                    break // EOF or end of (pcb ...) scope
                }
                if (prevToken === Keyword.OPEN_BRACKET) {
                    when (nextToken) {
                        Keyword.PARSER_SCOPE -> {
                            // Populates par.host_cad, par.host_version, par.string_quote
                            Keyword.PARSER_SCOPE.read_scope(par)
                        }
                        Keyword.RESOLUTION_SCOPE -> {
                            // Populates par.unit, par.resolution
                            Keyword.RESOLUTION_SCOPE.read_scope(par)
                        }
                        Keyword.STRUCTURE_SCOPE -> {
                            // Populates par.layer_structure, par.snap_angle, par.autoroute_settings
                            // and creates the board via MinimalBoardManager (if a valid boundary exists).
                            // Return value is ignored — we extract whatever was populated.
                            Keyword.STRUCTURE_SCOPE.read_scope(par)
                            break@outer // stop here — skip library, placement, network, wiring
                        }
                        else -> {
                            ScopeKeyword.skip_scope(scanner)
                        }
                    }
                }
            }

            closeQuietly(inputStream)

            // -----------------------------------------------------------------------
            // Build DsnMetadata from the parsed fields.
            // -----------------------------------------------------------------------
            var layerCount = 0
            val layerStructure = par.layer_structure
            val board = par.board
            if (layerStructure != null) {
                layerCount = layerStructure.arr.size
            } else if (board != null) {
                layerCount = board.get_layer_count()
            }

            val metadata = DsnMetadata(
                par.host_cad,
                par.host_version,
                layerCount,
                par.unit,
                par.resolution,
                par.snap_angle,
                par.autoroute_settings
            )

            return DsnReadResult.Success(board, metadata, par.warnings)
        }

        private fun closeQuietly(stream: InputStream) {
            try {
                stream.close()
            } catch (_: IOException) {
                // ignore — nothing useful to do here
            }
        }
    }
}
