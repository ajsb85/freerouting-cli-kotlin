package app.freerouting.io.specctra.parser

import app.freerouting.board.RoutingBoard
import app.freerouting.core.Packages
import app.freerouting.core.Padstack
import app.freerouting.core.Padstacks
import app.freerouting.geometry.planar.Area
import app.freerouting.geometry.planar.ConvexShape
import app.freerouting.geometry.planar.IntVector
import app.freerouting.geometry.planar.PolygonShape
import app.freerouting.geometry.planar.Simplex
import app.freerouting.geometry.planar.TileShape
import app.freerouting.geometry.planar.Vector
import app.freerouting.logger.FRLogger
import java.io.IOException
import java.util.Arrays
import java.util.LinkedList

/**
 * Class for reading and writing library scopes from dsn-files.
 */
class Library : ScopeKeyword("library") {

    override fun read_scope(p_par: ReadScopeParameter): Boolean {
        val board = p_par.board_handling.get_routing_board() ?: return false
        val boardPadstacks = Padstacks(board.layer_structure)
        board.library.padstacks = boardPadstacks
        val packageList = LinkedList<Package>()
        var nextToken: Any? = null
        while (true) {
            val prevToken = nextToken
            try {
                nextToken = p_par.scanner.next_token()
            } catch (e: IOException) {
                FRLogger.error("Library.read_scope: IO error scanning file", e)
                return false
            }
            if (nextToken == null) {
                FRLogger.warn("Library.read_scope: unexpected end of file at '" + p_par.scanner.get_scope_identifier() + "'")
                return false
            }
            if (nextToken === CLOSED_BRACKET) {
                // end of scope
                break
            }
            if (prevToken === OPEN_BRACKET) {
                if (nextToken === Keyword.PADSTACK) {
                    if (!read_padstack_scope(
                            p_par.scanner,
                            p_par.layer_structure!!,
                            p_par.coordinate_transform!!,
                            boardPadstacks
                        )
                    ) {
                        return false
                    }
                } else if (nextToken === Keyword.IMAGE) {
                    val currPackage = Package.read_scope(p_par.scanner, p_par.layer_structure!!) ?: return false
                    packageList.add(currPackage)
                } else {
                    skip_scope(p_par.scanner)
                }
            }
        }

        // Create the library packages on the board
        val boardPackages = Packages(boardPadstacks)
        board.library.packages = boardPackages
        for (currPackage in packageList) {
            val pinArr = Array(currPackage.pin_info_arr.size) { i ->
                val pinInfo = currPackage.pin_info_arr[i]
                val relX = Math.round(p_par.coordinate_transform!!.dsn_to_board(pinInfo.rel_coor[0])).toInt()
                val relY = Math.round(p_par.coordinate_transform!!.dsn_to_board(pinInfo.rel_coor[1])).toInt()
                val relCoor: Vector = IntVector(relX, relY)
                val boardPadstack = boardPadstacks.get(pinInfo.padstack_name)
                if (boardPadstack == null) {
                    FRLogger.warn("Library.read_scope: board padstack not found at '" + p_par.scanner.get_scope_identifier() + "'")
                    return false
                }
                app.freerouting.core.Package.Pin(pinInfo.pin_name, boardPadstack.no, relCoor, pinInfo.rotation)
            }
            val outlineArr = Array<app.freerouting.geometry.planar.Shape?>(currPackage.outline.size) { null }

            val it3 = currPackage.outline.iterator()
            for (i in outlineArr.indices) {
                val currShape = it3.next()
                if (currShape != null) {
                    outlineArr[i] = currShape.transform_to_board_rel(p_par.coordinate_transform!!)
                } else {
                    FRLogger.warn("Library.read_scope: outline shape is null at '" + p_par.scanner.get_scope_identifier() + "'")
                }
            }
            generate_missing_keepout_names("keepout_", currPackage.keepouts)
            generate_missing_keepout_names("via_keepout_", currPackage.via_keepouts)
            generate_missing_keepout_names("place_keepout_", currPackage.place_keepouts)
            
            val keepoutArr = currPackage.keepouts.mapNotNull { currKeepout ->
                val currLayer = currKeepout.shape_list.iterator().next().layer
                val currArea = Shape.transform_area_to_board_rel(currKeepout.shape_list, p_par.coordinate_transform!!) ?: return@mapNotNull null
                app.freerouting.core.Package.Keepout(currKeepout.area_name ?: "", currArea, currLayer.no)
            }.toTypedArray()
            
            val viaKeepoutArr = currPackage.via_keepouts.mapNotNull { currKeepout ->
                val currLayer = currKeepout.shape_list.iterator().next().layer
                val currArea = Shape.transform_area_to_board_rel(currKeepout.shape_list, p_par.coordinate_transform!!) ?: return@mapNotNull null
                app.freerouting.core.Package.Keepout(currKeepout.area_name ?: "", currArea, currLayer.no)
            }.toTypedArray()
            
            val placeKeepoutArr = currPackage.place_keepouts.mapNotNull { currKeepout ->
                val currLayer = currKeepout.shape_list.iterator().next().layer
                val currArea = Shape.transform_area_to_board_rel(currKeepout.shape_list, p_par.coordinate_transform!!) ?: return@mapNotNull null
                app.freerouting.core.Package.Keepout(currKeepout.area_name ?: "", currArea, currLayer.no)
            }.toTypedArray()
            
            boardPackages.add(
                currPackage.name,
                pinArr,
                outlineArr.filterNotNull().toTypedArray(),
                keepoutArr,
                viaKeepoutArr,
                placeKeepoutArr,
                currPackage.is_front
            )
        }
        return true
    }

    private fun generate_missing_keepout_names(
        p_keepout_type: String,
        p_keepout_list: Collection<Shape.ReadAreaScopeResult>
    ) {
        var allNamesExisting = true
        for (currKeepout in p_keepout_list) {
            if (currKeepout.area_name == null) {
                allNamesExisting = false
                break
            }
        }
        if (allNamesExisting) {
            return
        }
        // generate names
        var currNameIndex = 1
        for (currKeepout in p_keepout_list) {
            currKeepout.area_name = p_keepout_type + currNameIndex
            ++currNameIndex
        }
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_par: WriteScopeParameter) {
            p_par.file.start_scope()
            p_par.file.write("library")

            val packages = p_par.board.library.packages
            if (packages != null) {
                for (i in 1..packages.count()) {
                    val pkg = packages.get(i)
                    if (pkg != null) {
                        Package.write_scope(p_par, pkg)
                    }
                }
            }

            val padstacks = p_par.board.library.padstacks
            if (padstacks != null) {
                for (i in 1..padstacks.count()) {
                    val padstack = padstacks.get(i)
                    if (padstack != null) {
                        write_padstack_scope(p_par, padstack)
                    }
                }
            }

            p_par.file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_padstack_scope(p_par: WriteScopeParameter, p_padstack: Padstack) {
            // search the layer range of the padstack
            var firstLayerNo = 0
            while (firstLayerNo < p_par.board.get_layer_count() && p_padstack.get_shape(firstLayerNo) == null) {
                ++firstLayerNo
            }
            var lastLayerNo = p_par.board.get_layer_count() - 1
            while (lastLayerNo >= 0 && p_padstack.get_shape(lastLayerNo) == null) {
                --lastLayerNo
            }
            if (firstLayerNo >= p_par.board.get_layer_count() || lastLayerNo < 0) {
                FRLogger.warn("Library.write_padstack_scope: padstack shape not found at '" + p_padstack.name + "'")
                return
            }

            p_par.file.start_scope()
            p_par.file.write("padstack ")
            p_par.identifier_type.write(p_padstack.name, p_par.file)
            for (i in firstLayerNo..lastLayerNo) {
                val currBoardShape = p_padstack.get_shape(i) ?: continue
                val boardLayer = p_par.board.layer_structure.arr[i]
                val currLayer = Layer(boardLayer.name, i, boardLayer.is_signal)
                val currShape = p_par.coordinate_transform.board_to_dsn_rel(currBoardShape, currLayer) ?: continue
                p_par.file.start_scope()
                p_par.file.write("shape")
                currShape.write_scope(p_par.file, p_par.identifier_type)
                p_par.file.end_scope()
            }
            if (!p_padstack.attach_allowed) {
                p_par.file.new_line()
                p_par.file.write("(attach off)")
            }
            if (p_padstack.placed_absolute) {
                p_par.file.new_line()
                p_par.file.write("(absolute on)")
            }
            p_par.file.end_scope()
        }

        @JvmStatic
        fun read_padstack_scope(
            p_scanner: IJFlexScanner,
            p_layer_structure: LayerStructure,
            p_coordinate_transform: CoordinateTransform,
            p_board_padstacks: Padstacks?
        ): Boolean {
            if (p_board_padstacks == null) return false
            val padstackName: String
            var isDrillable = true
            var placedAbsolute = false
            val shapeList = LinkedList<Shape>()
            try {
                var nextToken = p_scanner.next_token()
                if (nextToken is String) {
                    padstackName = nextToken
                    p_scanner.set_scope_identifier(padstackName)
                } else {
                    FRLogger.warn("Library.read_padstack_scope: unexpected padstack identifier at '" + p_scanner.get_scope_identifier() + "'")
                    return false
                }

                while (nextToken !== Keyword.CLOSED_BRACKET) {
                    val prevToken = nextToken
                    nextToken = p_scanner.next_token()
                    if (prevToken === Keyword.OPEN_BRACKET) {
                        if (nextToken === Keyword.SHAPE) {
                            val currShape = Shape.read_scope(p_scanner, p_layer_structure)
                            if (currShape != null) {
                                shapeList.add(currShape)
                            }
                            // overread the closing bracket and unknown scopes.
                            var currNextToken = p_scanner.next_token()
                            while (currNextToken === Keyword.OPEN_BRACKET) {
                                skip_scope(p_scanner)
                                currNextToken = p_scanner.next_token()
                            }
                            if (currNextToken !== Keyword.CLOSED_BRACKET) {
                                FRLogger.warn("Library.read_padstack_scope: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'")
                                return false
                            }
                        } else if (nextToken === Keyword.ATTACH) {
                            isDrillable = DsnFile.read_on_off_scope(p_scanner)
                        } else if (nextToken === Keyword.ABSOLUTE) {
                            placedAbsolute = DsnFile.read_on_off_scope(p_scanner)
                        } else {
                            skip_scope(p_scanner)
                        }
                    }
                }
            } catch (e: IOException) {
                FRLogger.error("Library.read_padstack_scope: IO error scanning file", e)
                return false
            }
            if (p_board_padstacks.get(padstackName) != null) {
                // Padstack exists already
                return true
            }
            if (shapeList.isEmpty()) {
                FRLogger.warn("Library.read_padstack_scope: shape not found for padstack with name '$padstackName'")
                return true
            }
            val padstackShapes = arrayOfNulls<ConvexShape>(p_layer_structure.arr.size)
            for (padShape in shapeList) {
                var currShape = padShape.transform_to_board_rel(p_coordinate_transform) ?: continue
                var convexShape: ConvexShape?
                if (currShape is ConvexShape) {
                    convexShape = currShape
                } else {
                    if (currShape is PolygonShape) {
                        currShape = currShape.convex_hull()
                    }
                    val convexShapes = currShape.split_to_convex()
                    if (convexShapes.size != 1) {
                        FRLogger.warn("Library.read_padstack_scope: convex shape expected at '" + p_scanner.get_scope_identifier() + "'")
                    }
                    convexShape = convexShapes[0]
                    if (convexShape is Simplex) {
                        convexShape = convexShape.simplify()
                    }
                }
                var padstackShape: ConvexShape? = convexShape
                if (padstackShape != null) {
                    if (padstackShape.dimension() < 2) {
                        FRLogger.warn("Library.read_padstack_scope: the shape of padstack '" + padstackName + "' is not an area. We will enlarge it as a workaround, but it may result unintended consequences.")
                        // enlarge the shape a little bit, so that it is an area
                        padstackShape = padstackShape.offset(1.0)
                        if (padstackShape.dimension() < 2) {
                            padstackShape = null
                        }
                    }
                }

                if (padShape.layer === Layer.PCB || padShape.layer === Layer.SIGNAL) {
                    Arrays.fill(padstackShapes, padstackShape)
                } else {
                    val shapeLayer = p_layer_structure.get_no(padShape.layer.name)
                    if (shapeLayer < 0 || shapeLayer >= padstackShapes.size) {
                        FRLogger.warn("Library.read_padstack_scope: layer number found at '" + p_scanner.get_scope_identifier() + "'")
                        return false
                    }
                    padstackShapes[shapeLayer] = padstackShape
                }
            }
            p_board_padstacks.add(padstackName, padstackShapes, isDrillable, placedAbsolute)
            return true
        }
    }
}
