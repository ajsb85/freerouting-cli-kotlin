package app.freerouting.io.specctra

import app.freerouting.board.AngleRestriction
import app.freerouting.board.BasicBoard
import app.freerouting.io.specctra.parser.CoordinateTransform
import app.freerouting.io.specctra.parser.IJFlexScanner
import app.freerouting.io.specctra.parser.Keyword
import app.freerouting.io.specctra.parser.LayerStructure
import app.freerouting.io.specctra.parser.Library
import app.freerouting.io.specctra.parser.NetClass
import app.freerouting.io.specctra.parser.Network
import app.freerouting.io.specctra.parser.Rule
import app.freerouting.io.specctra.parser.ScopeKeyword
import app.freerouting.io.specctra.parser.SpecctraDsnStreamReader
import app.freerouting.io.specctra.parser.Structure
import app.freerouting.logger.FRLogger
import app.freerouting.rules.ViaInfo

import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * Reads a Specctra {@code .rules} file and applies the parsed rules directly to a
 * [BasicBoard], without any dependency on
 * {@link app.freerouting.interactive.GuiBoardManager}.
 *
 * <p>Replaces the read path previously found in
 * {@link app.freerouting.io.specctra.parser.RulesFile#read} (now {@link Deprecated}).
 */
class RulesReader private constructor() {

    companion object {

        /**
         * Reads the rules from [in] and applies them to [board].
         *
         * <p>The stream is closed by this method on return (success or failure).
         *
         * @param `in`        source — closed by this method on completion
         * @param designName expected PCB design name in the rules header (mismatch is logged
         *                   but does not abort the read)
         * @param board      the board to which parsed rules are applied
         * @return `true` if the rules were parsed and applied successfully;
         *         `false` on any parse or I/O error
         */
        @JvmStatic
        fun read(`in`: InputStream?, designName: String?, board: BasicBoard?): Boolean {
            if (`in` == null) {
                FRLogger.warn("RulesReader.read: input stream is null")
                return false
            }
            if (board == null) {
                FRLogger.warn("RulesReader.read: board is null")
                closeQuietly(`in`)
                return false
            }

            val scanner = SpecctraDsnStreamReader(`in`)
            try {
                // Validate the "(rules PCB <name>" header
                var currToken = scanner.next_token()
                if (currToken !== Keyword.OPEN_BRACKET) {
                    FRLogger.warn(
                        "RulesReader.read: open bracket expected at '${scanner.get_scope_identifier()}'"
                    )
                    return false
                }
                currToken = scanner.next_token()
                if (currToken !== Keyword.RULES) {
                    FRLogger.warn(
                        "RulesReader.read: keyword 'rules' expected at '${scanner.get_scope_identifier()}'"
                    )
                    return false
                }
                currToken = scanner.next_token()
                if (currToken !== Keyword.PCB_SCOPE) {
                    FRLogger.warn(
                        "RulesReader.read: keyword 'pcb' expected at '${scanner.get_scope_identifier()}'"
                    )
                    return false
                }
                scanner.yybegin(SpecctraDsnStreamReader.NAME)
                currToken = scanner.next_token()
                if (currToken !is String || currToken != designName) {
                    FRLogger.warn(
                        "RulesReader.read: design_name not matching at '${scanner.get_scope_identifier()}'" +
                                " (expected '$designName', got '$currToken')"
                    )
                    // non-fatal: continue reading
                }

                val layerStructure = LayerStructure(board.layer_structure)
                val coordinateTransform = board.communication.coordinate_transform

                // Parse all top-level scopes in the rules body
                var nextToken: Any? = null
                while (true) {
                    val prevToken = nextToken
                    try {
                        nextToken = scanner.next_token()
                    } catch (e: IOException) {
                        FRLogger.error("RulesReader.read: IO error scanning rules body", e)
                        return false
                    }
                    if (nextToken == null) {
                        FRLogger.warn(
                            "RulesReader.read: unexpected end of file at '${scanner.get_scope_identifier()}'"
                        )
                        return false
                    }
                    if (nextToken === Keyword.CLOSED_BRACKET) {
                        // end of (rules ...) scope — success
                        break
                    }
                    if (prevToken === Keyword.OPEN_BRACKET) {
                        when (nextToken) {
                            Keyword.RULE -> {
                                applyRules(Rule.read_scope(scanner), board, null)
                            }
                            Keyword.LAYER -> {
                                applyLayerRules(scanner, board)
                            }
                            Keyword.PADSTACK -> {
                                Library.read_padstack_scope(
                                    scanner,
                                    layerStructure,
                                    coordinateTransform,
                                    board.library.padstacks
                                )
                            }
                            Keyword.VIA -> {
                                applyViaInfo(scanner, board)
                            }
                            Keyword.VIA_RULE -> {
                                applyViaRule(scanner, board)
                            }
                            Keyword.CLASS -> {
                                applyNetClass(scanner, layerStructure, board)
                            }
                            Keyword.SNAP_ANGLE -> {
                                val snapAngle = Structure.read_snap_angle(scanner)
                                if (snapAngle != null) {
                                    board.rules.set_trace_angle_restriction(snapAngle)
                                }
                            }
                            else -> {
                                ScopeKeyword.skip_scope(scanner)
                            }
                        }
                    }
                }
                return true
            } catch (e: IOException) {
                FRLogger.error("RulesReader.read: IO error scanning rules header", e)
                return false
            } finally {
                closeQuietly(`in`)
            }
        }

        // -------------------------------------------------------------------------
        // Private helpers (migrated from RulesFile)
        // -------------------------------------------------------------------------

        private fun applyRules(rules: Collection<Rule>?, board: BasicBoard, layerName: String?) {
            if (rules == null) {
                return
            }
            var layerNo = -1
            if (layerName != null) {
                layerNo = board.layer_structure.get_no(layerName)
                if (layerNo < 0) {
                    FRLogger.warn("RulesReader.applyRules: layer not found: '$layerName'")
                }
            }
            val coordinateTransform = board.communication.coordinate_transform
            val stringQuote = board.communication.specctra_parser_info.string_quote
            for (rule in rules) {
                if (rule is Rule.WidthRule) {
                    val traceHalfwidth = (coordinateTransform.dsn_to_board(rule.value) / 2.0).roundToInt()
                    if (layerNo < 0) {
                        board.rules.set_default_trace_half_widths(traceHalfwidth)
                    } else {
                        board.rules.set_default_trace_half_width(layerNo, traceHalfwidth)
                    }
                } else if (rule is Rule.ClearanceRule) {
                    Structure.set_clearance_rule(
                        rule,
                        layerNo,
                        coordinateTransform,
                        board.rules,
                        stringQuote
                    )
                }
            }
        }

        private fun applyLayerRules(scanner: IJFlexScanner, board: BasicBoard) {
            try {
                var nextToken = scanner.next_token()
                if (nextToken !is String) {
                    FRLogger.warn(
                        "RulesReader.applyLayerRules: String expected at '${scanner.get_scope_identifier()}'"
                    )
                    return
                }
                val layerString = nextToken
                nextToken = scanner.next_token()
                while (nextToken !== Keyword.CLOSED_BRACKET) {
                    if (nextToken !== Keyword.OPEN_BRACKET) {
                        FRLogger.warn(
                            "RulesReader.applyLayerRules: '(' expected at '${scanner.get_scope_identifier()}'"
                        )
                        return
                    }
                    nextToken = scanner.next_token()
                    if (nextToken === Keyword.RULE) {
                        applyRules(Rule.read_scope(scanner), board, layerString)
                    } else {
                        ScopeKeyword.skip_scope(scanner)
                    }
                    nextToken = scanner.next_token()
                }
            } catch (e: IOException) {
                FRLogger.error("RulesReader.applyLayerRules: IO error scanning file", e)
            }
        }

        private fun applyViaInfo(scanner: IJFlexScanner, board: BasicBoard) {
            val viaInfo = Network.read_via_info(scanner, board) ?: return
            val existing = board.rules.via_infos.get(viaInfo.get_name())
            if (existing != null) {
                board.rules.via_infos.remove(existing)
            }
            board.rules.via_infos.add(viaInfo)
        }

        private fun applyViaRule(scanner: IJFlexScanner, board: BasicBoard) {
            val viaRule = Network.read_via_rule(scanner, board)
            if (viaRule != null) {
                Network.add_via_rule(viaRule, board)
            }
        }

        private fun applyNetClass(
            scanner: IJFlexScanner,
            layerStructure: LayerStructure,
            board: BasicBoard
        ) {
            val netClass = NetClass.read_scope(scanner) ?: return
            Network.insert_net_class(
                netClass,
                layerStructure,
                board,
                board.communication.coordinate_transform,
                false
            )
        }

        private fun closeQuietly(stream: InputStream) {
            try {
                stream.close()
            } catch (_: IOException) {
                // ignore
            }
        }
    }
}
