package app.freerouting.io.specctra.parser

import app.freerouting.board.AngleRestriction
import app.freerouting.board.BasicBoard
import app.freerouting.board.BoardOutline
import app.freerouting.board.Communication
import app.freerouting.board.ConductionArea
import app.freerouting.board.FixedState
import app.freerouting.board.ObstacleArea
import app.freerouting.board.RoutingBoard
import app.freerouting.board.ViaObstacleArea
import app.freerouting.core.BoardLibrary
import app.freerouting.core.Padstack
import app.freerouting.core.RoutingJob
import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.datastructures.UndoableObjects.Storable
import app.freerouting.geometry.planar.Area
import app.freerouting.geometry.planar.IntBox
import app.freerouting.geometry.planar.Limits
import app.freerouting.geometry.planar.Point
import app.freerouting.geometry.planar.PolylineShape
import app.freerouting.geometry.planar.TileShape
import app.freerouting.logger.FRLogger
import app.freerouting.rules.BoardRules
import app.freerouting.rules.ClearanceMatrix
import app.freerouting.rules.DefaultItemClearanceClasses.ItemClass
import java.io.IOException
import java.util.LinkedList

/**
 * Class for reading and writing structure scopes from dsn-files.
 */
class Structure : ScopeKeyword("structure") {

    override fun read_scope(p_par: ReadScopeParameter): Boolean {
        val boardConstructionInfo = BoardConstructionInfo()

        // If true, components on the back side are rotated before mirroring
        // The correct location is the scope PlaceControl, but Electra writes it here.
        var flipStyleRotateFirst = false

        val keepoutList: MutableCollection<Shape.ReadAreaScopeResult> = LinkedList()
        val viaKeepoutList: MutableCollection<Shape.ReadAreaScopeResult> = LinkedList()
        val placeKeepoutList: MutableCollection<Shape.ReadAreaScopeResult> = LinkedList()

        var nextToken: Any? = null
        while (true) {
            val prevToken = nextToken
            try {
                nextToken = p_par.scanner.next_token()
            } catch (e: IOException) {
                FRLogger.error("Structure.read_scope: IO error scanning file", e)
                return false
            }
            if (nextToken == null) {
                FRLogger.warn("Structure.read_scope: unexpected end of file at '${p_par.scanner.get_scope_identifier()}'")
                return false
            }
            if (nextToken === CLOSED_BRACKET) {
                // end of scope
                break
            }
            var readOk = true
            if (prevToken === OPEN_BRACKET) {
                if (nextToken === Keyword.BOUNDARY) {
                    read_boundary_scope(p_par.scanner, boardConstructionInfo)
                } else if (nextToken === Keyword.LAYER) {
                    readOk = read_layer_scope(p_par.scanner, boardConstructionInfo, p_par.string_quote)
                    if (p_par.layer_structure != null) {
                        // correct the layer_structure because another layer is read
                        p_par.layer_structure = LayerStructure(boardConstructionInfo.layer_info)
                    }
                } else if (nextToken === Keyword.VIA) {
                    p_par.via_padstack_names = read_via_padstacks(p_par.scanner)
                } else if (nextToken === Keyword.RULE) {
                    val rules = Rule.read_scope(p_par.scanner)
                    if (rules != null) {
                        boardConstructionInfo.default_rules.addAll(rules)
                    }
                } else if (nextToken === Keyword.KEEPOUT) {
                    if (p_par.layer_structure == null) {
                        p_par.layer_structure = LayerStructure(boardConstructionInfo.layer_info)
                    }
                    val area = Shape.read_area_scope(p_par.scanner, p_par.layer_structure, false)
                    if (area != null) {
                        keepoutList.add(area)
                    }
                } else if (nextToken === Keyword.VIA_KEEPOUT) {
                    if (p_par.layer_structure == null) {
                        p_par.layer_structure = LayerStructure(boardConstructionInfo.layer_info)
                    }
                    val area = Shape.read_area_scope(p_par.scanner, p_par.layer_structure, false)
                    if (area != null) {
                        viaKeepoutList.add(area)
                    }
                } else if (nextToken === Keyword.PLACE_KEEPOUT) {
                    if (p_par.layer_structure == null) {
                        p_par.layer_structure = LayerStructure(boardConstructionInfo.layer_info)
                    }
                    val area = Shape.read_area_scope(p_par.scanner, p_par.layer_structure, false)
                    if (area != null) {
                        placeKeepoutList.add(area)
                    }
                } else if (nextToken === Keyword.PLANE_SCOPE) {
                    if (p_par.layer_structure == null) {
                        p_par.layer_structure = LayerStructure(boardConstructionInfo.layer_info)
                    }
                    Keyword.PLANE_SCOPE.read_scope(p_par)
                } else if (nextToken === Keyword.AUTOROUTE_SETTINGS) {
                    if (p_par.layer_structure == null) {
                        p_par.layer_structure = LayerStructure(boardConstructionInfo.layer_info)
                    }
                    p_par.autoroute_settings = AutorouteSettings.read_scope(p_par.scanner, p_par.layer_structure!!)
                } else if (nextToken === Keyword.CONTROL) {
                    readOk = read_control_scope(p_par)
                } else if (nextToken === Keyword.FLIP_STYLE) {
                    flipStyleRotateFirst = PlaceControl.read_flip_style_rotate_first(p_par.scanner)
                } else if (nextToken === Keyword.SNAP_ANGLE) {
                    val snapAngle = read_snap_angle(p_par.scanner)
                    if (snapAngle != null) {
                        p_par.snap_angle = snapAngle
                    }
                } else {
                    skip_scope(p_par.scanner)
                }
            }
            if (!readOk) {
                return false
            }
        }

        // let's create a board based on the data we read (TODO: move this method
        // somewhere outside of the designforms.specctra package)
        var result = true
        if (p_par.board_handling.get_routing_board() == null) {
            result = create_board(p_par, boardConstructionInfo)
        }
        val board = p_par.board_handling.get_routing_board() ?: return false
        if (flipStyleRotateFirst) {
            board.components.set_flip_style_rotate_first(true)
        }

        // insert the keepouts
        for (currArea in keepoutList) {
            if (!insert_keepout(currArea, p_par, KeepoutType.keepout, FixedState.SYSTEM_FIXED)) {
                return false
            }
        }

        for (currArea in viaKeepoutList) {
            if (!insert_keepout(currArea, p_par, KeepoutType.via_keepout, FixedState.SYSTEM_FIXED)) {
                return false
            }
        }

        for (currArea in placeKeepoutList) {
            if (!insert_keepout(currArea, p_par, KeepoutType.place_keepout, FixedState.SYSTEM_FIXED)) {
                return false
            }
        }

        // insert the planes.
        for (planeInfo in p_par.plane_list) {
            val netName = planeInfo.net_name ?: continue
            val netId = Net.Id(netName, 1)
            if (!p_par.netlist.contains(netId)) {
                val newNet = p_par.netlist.add_net(netId)
                if (newNet != null) {
                    board.rules.nets.add(newNet.id.name, newNet.id.subnet_number, true)
                }
            }
            val currNet = board.rules.nets.get(netName, 1)
            if (currNet == null) {
                FRLogger.warn("Plane.read_scope: net not found at '${p_par.scanner.get_scope_identifier()}'")
                continue
            }
            val coordinateTransform = p_par.coordinate_transform ?: continue
            val planeAreaInfo = planeInfo.area ?: continue
            val planeArea = Shape.transform_area_to_board(planeAreaInfo.shape_list, coordinateTransform) ?: continue
            val currLayer = planeAreaInfo.shape_list.iterator().next().layer
            if (currLayer.no >= 0) {
                var clearanceClassNo: Int
                if (planeAreaInfo.clearance_class_name != null) {
                    clearanceClassNo = board.rules.clearance_matrix.get_no(planeAreaInfo.clearance_class_name)
                    if (clearanceClassNo < 0) {
                        FRLogger.warn("Structure.read_scope: clearance class not found at '${p_par.scanner.get_scope_identifier()}'")
                        clearanceClassNo = BoardRules.clearance_class_none()
                    }
                } else {
                    clearanceClassNo = currNet.get_class().default_item_clearance_classes
                        .get(ItemClass.AREA)
                }
                val netNumbers = IntArray(1)
                netNumbers[0] = currNet.net_number
                board.insert_conduction_area(
                    planeArea, currLayer.no, netNumbers, clearanceClassNo, false,
                    FixedState.SYSTEM_FIXED
                )
            } else {
                FRLogger.warn("Plane.read_scope: unexpected layer name at '${p_par.scanner.get_scope_identifier()}'")
                return false
            }
        }
        insert_missing_power_planes(boardConstructionInfo.layer_info, p_par.netlist, board)

        p_par.board_handling.initialize_manual_trace_half_widths()

        // Apply DSN autoroute settings to the current routing job if they were parsed
        if (p_par.autoroute_settings != null) {
            // Get the current routing job from the board manager
            val currentJob = p_par.board_handling.getCurrentRoutingJob()
            if (currentJob?.routerSettings != null) {
                // Apply the DSN file's autoroute settings to the routing job
                currentJob.routerSettings.applyNewValuesFrom(p_par.autoroute_settings)
                FRLogger.info("Applied DSN autoroute settings to routing job")
            }
        }

        return result
    }

    enum class KeepoutType {
        keepout, via_keepout, place_keepout
    }

    private class BoardConstructionInfo {
        val layer_info: MutableCollection<Layer> = LinkedList()
        var bounding_shape: Shape? = null
        val outline_shapes: MutableList<Shape> = LinkedList()
        var outline_clearance_class_name: String? = null
        var found_layer_count: Int = 0
        val default_rules: MutableCollection<Rule> = LinkedList()
        val layer_dependent_rules: MutableCollection<LayerRule> = LinkedList()
    }

    private class LayerRule(val layer_name: String, val rule: Collection<Rule>)

    /**
     * Used to separate the holes in the outline.
     */
    private class OutlineShape(val shape: PolylineShape) {
        val bounding_box: IntBox = shape.bounding_box()
        val convex_shapes: Array<TileShape>? = shape.split_to_convex()
        var is_hole: Boolean = false

        /**
         * Returns true, if this shape contains all corners of p_other_shape.
         */
        fun contains_all_corners(p_other_shape: OutlineShape): Boolean {
            val convexShapes = this.convex_shapes ?: return false
            val cornerCount = p_other_shape.shape.border_line_count()
            for (i in 0 until cornerCount) {
                val currCorner = p_other_shape.shape.corner(i)
                var isContained = false
                for (convexShape in convexShapes) {
                    if (convexShape.contains(currCorner)) {
                        isContained = true
                        break
                    }
                }
                if (!isContained) {
                    return false
                }
            }
            return true
        }
    }

    companion object {

        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_par: WriteScopeParameter) {
            p_par.file.start_scope()
            p_par.file.write("structure")

            // write the layer structure
            write_layers(p_par)

            // write the boundaries
            write_boundaries(p_par)

            // write the routing vias
            write_via_padstacks(p_par.board.library, p_par.file, p_par.identifier_type)

            // write the rules
            write_default_rules(p_par)

            // write the snap angles
            write_snap_angle(p_par.file, p_par.board.rules.get_trace_angle_restriction())

            // write the control scope
            write_control_scope(p_par.board.rules, p_par.file)

            if (p_par.autoroute_settings != null) {
                // write the auto-route settings
                AutorouteSettings.write_scope(
                    p_par.file, p_par.autoroute_settings, p_par.board.layer_structure,
                    p_par.identifier_type
                )
            }

            // write the conduction areas
            write_conduction_areas(p_par)

            // write the keepouts
            write_keepouts(p_par)

            p_par.file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_conduction_areas(p_par: WriteScopeParameter) {
            var currOb: Storable?
            val it = p_par.board.item_list.start_read_object()
            while (true) {
                currOb = p_par.board.item_list.read_object(it)
                if (currOb == null) {
                    break
                }
                if (currOb !is ConductionArea) {
                    continue
                }
                val currArea = currOb
                if (p_par.board.layer_structure.arr[currArea.get_layer()].is_signal) {
                    // These conduction areas are written in the wiring scope.
                    continue
                }
                Plane.write_scope(p_par, currArea)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_keepouts(p_par: WriteScopeParameter) {
            var currOb: Storable?
            val it = p_par.board.item_list.start_read_object()
            while (true) {
                currOb = p_par.board.item_list.read_object(it)
                if (currOb == null) {
                    break
                }
                if (currOb !is ObstacleArea) {
                    continue
                }
                val currKeepout = currOb
                if (currKeepout.get_component_no() != 0) {
                    // keepouts belonging to a component are not written individually.
                    continue
                }
                if (currKeepout is ConductionArea) {
                    // conduction area will be written later.
                    continue
                }
                write_keepout_scope(p_par, currKeepout)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_boundaries(p_par: WriteScopeParameter) {
            // write the bounding box
            p_par.file.start_scope()
            p_par.file.write("boundary")
            val bounds = p_par.board.get_bounding_box()
            val rectCoor = p_par.coordinate_transform.board_to_dsn(bounds)
            val boundingRectangle = Rectangle(Layer.PCB, rectCoor)
            boundingRectangle.write_scope(p_par.file, p_par.identifier_type)
            p_par.file.end_scope()

            // lookup the outline in the board
            var currOb: Storable?
            val it = p_par.board.item_list.start_read_object()
            while (true) {
                currOb = p_par.board.item_list.read_object(it)
                if (currOb == null) {
                    break
                }
                if (currOb is BoardOutline) {
                    break
                }
            }
            if (currOb == null) {
                FRLogger.warn("Structure.write_scope: board outline not found")
                return
            }
            val outline = currOb as BoardOutline

            // write the outline
            for (i in 0 until outline.shape_count()) {
                val outlineShape = p_par.coordinate_transform.board_to_dsn(outline.get_shape(i), Layer.SIGNAL)
                if (outlineShape != null) {
                    p_par.file.start_scope()
                    p_par.file.write("boundary")
                    outlineShape.write_scope(p_par.file, p_par.identifier_type)
                    p_par.file.end_scope()
                }
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_layers(p_par: WriteScopeParameter) {
            for (i in p_par.board.layer_structure.arr.indices) {
                val writeLayerRule = p_par.board.rules
                    .get_default_net_class()
                    .get_trace_half_width(i) != p_par.board.rules
                    .get_default_net_class()
                    .get_trace_half_width(0) || !clearance_equals(p_par.board.rules.clearance_matrix, i, 0)
                Layer.write_scope(p_par, i, writeLayerRule)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_default_rules(p_par: WriteScopeParameter) {
            // write the default rule using 0 as default layer.
            Rule.write_default_rule(p_par, 0)
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_via_padstacks(
            p_library: BoardLibrary,
            p_file: IndentFileWriter,
            p_identifier_type: IdentifierType
        ) {
            p_file.new_line()
            p_file.write("(via")
            for (i in 0 until p_library.via_padstack_count()) {
                val currPadstack = p_library.get_via_padstack(i)
                if (currPadstack != null) {
                    p_file.write(" ")
                    p_identifier_type.write(currPadstack.name, p_file)
                } else {
                    FRLogger.warn("Structure.write_via_padstacks: padstack is null")
                }
            }
            p_file.write(")")
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_control_scope(p_rules: BoardRules, p_file: IndentFileWriter) {
            p_file.start_scope()
            p_file.write("control")
            p_file.new_line()
            p_file.write("(via_at_smd ")
            var viaAtSmdAllowed = false
            for (i in 0 until p_rules.via_infos.count()) {
                if (p_rules.via_infos.get(i).attach_smd_allowed()) {
                    viaAtSmdAllowed = true
                    break
                }
            }
            if (viaAtSmdAllowed) {
                p_file.write("on)")
            } else {
                p_file.write("off)")
            }
            p_file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_keepout_scope(p_par: WriteScopeParameter, p_keepout: ObstacleArea) {
            val keepoutArea = p_keepout.get_area()
            val layerNo = p_keepout.get_layer()
            val boardLayer = p_par.board.layer_structure.arr[layerNo]
            val keepoutLayer = Layer(boardLayer.name, layerNo, boardLayer.is_signal)
            val boundaryShape: app.freerouting.geometry.planar.Shape
            val holes: Array<out app.freerouting.geometry.planar.Shape>
            if (keepoutArea is app.freerouting.geometry.planar.Shape) {
                boundaryShape = keepoutArea
                holes = emptyArray()
            } else {
                boundaryShape = keepoutArea.get_border()
                holes = keepoutArea.get_holes()
            }
            p_par.file.start_scope()
            if (p_keepout is ViaObstacleArea) {
                p_par.file.write("via_keepout")
            } else {
                p_par.file.write("keepout")
            }
            val dsnShape = p_par.coordinate_transform.board_to_dsn(boundaryShape, keepoutLayer)
            dsnShape?.write_scope(p_par.file, p_par.identifier_type)
            for (hole in holes) {
                val dsnHole = p_par.coordinate_transform.board_to_dsn(hole, keepoutLayer)
                dsnHole?.write_hole_scope(p_par.file, p_par.identifier_type)
            }
            // write clearance class if it's defined for this keepout area.
            if (p_keepout.clearance_class_no() > 0) {
                // skip it if it's the default clearance class.
                val clearanceName = p_par.board.rules.clearance_matrix.get_name(p_keepout.clearance_class_no())

                if ("default" != clearanceName && clearanceName != null) {
                    Rule.write_item_clearance_class(clearanceName, p_par.file, p_par.identifier_type)
                }
            }
            p_par.file.end_scope()
        }

        @JvmStatic
        private fun read_boundary_scope(p_scanner: IJFlexScanner, p_board_construction_info: BoardConstructionInfo): Boolean {
            val currShape = Shape.read_scope(p_scanner, null)
            // overread the closing bracket.
            try {
                var prevToken: Any? = null
                while (true) {
                    val nextToken = p_scanner.next_token()
                    if (nextToken === CLOSED_BRACKET) {
                        break
                    }
                    if (prevToken === OPEN_BRACKET) {
                        if (nextToken === Keyword.CLEARANCE_CLASS) {
                            p_board_construction_info.outline_clearance_class_name = DsnFile.read_string_scope(p_scanner)
                        } else {
                            FRLogger.error(
                                "There are multiple shapes defined in the boundary section of the DSN file. This scenario is not currently supported. If you have more than one board outlines defined, try to merge them into one.",
                                null
                            )
                            return false
                        }
                    }
                    prevToken = nextToken
                }
            } catch (e: IOException) {
                FRLogger.error("Structure.read_boundary_scope: IO error scanning file", e)
                return false
            }
            if (currShape == null) {
                FRLogger.warn("Structure.read_boundary_scope: shape is null at '${p_scanner.get_scope_identifier()}'")
                return true
            }
            if (currShape.layer == Layer.PCB) {
                if (p_board_construction_info.bounding_shape == null) {
                    p_board_construction_info.bounding_shape = currShape
                } else {
                    FRLogger.warn(
                        "Structure.read_boundary_scope: exact 1 bounding_shape expected at '${p_scanner.get_scope_identifier()}'"
                    )
                }
            } else if (currShape.layer == Layer.SIGNAL) {
                p_board_construction_info.outline_shapes.add(currShape)
            } else {
                FRLogger.warn("Structure.read_boundary_scope: unexpected layer at '${p_scanner.get_scope_identifier()}'")
            }
            return true
        }

        @JvmStatic
        private fun read_layer_scope(
            p_scanner: IJFlexScanner,
            p_board_construction_info: BoardConstructionInfo,
            p_string_quote: String
        ): Boolean {
            try {
                var layerOk = true
                var isSignal = true

                val layerString = p_scanner.next_string() ?: return false

                val netNames: MutableCollection<String> = LinkedList()
                var nextToken = p_scanner.next_token()
                while (nextToken !== CLOSED_BRACKET) {
                    if (nextToken !== OPEN_BRACKET) {
                        FRLogger.warn("Structure.read_layer_scope: ( expected at '${p_scanner.get_scope_identifier()}'")
                        return false
                    }
                    nextToken = p_scanner.next_token()
                    if (nextToken === Keyword.TYPE) {
                        nextToken = p_scanner.next_token()
                        if (nextToken === Keyword.POWER) {
                            isSignal = false
                        } else if (nextToken !== Keyword.SIGNAL &&
                            nextToken.toString() != Keyword.JUMPER.get_name()
                        ) {
                            if (nextToken is String) {
                                FRLogger.error(
                                    "Structure.read_layer_scope: the layer '$layerString' has an unknown layer type '$nextToken'",
                                    null
                                )
                            } else {
                                FRLogger.warn(
                                    "Structure.read_layer_scope: the layer '$layerString' has an unknown layer type at '${p_scanner.get_scope_identifier()}'"
                                )
                            }
                            layerOk = false
                        }
                        nextToken = p_scanner.next_token()
                        if (nextToken !== CLOSED_BRACKET) {
                            FRLogger.warn("Structure.read_layer_scope: ) expected at '${p_scanner.get_scope_identifier()}'")
                            return false
                        }
                    } else if (nextToken === Keyword.RULE) {
                        val currRules = Rule.read_scope(p_scanner)
                        if (currRules != null) {
                            p_board_construction_info.layer_dependent_rules.add(LayerRule(layerString, currRules))
                        }
                    } else if (nextToken === Keyword.USE_NET) {
                        while (true) {
                            p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                            nextToken = p_scanner.next_token()
                            if (nextToken === CLOSED_BRACKET) {
                                break
                            }
                            if (nextToken is String) {
                                netNames.add(nextToken)
                            } else {
                                FRLogger.warn("Structure.read_layer_scope: string expected at '${p_scanner.get_scope_identifier()}'")
                            }
                        }
                    } else {
                        skip_scope(p_scanner)
                    }
                    nextToken = p_scanner.next_token()
                }
                if (layerOk) {
                    val currLayer = Layer(layerString, p_board_construction_info.found_layer_count, isSignal, netNames)
                    p_board_construction_info.layer_info.add(currLayer)
                    ++p_board_construction_info.found_layer_count
                }
            } catch (e: IOException) {
                FRLogger.error("Layer.read_scope: IO error scanning file", e)
                return false
            }
            return true
        }

        @JvmStatic
        fun read_via_padstacks(p_scanner: IJFlexScanner): MutableCollection<String>? {
            try {
                val normalVias: MutableCollection<String> = LinkedList()
                var spareVias: Collection<String> = LinkedList()
                while (true) {
                    var nextToken = p_scanner.next_token()
                    if (nextToken === CLOSED_BRACKET) {
                        break
                    }
                    if (nextToken === OPEN_BRACKET) {
                        nextToken = p_scanner.next_token()
                        if (nextToken === Keyword.SPARE) {
                            val spare = read_via_padstacks(p_scanner)
                            if (spare != null) {
                                spareVias = spare
                            }
                        } else {
                            skip_scope(p_scanner)
                        }
                    } else if (nextToken is String) {
                        normalVias.add(nextToken)
                    } else {
                        FRLogger.warn("Structure.read_via_padstack: String expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                }
                // add the spare vias to the end of the list
                normalVias.addAll(spareVias)
                return normalVias
            } catch (e: IOException) {
                FRLogger.error("Structure.read_via_padstack: IO error scanning file", e)
                return null
            }
        }

        @JvmStatic
        private fun read_control_scope(p_par: ReadScopeParameter): Boolean {
            var nextToken: Any? = null
            while (true) {
                val prevToken = nextToken
                try {
                    nextToken = p_par.scanner.next_token()
                } catch (e: IOException) {
                    FRLogger.error("Structure.read_control_scope: IO error scanning file", e)
                    return false
                }
                if (nextToken == null) {
                    FRLogger.warn(
                        "Structure.read_control_scope: unexpected end of file at '${p_par.scanner.get_scope_identifier()}'"
                    )
                    return false
                }
                if (nextToken === CLOSED_BRACKET) {
                    // end of scope
                    break
                }
                if (prevToken === OPEN_BRACKET) {
                    if (nextToken === Keyword.VIA_AT_SMD) {
                        p_par.via_at_smd_allowed = DsnFile.read_on_off_scope(p_par.scanner)
                    } else {
                        skip_scope(p_par.scanner)
                    }
                }
            }
            return true
        }

        @JvmStatic
        fun read_snap_angle(p_scanner: IJFlexScanner): AngleRestriction? {
            try {
                var nextToken = p_scanner.next_token()
                val snapAngle = when (nextToken) {
                    Keyword.NINETY_DEGREE -> AngleRestriction.NINETY_DEGREE
                    Keyword.FORTYFIVE_DEGREE -> AngleRestriction.FORTYFIVE_DEGREE
                    Keyword.NONE -> AngleRestriction.NONE
                    else -> {
                        FRLogger.warn("Structure.read_snap_angle_scope: unexpected token at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                }
                nextToken = p_scanner.next_token()
                if (nextToken !== CLOSED_BRACKET) {
                    FRLogger.warn(
                        "Structure.read_selection_layer_scop: closing bracket expected at '${p_scanner.get_scope_identifier()}'"
                    )
                    return null
                }
                return snapAngle
            } catch (e: IOException) {
                FRLogger.error("Structure.read_snap_angle: IO error scanning file", e)
                return null
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_snap_angle(p_file: IndentFileWriter, p_angle_restriction: AngleRestriction) {
            p_file.start_scope()
            p_file.write("snap_angle ")
            p_file.new_line()

            if (p_angle_restriction == AngleRestriction.NINETY_DEGREE) {
                p_file.write("ninety_degree")
            } else if (p_angle_restriction == AngleRestriction.FORTYFIVE_DEGREE) {
                p_file.write("fortyfive_degree")
            } else {
                p_file.write("none")
            }
            p_file.end_scope()
        }

        @JvmStatic
        private fun insert_missing_power_planes(
            p_layer_info: Collection<Layer>,
            p_netlist: NetList,
            p_board: BasicBoard
        ) {
            val conductionAreas = p_board.get_conduction_areas()
            for (currLayer in p_layer_info) {
                if (currLayer.is_signal) {
                    continue
                }
                var conductionAreaFound = false
                for (currConductionArea in conductionAreas) {
                    if (currConductionArea.get_layer() == currLayer.no) {
                        conductionAreaFound = true
                        break
                    }
                }
                if (!conductionAreaFound && currLayer.net_names.isNotEmpty()) {
                    val currNetName = currLayer.net_names.iterator().next()
                    val currNetId = Net.Id(currNetName, 1)
                    if (!p_netlist.contains(currNetId)) {
                        val newNet = p_netlist.add_net(currNetId)
                        if (newNet != null) {
                            p_board.rules.nets.add(newNet.id.name, newNet.id.subnet_number, true)
                        }
                    }
                    val currNet = p_board.rules.nets.get(currNetId.name, currNetId.subnet_number)
                    if (currNet == null) {
                        FRLogger.warn("Structure.insert_missing_power_planes: net not found at '${currNetId.name}'")
                        continue
                    }
                    val netNumbers = IntArray(1)
                    netNumbers[0] = currNet.net_number
                    p_board.insert_conduction_area(
                        p_board.bounding_box, currLayer.no, netNumbers,
                        BoardRules.clearance_class_none(), false, FixedState.SYSTEM_FIXED
                    )
                }
            }
        }

        /**
         * Calculates shapes in p_outline_shapes, which are holes in the outline and
         * returns them in the result list.
         */
        @JvmStatic
        private fun separate_holes(p_outline_shapes: MutableCollection<PolylineShape>): Collection<PolylineShape> {
            val shapeArr = ArrayList<OutlineShape>()
            for (shape in p_outline_shapes) {
                shapeArr.add(OutlineShape(shape))
            }
            for (i in shapeArr.indices) {
                val currShape = shapeArr[i]
                for (j in shapeArr.indices) {
                    // check if shape_arr[j] may be contained in shape_arr[i]
                    val otherShape = shapeArr[j]
                    if (i == j || otherShape.is_hole) {
                        continue
                    }
                    if (!otherShape.bounding_box.contains(currShape.bounding_box)) {
                        continue
                    }
                    currShape.is_hole = otherShape.contains_all_corners(currShape)
                }
            }
            val holeList = LinkedList<PolylineShape>()
            for (outlineShape in shapeArr) {
                if (outlineShape.is_hole) {
                    p_outline_shapes.remove(outlineShape.shape)
                    holeList.add(outlineShape.shape)
                }
            }
            return holeList
        }

        /**
         * Updates the board rules from the rules read from the dsn file.
         */
        @JvmStatic
        private fun update_board_rules(
            p_par: ReadScopeParameter,
            p_board_construction_info: BoardConstructionInfo,
            p_board_rules: BoardRules
        ) {
            val coordinateTransform = p_par.coordinate_transform ?: return
            val layerStructure = p_par.layer_structure ?: return
            var smdToTurnGapFound = false
            // update the clearance matrix
            for (currOb in p_board_construction_info.default_rules) {
                if (currOb is Rule.ClearanceRule) {
                    if (set_clearance_rule(currOb, -1, coordinateTransform, p_board_rules, p_par.string_quote)) {
                        smdToTurnGapFound = true
                    }
                }
            }
            // update width rules
            for (currOb in p_board_construction_info.default_rules) {
                if (currOb is Rule.WidthRule) {
                    val wireWidth = currOb.value
                    val traceHalfwidth = Math.round(coordinateTransform.dsn_to_board(wireWidth) / 2.0).toInt()
                    FRLogger.debug(
                        "Set default trace width (all layers): DSN=$wireWidth → board=${traceHalfwidth * 2} (${traceHalfwidth * 2 / 40000.0} mm)"
                    )
                    p_board_rules.set_default_trace_half_widths(traceHalfwidth)
                }
            }
            for (layerRule in p_board_construction_info.layer_dependent_rules) {
                val layerNo = layerStructure.get_no(layerRule.layer_name)
                if (layerNo < 0) {
                    continue
                }
                for (currOb in layerRule.rule) {
                    if (currOb is Rule.WidthRule) {
                        val wireWidth = currOb.value
                        val traceHalfwidth = Math.round(coordinateTransform.dsn_to_board(wireWidth) / 2.0).toInt()
                        p_board_rules.set_default_trace_half_width(layerNo, traceHalfwidth)
                    } else if (currOb is Rule.ClearanceRule) {
                        set_clearance_rule(currOb, layerNo, coordinateTransform, p_board_rules, p_par.string_quote)
                    }
                }
            }
            if (!smdToTurnGapFound) {
                p_board_rules.set_pin_edge_to_turn_dist(p_board_rules.get_min_trace_half_width().toDouble())
            }
        }

        /**
         * Converts a dsn clearance rule into a board clearance rule. If p_layer_no < 0,
         * the rule is set on all layers. Returns true, if the string smd_to_turn_gap
         * was found.
         */
        @JvmStatic
        fun set_clearance_rule(
            p_rule: Rule.ClearanceRule,
            p_layer_no: Int,
            p_coordinate_transform: CoordinateTransform,
            p_board_rules: BoardRules,
            p_string_quote: String
        ): Boolean {
            var result = false
            val currClearance = Math.round(p_coordinate_transform.dsn_to_board(p_rule.value)).toInt()
            if (p_rule.clearance_class_pairs.isEmpty()) {
                if (p_layer_no < 0) {
                    p_board_rules.clearance_matrix.set_default_value(currClearance)
                    FRLogger.debug(
                        "Set DEFAULT clearance (all layers): $currClearance (${currClearance / 40000.0} mm) from DSN value ${p_rule.value}"
                    )
                } else {
                    p_board_rules.clearance_matrix.set_default_value(p_layer_no, currClearance)
                    FRLogger.debug(
                        "Set DEFAULT clearance (layer $p_layer_no): $currClearance (${currClearance / 40000.0} mm) from DSN value ${p_rule.value}"
                    )
                }
                return result
            }
            if (contains_wire_clearance_pair(p_rule.clearance_class_pairs)) {
                create_default_clearance_classes(p_board_rules)
            }

            for (currString in p_rule.clearance_class_pairs) {
                var currentString = currString
                if ("smd_to_turn_gap".equals(currentString, ignoreCase = true)) {
                    p_board_rules.set_pin_edge_to_turn_dist(currClearance.toDouble())
                    result = true
                    continue
                }
                var currPair = arrayOfNulls<String>(2)
                if (p_rule.clearance_class_pairs.size == 2) {
                    val iterator = p_rule.clearance_class_pairs.iterator()
                    currPair[0] = iterator.next()
                    currPair[1] = iterator.next()
                    for (i in currPair.indices) {
                        currPair[i] = currPair[i]?.replace("[\"]".toRegex(), "")
                    }
                    if (currPair[1]?.startsWith("_") == true) {
                        currPair[1] = currPair[1]?.substring(1)
                    }
                } else if (currentString.startsWith(p_string_quote)) {
                    // split at the second occurrence of p_string_quote
                    currentString = currentString.substring(p_string_quote.length)
                    currPair = currentString.split(p_string_quote.toRegex(), limit = 2).toTypedArray()
                    if (currPair.size != 2 || currPair[1]?.startsWith("_") != true) {
                        FRLogger.warn("Structure.set_clearance_rule: '_' expected at '$currentString'")
                        FRLogger.warn(
                            "You probably get this error because your clearance rule name has spaces or special characters in its name. Please change them first, and try again."
                        )
                        continue
                    }
                    currPair[1] = currPair[1]?.substring(1)
                } else {
                    val splitArr = currentString.split("_".toRegex(), limit = 2).toTypedArray()
                    if (splitArr.size != 2) {
                        // pairs with more than 1 underline like smd_via_same_net are not implemented
                        continue
                    }
                    currPair[0] = splitArr[0]
                    currPair[1] = splitArr[1]
                }

                val pair0 = currPair[0] ?: continue
                val pair1 = currPair[1] ?: continue

                val firstClassNo = if ("wire" == pair0) {
                    1 // default class
                } else {
                    p_board_rules.clearance_matrix.get_no(pair0)
                }
                val finalFirstClassNo = if (firstClassNo < 0) {
                    append_clearance_class(p_board_rules, pair0)
                } else {
                    firstClassNo
                }

                val secondClassNo = if ("wire" == pair1) {
                    1 // default class
                } else {
                    p_board_rules.clearance_matrix.get_no(pair1)
                }
                val finalSecondClassNo = if (secondClassNo < 0) {
                    append_clearance_class(p_board_rules, pair1)
                } else {
                    secondClassNo
                }

                if (p_layer_no < 0) {
                    p_board_rules.clearance_matrix.set_value(finalFirstClassNo, finalSecondClassNo, currClearance)
                    p_board_rules.clearance_matrix.set_value(finalSecondClassNo, finalFirstClassNo, currClearance)
                    FRLogger.debug(
                        "Set clearance (all layers): ${pair0}_$pair1 = $currClearance (${currClearance / 40000.0} mm), classes [$finalFirstClassNo,$finalSecondClassNo]"
                    )
                } else {
                    p_board_rules.clearance_matrix.set_value(finalFirstClassNo, finalSecondClassNo, p_layer_no, currClearance)
                    p_board_rules.clearance_matrix.set_value(finalSecondClassNo, finalFirstClassNo, p_layer_no, currClearance)
                    FRLogger.debug(
                        "Set clearance (layer $p_layer_no): ${pair0}_$pair1 = $currClearance (${currClearance / 40000.0} mm), classes [$finalFirstClassNo,$finalSecondClassNo]"
                    )
                }
            }
            return result
        }

        @JvmStatic
        fun contains_wire_clearance_pair(p_clearance_pairs: Collection<String>): Boolean {
            for (currPair in p_clearance_pairs) {
                if (currPair.startsWith("wire_") || currPair.endsWith("_wire")) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        private fun create_default_clearance_classes(p_board_rules: BoardRules) {
            append_clearance_class(p_board_rules, "via")
            append_clearance_class(p_board_rules, "smd")
            append_clearance_class(p_board_rules, "pin")
            append_clearance_class(p_board_rules, "area")
        }

        @JvmStatic
        private fun append_clearance_class(p_board_rules: BoardRules, p_name: String): Int {
            p_board_rules.clearance_matrix.append_class(p_name)
            val result = p_board_rules.clearance_matrix.get_no(p_name)
            val defaultNetClass = p_board_rules.get_default_net_class()
            when (p_name) {
                "via" -> defaultNetClass.default_item_clearance_classes.set(ItemClass.VIA, result)
                "pin" -> defaultNetClass.default_item_clearance_classes.set(ItemClass.PIN, result)
                "smd" -> defaultNetClass.default_item_clearance_classes.set(ItemClass.SMD, result)
                "area" -> defaultNetClass.default_item_clearance_classes.set(ItemClass.AREA, result)
            }
            return result
        }

        /**
         * Returns true, if all clearance values on the 2 input layers are equal.
         */
        @JvmStatic
        private fun clearance_equals(p_cl_matrix: ClearanceMatrix, p_layer_1: Int, p_layer_2: Int): Boolean {
            if (p_layer_1 == p_layer_2) {
                return true
            }
            for (i in 1 until p_cl_matrix.get_class_count()) {
                for (j in i until p_cl_matrix.get_class_count()) {
                    if (p_cl_matrix.get_value(i, j, p_layer_1, false) != p_cl_matrix.get_value(i, j, p_layer_2, false)) {
                        return false
                    }
                }
            }
            return true
        }

        @JvmStatic
        private fun insert_keepout(
            p_area: Shape.ReadAreaScopeResult,
            p_par: ReadScopeParameter,
            p_keepout_type: KeepoutType,
            p_fixed_state: FixedState
        ): Boolean {
            val coordinateTransform = p_par.coordinate_transform ?: return false
            val keepoutArea = Shape.transform_area_to_board(p_area.shape_list, coordinateTransform) ?: return false
            if (keepoutArea.dimension() < 2) {
                // A degenerate keepout (e.g. all polygon vertices identical, exported incorrectly by the EDA
                // tool) cannot be enforced as a routing constraint. The board remains valid — the keepout
                // restriction is simply not applied, making routing more permissive in that area.
                // This is a known export defect in some EDA tools (e.g. KiCad 4.0.7).
                FRLogger.warn(
                    "Keepout zone '${p_area.area_name}' was skipped because its geometry is degenerate " +
                            "(e.g. zero-area polygon). This is likely a DSN export issue in your EDA tool. " +
                            "The board will be routed without this keepout constraint."
                )
                return true
            }
            val board = p_par.board_handling.get_routing_board()
            if (board == null) {
                FRLogger.warn("Structure.insert_keepout: board not initialized")
                return false
            }
            val currLayer = p_area.shape_list.iterator().next().layer
            if (currLayer == Layer.SIGNAL) {
                for (i in 0 until board.get_layer_count()) {
                    val layerStructure = p_par.layer_structure
                    if (layerStructure != null && layerStructure.arr[i].is_signal) {
                        insert_keepout(board, keepoutArea, i, p_area.clearance_class_name, p_keepout_type, p_fixed_state)
                    }
                }
            } else if (currLayer.no >= 0) {
                insert_keepout(board, keepoutArea, currLayer.no, p_area.clearance_class_name, p_keepout_type, p_fixed_state)
            } else {
                FRLogger.warn("Structure.insert_keepout: unknown layer name at '${p_par.scanner.get_scope_identifier()}'")
                return false
            }

            return true
        }

        @JvmStatic
        private fun insert_keepout(
            p_board: BasicBoard,
            p_area: Area,
            p_layer: Int,
            p_clearance_class_name: String?,
            p_keepout_type: KeepoutType,
            p_fixed_state: FixedState
        ) {
            val clearanceClassNo = if (p_clearance_class_name == null) {
                p_board.rules.get_default_net_class().default_item_clearance_classes
                    .get(ItemClass.AREA)
            } else {
                val no = p_board.rules.clearance_matrix.get_no(p_clearance_class_name)
                if (no < 0) {
                    FRLogger.warn("Keepout.insert_keepout: clearance class not found at '$p_clearance_class_name'")
                    BoardRules.clearance_class_none()
                } else {
                    no
                }
            }
            if (p_keepout_type == KeepoutType.via_keepout) {
                p_board.insert_via_obstacle(p_area, p_layer, clearanceClassNo, p_fixed_state)
            } else if (p_keepout_type == KeepoutType.place_keepout) {
                p_board.insert_component_obstacle(p_area, p_layer, clearanceClassNo, p_fixed_state)
            } else {
                p_board.insert_obstacle(p_area, p_layer, clearanceClassNo, p_fixed_state)
            }
        }
    }

    private fun create_board(p_par: ReadScopeParameter, p_board_construction_info: BoardConstructionInfo): Boolean {
        val layerCount = p_board_construction_info.layer_info.size
        if (layerCount == 0) {
            FRLogger.warn(
                "Structure.create_board: layers missing in structure scope at '${p_par.scanner.get_scope_identifier()}'"
            )
            return false
        }
        if (p_board_construction_info.bounding_shape == null) {
            // happens if the boundary shape with layer pcb is missing
            if (p_board_construction_info.outline_shapes.isEmpty()) {
                FRLogger.warn("Structure.create_board: outline missing at '${p_par.scanner.get_scope_identifier()}'")
                p_par.board_outline_ok = false
                return false
            }
            val it = p_board_construction_info.outline_shapes.iterator()

            var boundingBox = it.next().bounding_box() ?: return false
            while (it.hasNext()) {
                val nextBox = it.next().bounding_box() ?: continue
                boundingBox = boundingBox.union(nextBox)
            }
            p_board_construction_info.bounding_shape = boundingBox
        }
        val boundingBox = p_board_construction_info.bounding_shape!!.bounding_box() ?: return false
        val boardLayerArr = arrayOfNulls<app.freerouting.board.Layer>(layerCount)
        val it = p_board_construction_info.layer_info.iterator()
        for (i in 0 until layerCount) {
            val currLayer = it.next()
            if (currLayer.no < 0 || currLayer.no >= layerCount) {
                FRLogger.warn("Structure.create_board: illegal layer number at '${p_par.scanner.get_scope_identifier()}'")
                return false
            }
            boardLayerArr[i] = app.freerouting.board.Layer(currLayer.name, currLayer.is_signal)
        }
        val boardLayerStructure = app.freerouting.board.LayerStructure(boardLayerArr.filterNotNull().toTypedArray())
        p_par.layer_structure = LayerStructure(p_board_construction_info.layer_info)

        // Calculate an approximate scaling between dsn coordinates and board
        // coordinates.
        var scaleFactor = Math.max(p_par.resolution, 1)

        var maxCoor = 0.0
        for (i in 0 until 4) {
            maxCoor = Math.max(maxCoor, Math.abs(boundingBox.coor[i] * p_par.resolution))
        }
        if (maxCoor == 0.0) {
            p_par.board_outline_ok = false
            return false
        }
        // make scalefactor smaller, if there is a danger of integer overflow.
        while (5 * maxCoor >= Limits.CRIT_INT) {
            scaleFactor /= 10
            maxCoor /= 10.0
        }

        val coordinateTransform = CoordinateTransform(scaleFactor.toDouble(), 0.0, 0.0)
        p_par.coordinate_transform = coordinateTransform

        val shapeBounds = (boundingBox.transform_to_board(coordinateTransform) ?: return false) as IntBox
        val bounds = shapeBounds.offset(1000.0)

        val boardOutlineShapes: MutableCollection<PolylineShape> = LinkedList()
        for (currShape in p_board_construction_info.outline_shapes) {
            var currentShape = currShape
            if (currentShape is PolygonPath) {
                if (currentShape.width != 0.0) {
                    // set the width to 0, because the offset function used in transform_to_board is not implemented for shapes, which are not convex.
                    currentShape = PolygonPath(currentShape.layer, 0.0, currentShape.coordinate_arr)
                }
            }
            val currBoardShape = (currentShape.transform_to_board(coordinateTransform) ?: continue) as PolylineShape
            if (currBoardShape.dimension() > 0) {
                boardOutlineShapes.add(currBoardShape)
            }
        }
        if (boardOutlineShapes.isEmpty()) {
            // construct an outline from the bounding_shape, if the outline is missing.
            val currBoardShape = (p_board_construction_info.bounding_shape!!
                .transform_to_board(coordinateTransform) ?: return false) as PolylineShape
            boardOutlineShapes.add(currBoardShape)
        }
        val holeShapes = separate_holes(boardOutlineShapes)
        val clearanceMatrix = ClearanceMatrix.get_default_instance(boardLayerStructure, 0)
        val boardRules = BoardRules(boardLayerStructure, clearanceMatrix)
        val specctraParserInfo = Communication.SpecctraParserInfo(
            p_par.string_quote,
            p_par.host_cad, p_par.host_version, p_par.constants, p_par.write_resolution,
            p_par.dsn_file_generated_by_host
        )
        val boardCommunication = Communication(
            p_par.unit, p_par.resolution, specctraParserInfo,
            coordinateTransform, p_par.item_id_no_generator, p_par.observers
        )

        if (boardCommunication.host_is_old_kicad()) {
            FRLogger.warn(
                "Structure.create_board: The DSN file was exported from an old KiCad version that has known compatibility issues. Please update KiCad to version 6 or newer."
            )
        }

        val outlineShapeArr = boardOutlineShapes.toTypedArray()
        update_board_rules(p_par, p_board_construction_info, boardRules)
        boardRules.set_trace_angle_restriction(p_par.snap_angle)
        p_par.board_handling.create_board(
            bounds, boardLayerStructure, outlineShapeArr,
            p_board_construction_info.outline_clearance_class_name, boardRules, boardCommunication
        )

        val board = p_par.board_handling.get_routing_board() ?: return false

        // Insert the holes in the board outline as keepouts.
        for (currOutlineHole in holeShapes) {
            for (i in 0 until boardLayerStructure.arr.size) {
                board.insert_obstacle(currOutlineHole, i, 0, FixedState.SYSTEM_FIXED)
            }
        }

        return true
    }
}
