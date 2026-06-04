package app.freerouting.io.specctra

import app.freerouting.board.BasicBoard
import app.freerouting.board.FixedState
import app.freerouting.core.Padstack
import app.freerouting.io.specctra.parser.IJFlexScanner
import app.freerouting.io.specctra.parser.Keyword
import app.freerouting.io.specctra.parser.LayerStructure
import app.freerouting.io.specctra.parser.PolygonPath
import app.freerouting.io.specctra.parser.ScopeKeyword
import app.freerouting.io.specctra.parser.Shape
import app.freerouting.io.specctra.parser.SpecctraDsnStreamReader
import app.freerouting.geometry.planar.Point
import app.freerouting.geometry.planar.Polyline
import app.freerouting.logger.FRLogger
import app.freerouting.rules.Net
import app.freerouting.rules.DefaultItemClearanceClasses

import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * Reads a Specctra session (.ses) file and imports the routing data (wires and vias) into a
 * [BasicBoard].
 *
 * <p>This class has no dependency on {@code BoardManager}, {@code RoutingJob}, or any GUI class.
 *
 * <p>Replaces the read path previously found in
 * {@link app.freerouting.io.specctra.parser.SesFileReader} (now {@link Deprecated}).
 */
class SesReader private constructor(
    private val scanner: IJFlexScanner,
    private val board: BasicBoard,
    private val sessionFileScaleDenominator: Double
) {
    private val specctraLayerStructure = LayerStructure(board.layer_structure)
    private var wiresImported = 0
    private var viasImported = 0
    private var errorsEncountered = 0

    companion object {
        /**
         * Reads a SES file and imports the routing data into [board].
         *
         * <p>The stream is always closed by this method on return (success or failure).
         *
         * @param `in`   the SES input stream — closed by this method on completion
         * @param board the board to which wires and vias are added
         * @return a summary describing how many wires and vias were imported and how many errors
         *         were encountered; a non-zero [SesImportSummary.errorsEncountered] value
         *         means some items were skipped but the board is otherwise intact
         * @throws IOException if `in` is `null` or `board` is `null`, or if the
         *                     stream does not start with a valid Specctra session header, or if an I/O
         *                     error occurs while reading the stream
         */
        @JvmStatic
        @Throws(IOException::class)
        fun read(`in`: InputStream?, board: BasicBoard?): SesImportSummary {
            if (`in` == null) {
                throw IOException("SesReader.read: input stream must not be null")
            }
            if (board == null) {
                throw IOException("SesReader.read: board must not be null")
            }

            val scanner = SpecctraDsnStreamReader(`in`)

            // SES files use the same scale factor as SpecctraSesFileWriter: dsn_to_board(1) / resolution
            val scaleFactor = board.communication.coordinate_transform.dsn_to_board(1.0) /
                    board.communication.resolution

            val reader = SesReader(scanner, board, scaleFactor)

            try {
                reader.processSessionScope()
                FRLogger.info(
                    "SES file import complete: ${reader.wiresImported} wires, " +
                            "${reader.viasImported} vias imported" +
                            if (reader.errorsEncountered > 0) " (${reader.errorsEncountered} errors)" else ""
                )
                return SesImportSummary(
                    reader.wiresImported,
                    reader.viasImported,
                    reader.errorsEncountered
                )
            } finally {
                closeQuietly(`in`)
            }
        }

        private fun closeQuietly(stream: InputStream) {
            try {
                stream.close()
            } catch (_: IOException) {
                // ignore — nothing useful to do here
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Private parse helpers (migrated from SesFileReader)
    // ---------------------------------------------------------------------------

    /**
     * Processes the outermost scope of the session file.
     *
     * @throws IOException if the session header is malformed or an I/O error occurs
     */
    @Throws(IOException::class)
    private fun processSessionScope() {
        // Validate the "(session <name>" header
        var nextToken: Any? = null
        for (i in 0 until 3) {
            nextToken = this.scanner.next_token()
            var keywordOk = true
            if (i == 0) {
                keywordOk = (nextToken === Keyword.OPEN_BRACKET)
            } else if (i == 1) {
                keywordOk = (nextToken === Keyword.SESSION)
                this.scanner.yybegin(SpecctraDsnStreamReader.NAME) // consume the session name
            }
            if (!keywordOk) {
                throw IOException(
                    "SesReader: not a Specctra session file — expected '(session <name>' header, got: $nextToken"
                )
            }
        }

        // Read the direct subscopes of the session scope
        while (true) {
            val prevToken = nextToken
            nextToken = this.scanner.next_token() ?: return // end of file
            if (nextToken === Keyword.CLOSED_BRACKET) {
                // end of session scope
                break
            }

            if (prevToken === Keyword.OPEN_BRACKET) {
                if (nextToken === Keyword.ROUTES) {
                    processRoutesScope()
                } else {
                    // skip placement, was_is, and any other scopes we don't need
                    ScopeKeyword.skip_scope(this.scanner)
                }
            }
        }
    }

    /** Processes the `(routes ...)` scope containing network data. */
    @Throws(IOException::class)
    private fun processRoutesScope() {
        var nextToken: Any? = null
        while (true) {
            val prevToken = nextToken
            nextToken = this.scanner.next_token()
            if (nextToken == null) {
                FRLogger.warn(
                    "SesReader.processRoutesScope: unexpected end of file at '${this.scanner.get_scope_identifier()}'"
                )
                return
            }
            if (nextToken === Keyword.CLOSED_BRACKET) {
                break
            }

            if (prevToken === Keyword.OPEN_BRACKET) {
                if (nextToken === Keyword.NETWORK_OUT) {
                    processNetworkScope()
                } else {
                    ScopeKeyword.skip_scope(this.scanner)
                }
            }
        }
    }

    /** Processes the `(network_out ...)` scope containing individual nets. */
    @Throws(IOException::class)
    private fun processNetworkScope() {
        var nextToken: Any? = null
        while (true) {
            val prevToken = nextToken
            nextToken = this.scanner.next_token()
            if (nextToken == null) {
                FRLogger.warn(
                    "SesReader.processNetworkScope: unexpected end of file at '${this.scanner.get_scope_identifier()}'"
                )
                return
            }
            if (nextToken === Keyword.CLOSED_BRACKET) {
                break
            }

            if (prevToken === Keyword.OPEN_BRACKET) {
                if (nextToken === Keyword.NET) {
                    processNetScope()
                } else {
                    ScopeKeyword.skip_scope(this.scanner)
                }
            }
        }
    }

    /** Processes a single `(net ...)` scope containing wires and vias. */
    @Throws(IOException::class)
    private fun processNetScope() {
        var nextToken = this.scanner.next_token()
        if (nextToken !is String) {
            FRLogger.warn(
                "SesReader.processNetScope: String expected at '${this.scanner.get_scope_identifier()}'"
            )
            errorsEncountered++
            return
        }
        val netName = nextToken
        this.scanner.set_scope_identifier(netName)

        val net = board.rules.nets.get(netName, 1)
        if (net == null) {
            FRLogger.warn("SesReader: net not found: '$netName' — skipping")
            errorsEncountered++
            ScopeKeyword.skip_scope(this.scanner)
            return
        }
        val netNo = net.net_number
        val netNoArr = intArrayOf(netNo)

        while (true) {
            val prevToken = nextToken
            nextToken = this.scanner.next_token() ?: return
            if (nextToken === Keyword.CLOSED_BRACKET) {
                break
            }

            if (prevToken === Keyword.OPEN_BRACKET) {
                if (nextToken === Keyword.WIRE) {
                    if (!processWireScope(netNoArr)) {
                        errorsEncountered++
                    }
                } else if (nextToken === Keyword.VIA) {
                    if (!processViaScope(netNoArr)) {
                        errorsEncountered++
                    }
                } else {
                    ScopeKeyword.skip_scope(this.scanner)
                }
            }
        }
    }

    /**
     * Processes a `(wire ...)` scope and inserts the trace into the board.
     *
     * @return `true` if the wire was successfully imported; `false` on a parse or
     *         geometry error (the caller increments [errorsEncountered])
     */
    @Throws(IOException::class)
    private fun processWireScope(netNoArr: IntArray): Boolean {
        var wirePath: PolygonPath? = null
        var nextToken: Any? = null
        while (true) {
            val prevToken = nextToken
            nextToken = this.scanner.next_token()
            if (nextToken == null) {
                FRLogger.warn(
                    "SesReader.processWireScope: unexpected end of file at '${this.scanner.get_scope_identifier()}'"
                )
                return false
            }
            if (nextToken === Keyword.CLOSED_BRACKET) {
                break
            }
            if (prevToken === Keyword.OPEN_BRACKET) {
                if (nextToken === Keyword.POLYGON_PATH) {
                    wirePath = Shape.read_polygon_path_scope(this.scanner, this.specctraLayerStructure)
                } else {
                    ScopeKeyword.skip_scope(this.scanner)
                }
            }
        }

        if (wirePath == null) {
            // conduction areas have no polygon_path — silently skip
            return true
        }

        try {
            val layerNo = wirePath.layer.no
            val boardCoordinates = IntArray(wirePath.coordinate_arr.size)
            for (i in wirePath.coordinate_arr.indices) {
                boardCoordinates[i] =
                    (wirePath.coordinate_arr[i] / sessionFileScaleDenominator).roundToInt()
            }

            val points = Array(boardCoordinates.size / 2) { i ->
                Point.get_instance(boardCoordinates[2 * i], boardCoordinates[2 * i + 1])
            }

            val polyline = Polyline(points)
            val halfWidth =
                (wirePath.width / (2.0 * sessionFileScaleDenominator)).roundToInt()

            val clearanceClass = board.rules.get_default_net_class().default_item_clearance_classes
                .get(DefaultItemClearanceClasses.ItemClass.TRACE)

            board.insert_trace(
                polyline,
                layerNo,
                halfWidth,
                netNoArr,
                clearanceClass,
                FixedState.USER_FIXED
            )

            wiresImported++
            return true

        } catch (e: Exception) {
            FRLogger.warn("SesReader.processWireScope: failed to import wire — ${e.message}")
            return false
        }
    }

    /**
     * Processes a `(via ...)` scope and inserts the via into the board.
     *
     * @return `true` if the via was successfully imported; `false` on a parse or
     *         geometry error (the caller increments [errorsEncountered])
     */
    @Throws(IOException::class)
    private fun processViaScope(netNoArr: IntArray): Boolean {
        var nextToken = this.scanner.next_token()
        if (nextToken !is String) {
            FRLogger.warn(
                "SesReader.processViaScope: padstack name expected at '${this.scanner.get_scope_identifier()}'"
            )
            return false
        }
        val padstackName = nextToken
        this.scanner.set_scope_identifier(padstackName)

        val location = DoubleArray(2)
        for (i in 0 until 2) {
            nextToken = this.scanner.next_token()
            if (nextToken is Double) {
                location[i] = nextToken
            } else if (nextToken is Int) {
                location[i] = nextToken.toDouble()
            } else {
                FRLogger.warn(
                    "SesReader.processViaScope: number expected at '${this.scanner.get_scope_identifier()}'"
                )
                return false
            }
        }

        // Skip any additional sub-scopes (e.g. type, lock_type)
        nextToken = this.scanner.next_token()
        while (nextToken === Keyword.OPEN_BRACKET) {
            ScopeKeyword.skip_scope(this.scanner)
            nextToken = this.scanner.next_token()
        }

        if (nextToken !== Keyword.CLOSED_BRACKET) {
            FRLogger.warn(
                "SesReader.processViaScope: closing bracket expected at '${this.scanner.get_scope_identifier()}'"
            )
            return false
        }

        try {
            val viaPadstack = this.board.library.padstacks?.get(padstackName)
            if (viaPadstack == null) {
                FRLogger.warn("SesReader.processViaScope: via padstack not found: $padstackName")
                return false
            }

            val x = (location[0] / sessionFileScaleDenominator).roundToInt()
            val y = (location[1] / sessionFileScaleDenominator).roundToInt()
            val viaLocation = Point.get_instance(x, y)

            val clearanceClass = board.rules.get_default_net_class().default_item_clearance_classes
                .get(DefaultItemClearanceClasses.ItemClass.VIA)

            board.insert_via(
                viaPadstack,
                viaLocation,
                netNoArr,
                clearanceClass,
                FixedState.USER_FIXED,
                true
            )

            viasImported++
            return true

        } catch (e: Exception) {
            FRLogger.warn("SesReader.processViaScope: failed to import via — ${e.message}")
            return false
        }
    }
}
