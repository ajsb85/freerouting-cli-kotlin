package app.freerouting.core.scoring

import app.freerouting.board.BasicBoard
import app.freerouting.board.ComponentOutline
import app.freerouting.board.ConductionArea
import app.freerouting.board.DrillItem
import app.freerouting.board.FixedState
import app.freerouting.board.Item
import app.freerouting.board.Pin
import app.freerouting.board.PolylineTrace
import app.freerouting.board.Trace
import app.freerouting.board.Unit as FRUnit
import app.freerouting.board.Via
import app.freerouting.constants.Constants
import app.freerouting.datastructures.UndoableObjects
import app.freerouting.geometry.planar.FloatPoint
import app.freerouting.geometry.planar.Line
import app.freerouting.geometry.planar.Polyline
import app.freerouting.core.FileFormat
import app.freerouting.management.TextManager
import app.freerouting.management.gson.GsonProvider
import app.freerouting.rules.BoardRules
import app.freerouting.settings.RouterScoringSettings
import com.google.gson.annotations.SerializedName
import java.awt.geom.Rectangle2D
import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.util.ArrayList

/**
 * Statistics of a board.
 */
class BoardStatistics : Serializable {

    @SerializedName("host")
    @JvmField
    var host: String? = null

    @SerializedName("unit")
    @JvmField
    var unit: String? = null

    @SerializedName("board")
    @JvmField
    var board: BoardStatisticsBoard = BoardStatisticsBoard()

    @SerializedName("layers")
    @JvmField
    var layers: BoardStatisticsLayers = BoardStatisticsLayers()

    @SerializedName("items")
    @JvmField
    var items: BoardStatisticsItems = BoardStatisticsItems()

    @SerializedName("components")
    @JvmField
    var components: BoardStatisticsComponents = BoardStatisticsComponents()

    @SerializedName("pads")
    @JvmField
    var pads: BoardStatisticsPads = BoardStatisticsPads()

    @SerializedName("nets")
    @JvmField
    var nets: BoardStatisticsNets = BoardStatisticsNets()

    @SerializedName("connections")
    @JvmField
    var connections: BoardStatisticsConnections = BoardStatisticsConnections()

    @SerializedName("traces")
    @JvmField
    var traces: BoardStatisticsTraces = BoardStatisticsTraces()

    @SerializedName("bends")
    @JvmField
    var bends: BoardStatisticsBends = BoardStatisticsBends()

    @SerializedName("vias")
    @JvmField
    var vias: BoardStatisticsVias = BoardStatisticsVias()

    @SerializedName("clearance_violations")
    @JvmField
    var clearanceViolations: BoardStatisticsClearanceViolations = BoardStatisticsClearanceViolations()

    constructor()

    /**
     * Creates a new BoardFileStatistics object from a RoutingBoard object.
     */
    constructor(board: BasicBoard) : this(board, null)

    /**
     * Creates a new BoardFileStatistics object from a RoutingBoard object and
     * defines the preferred unit for the statistics.
     */
    constructor(board: BasicBoard, unit: FRUnit?) {
        val bb = board.get_bounding_box()

        var currentHost: String? = board.communication.specctra_parser_info.host_cad + "," +
                board.communication.specctra_parser_info.host_version
        if (currentHost == null || currentHost.isEmpty()) {
            currentHost = "Freerouting," + Constants.FREEROUTING_VERSION
        }
        this.host = TextManager.unescapeUnicode(currentHost)

        this.unit = board.communication.unit.toString()

        // Board
        this.board.boundingBox = Rectangle2D.Float(
            bb.ur.x.toFloat(), board.get_bounding_box().ur.y.toFloat(),
            bb.ll.x.toFloat(), board.get_bounding_box().ll.y.toFloat()
        )
        this.board.size = Rectangle2D.Float(
            0f, 0f,
            Math.abs(board.get_bounding_box().ll.x.toFloat() - board.get_bounding_box().ur.x.toFloat()),
            Math.abs(board.get_bounding_box().ll.y.toFloat() - board.get_bounding_box().ur.y.toFloat())
        )

        // Layers
        this.layers.totalCount = board.get_layer_count()
        this.layers.signalCount = board.layer_structure.signal_layer_count()

        // Items
        this.items.totalCount = 0
        this.items.traceCount = 0
        this.items.viaCount = 0
        this.items.conductionAreaCount = 0
        this.items.drillItemCount = 0
        this.items.pinCount = 0
        this.items.componentOutlineCount = 0
        this.items.otherCount = 0
        val it = board.item_list.start_read_object()
        while (true) {
            val curr_item = board.item_list.read_object(it) as? Item ?: break
            this.items.totalCount = (this.items.totalCount ?: 0) + 1
            when (curr_item) {
                is Trace -> this.items.traceCount = (this.items.traceCount ?: 0) + 1
                is Via -> this.items.viaCount = (this.items.viaCount ?: 0) + 1
                is ConductionArea -> this.items.conductionAreaCount = (this.items.conductionAreaCount ?: 0) + 1
                is DrillItem -> this.items.drillItemCount = (this.items.drillItemCount ?: 0) + 1
                is Pin -> this.items.pinCount = (this.items.pinCount ?: 0) + 1
                is ComponentOutline -> this.items.componentOutlineCount = (this.items.componentOutlineCount ?: 0) + 1
                else -> this.items.otherCount = (this.items.otherCount ?: 0) + 1
            }
        }

        // Components
        this.components.totalCount = board.components.count()

        // Pads
        this.pads.totalCount = board.get_pins().size

        // Nets
        this.nets.totalCount = board.rules.nets.max_net_no()
        this.nets.classCount = board.rules.net_classes.count()

        // Traces
        this.traces.totalCount = board.get_traces().size
        this.traces.totalLength = board.get_traces().stream().mapToDouble { it.get_length() }.sum().toFloat()

        // Normalise trace length to millimetres so that calculateScore() uses a
        // physically meaningful cost regardless of DSN coordinate resolution.
        val boardUnitToMmFactor = FRUnit.scale(1.0, board.communication.unit, FRUnit.MM) /
                if (board.communication.resolution > 0) board.communication.resolution else 1
        this.traces.totalLengthMm = (this.traces.totalLength!! * boardUnitToMmFactor).toFloat()
        if (this.traces.totalCount!! > 0) {
            this.traces.averageLength = this.traces.totalLength!! / this.traces.totalCount!!
        } else {
            this.traces.averageLength = 0.0f
        }
        this.traces.totalSegmentCount = 0
        this.traces.totalHorizontalLength = 0.0f
        this.traces.totalVerticalLength = 0.0f
        this.traces.totalAngledLength = 0.0f
        for (trace in board.get_traces()) {
            if (trace is PolylineTrace) {
                val polyline = trace.polyline()
                val cornerCount = polyline.corner_count()
                if (cornerCount > 1) {
                    this.traces.totalSegmentCount = (this.traces.totalSegmentCount ?: 0) + (cornerCount - 1)
                }

                for (line in polyline.arr) {
                    val a = line.a.to_float()
                    val b = line.b.to_float()
                    val dx = a.x - b.x
                    val dy = a.y - b.y
                    val length = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                    if (a.x == b.x) {
                        this.traces.totalVerticalLength = (this.traces.totalVerticalLength ?: 0f) + length
                    } else if (a.y == b.y) {
                        this.traces.totalHorizontalLength = (this.traces.totalHorizontalLength ?: 0f) + length
                    } else {
                        this.traces.totalAngledLength = (this.traces.totalAngledLength ?: 0f) + length
                    }
                }
            }
        }

        this.traces.totalWeightedLength = 0.0f
        val default_clearance_class = BoardRules.default_clearance_class()
        val it2 = board.item_list.start_read_object()
        while (true) {
            val curr_item = board.item_list.read_object(it2) ?: break
            if (curr_item is Trace) {
                val fixed_state = curr_item.get_fixed_state()
                if (fixed_state == FixedState.UNFIXED || fixed_state == FixedState.SHOVE_FIXED) {
                    var weighted_trace_length = curr_item.get_length() * (curr_item.get_half_width() +
                            board.clearance_value(curr_item.clearance_class_no(), default_clearance_class, curr_item.get_layer()))
                    if (fixed_state == FixedState.SHOVE_FIXED) {
                        weighted_trace_length /= 2.0
                    }
                    this.traces.totalWeightedLength = (this.traces.totalWeightedLength ?: 0f) + weighted_trace_length.toFloat()
                }
            }
        }

        // Connections
        val drc = app.freerouting.drc.DesignRulesChecker(board, null)
        drc.calculateAllIncompletes()
        this.connections.maximumCount = drc.max_connections
        this.connections.incompleteCount = drc.getIncompleteCount()

        // Bends
        this.bends.totalCount = 0
        this.bends.ninetyDegreeCount = 0
        this.bends.fortyFiveDegreeCount = 0
        this.bends.otherAngleCount = 0
        for (trace in board.get_traces()) {
            if (trace is PolylineTrace) {
                val polyline = trace.polyline()
                val cornerCount = polyline.corner_count()

                if (cornerCount >= 3) {
                    val bendsInTrace = cornerCount - 2
                    this.bends.totalCount = (this.bends.totalCount ?: 0) + bendsInTrace

                    for (i in 1 until cornerCount - 1) {
                        val prev = polyline.corner(i - 1)!!.to_float()
                        val curr = polyline.corner(i)!!.to_float()
                        val next = polyline.corner(i + 1)!!.to_float()

                        val dx1 = curr.x - prev.x
                        val dy1 = curr.y - prev.y
                        val dx2 = next.x - curr.x
                        val dy2 = next.y - curr.y

                        var angle = Math.abs(Math.toDegrees(Math.atan2(dy2, dx2) - Math.atan2(dy1, dx1)))
                        angle = Math.min(angle, 360.0 - angle)
                        angle = if (angle > 180.0) 360.0 - angle else angle

                        if (Math.abs(angle - 90.0) < 1.0) {
                            this.bends.ninetyDegreeCount = (this.bends.ninetyDegreeCount ?: 0) + 1
                        } else if (Math.abs(angle - 45.0) < 1.0 || Math.abs(angle - 135.0) < 1.0) {
                            this.bends.fortyFiveDegreeCount = (this.bends.fortyFiveDegreeCount ?: 0) + 1
                        } else {
                            this.bends.otherAngleCount = (this.bends.otherAngleCount ?: 0) + 1
                        }
                    }
                }
            }
        }

        // Vias
        this.vias.totalCount = board.get_vias().size
        this.vias.throughHoleCount = 0
        this.vias.blindCount = 0
        this.vias.buriedCount = 0
        for (via in board.get_vias()) {
            if (via.first_layer() == 0 && via.last_layer() == this.layers.totalCount!! - 1) {
                this.vias.throughHoleCount = (this.vias.throughHoleCount ?: 0) + 1
            } else if (via.first_layer() == 0 || via.last_layer() == this.layers.totalCount!! - 1) {
                this.vias.blindCount = (this.vias.blindCount ?: 0) + 1
            } else {
                this.vias.buriedCount = (this.vias.buriedCount ?: 0) + 1
            }
        }

        // Clearance violations
        this.clearanceViolations.totalCount = drc.getAllClearanceViolations().size

        // Convert all length values from board.communication.unit to the preferred unit
        val targetUnit = unit ?: FRUnit.MM

        if (targetUnit != board.communication.unit) {
            val fromUnit = board.communication.unit
            val toUnit = targetUnit
            this.unit = targetUnit.toString()

            // Board
            this.board.boundingBox = Rectangle2D.Float(
                FRUnit.scale(this.board.boundingBox!!.x.toDouble(), fromUnit, toUnit).toFloat(),
                FRUnit.scale(this.board.boundingBox!!.y.toDouble(), fromUnit, toUnit).toFloat(),
                FRUnit.scale(this.board.boundingBox!!.width.toDouble(), fromUnit, toUnit).toFloat(),
                FRUnit.scale(this.board.boundingBox!!.height.toDouble(), fromUnit, toUnit).toFloat()
            )
            this.board.size = Rectangle2D.Float(
                0f, 0f,
                FRUnit.scale(this.board.size!!.width.toDouble(), fromUnit, toUnit).toFloat(),
                FRUnit.scale(this.board.size!!.height.toDouble(), fromUnit, toUnit).toFloat()
            )

            // Traces
            this.traces.totalLength = FRUnit.scale(this.traces.totalLength!!.toDouble(), fromUnit, toUnit).toFloat()
            this.traces.totalWeightedLength = FRUnit.scale(this.traces.totalWeightedLength!!.toDouble(), fromUnit, toUnit).toFloat()
            this.traces.averageLength = FRUnit.scale(this.traces.averageLength!!.toDouble(), fromUnit, toUnit).toFloat()
            this.traces.totalHorizontalLength = FRUnit.scale(this.traces.totalHorizontalLength!!.toDouble(), fromUnit, toUnit).toFloat()
            this.traces.totalVerticalLength = FRUnit.scale(this.traces.totalVerticalLength!!.toDouble(), fromUnit, toUnit).toFloat()
            this.traces.totalAngledLength = FRUnit.scale(this.traces.totalAngledLength!!.toDouble(), fromUnit, toUnit).toFloat()
        }
    }

    /**
     * Creates a new BoardFileStatistics object from a file. This method should be
     * used only if the board object is not available, because the board object
     * based method is more detailed.
     */
    constructor(data: ByteArray, format: FileFormat) : this() {
        if (format == FileFormat.SES || format == FileFormat.DSN) {
            val content = String(data, StandardCharsets.UTF_8)

            if (format == FileFormat.SES) {
                val lines = content.split("\\(path ".toRegex()).toTypedArray()
                val layers = ArrayList<String>()
                for (i in lines.indices) {
                    val line = lines[i]
                    val words = line.split(" ".toRegex()).toTypedArray()

                    if (i > 0 && words.size >= 2) {
                        val layer = words[0]
                        if (!layers.contains(layer)) {
                            layers.add(layer)
                        }
                    }
                }

                this.layers.totalCount = layers.size
                this.components.totalCount = content.split("\\(component".toRegex()).toTypedArray().size - 1
                this.nets.totalCount = content.split("\\(net".toRegex()).toTypedArray().size - 1
                this.traces.totalCount = content.split("\\(wire".toRegex()).toTypedArray().size - 1
                this.vias.totalCount = content.split("\\(via".toRegex()).toTypedArray().size - 1
            } else if (format == FileFormat.DSN) {
                val lines = content.split("\n".toRegex()).toTypedArray()
                var hostCad: String? = null
                var hostVersion: String? = null
                for (lineStr in lines) {
                    val line = lineStr.trim()
                    val value: String
                    if (line.startsWith("(host_cad")) {
                        value = line.substring(9, line.length - 1).trim()
                        hostCad = TextManager.removeQuotes(value)
                    } else if (line.startsWith("(host_version")) {
                        value = line.substring(13, line.length - 1).trim()
                        hostVersion = TextManager.removeQuotes(value)
                    }

                    if (hostCad != null && hostVersion != null) {
                        break
                    }
                }

                if (hostCad != null && hostVersion != null) {
                    this.host = "$hostCad,$hostVersion"
                } else if (hostCad != null) {
                    this.host = hostCad
                }

                this.layers.totalCount = content.split("\\(layer".toRegex()).toTypedArray().size - 1
                this.components.totalCount = content.split("\\(component".toRegex()).toTypedArray().size - 1
                this.nets.classCount = content.split("\\(class".toRegex()).toTypedArray().size - 1
                this.nets.totalCount = content.split("\\(net".toRegex()).toTypedArray().size - 1
                this.traces.totalCount = content.split("\\(wire".toRegex()).toTypedArray().size - 1
                this.vias.totalCount = content.split("\\(via".toRegex()).toTypedArray().size - 1
            }
        }
    }

    /**
     * Returns a JSON representation of this object.
     */
    override fun toString(): String {
        return GsonProvider.GSON.toJson(this)
    }

    /**
     * Calculates the score/cost of the board based on the given scoring settings.
     * Higher score means better board.
     */
    fun calculateScore(scoringSettings: RouterScoringSettings): Float {
        val maximumScore = getMaximumScore(scoringSettings)
        val unroutedNetPenalty = scoringSettings.unroutedNetPenalty ?: 0f
        val clearanceViolationPenalty = scoringSettings.clearanceViolationPenalty ?: 0f
        val bendPenalty = scoringSettings.bendPenalty ?: 0f
        val defaultPreferredDirectionTraceCost = scoringSettings.defaultPreferredDirectionTraceCost ?: 0.0
        val viaCosts = scoringSettings.viaCosts ?: 0

        val penalties = this.connections.incompleteCount!! * unroutedNetPenalty +
                this.clearanceViolations.totalCount!! * clearanceViolationPenalty +
                this.bends.totalCount!! * bendPenalty
        val traceLengthForCost = (this.traces.totalLengthMm ?: this.traces.totalLength ?: 0f).toDouble()
        val costs = (traceLengthForCost * defaultPreferredDirectionTraceCost +
                this.vias.totalCount!! * viaCosts).toFloat()

        return maximumScore - penalties - costs
    }

    fun getMaximumScore(scoringSettings: RouterScoringSettings): Float {
        val unroutedNetPenalty = scoringSettings.unroutedNetPenalty ?: 0f
        return this.connections.maximumCount!! * unroutedNetPenalty
    }

    fun getNormalizedScore(scoringSettings: RouterScoringSettings): Float {
        val maximumScore = getMaximumScore(scoringSettings)
        if (maximumScore <= 0f) {
            return 0f
        }
        return Math.max(0f, calculateScore(scoringSettings) / maximumScore) * 1000f
    }
}
