package app.freerouting.io.specctra.parser

import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.geometry.planar.Area
import app.freerouting.geometry.planar.PolylineArea
import app.freerouting.geometry.planar.PolylineShape
import app.freerouting.logger.FRLogger
import java.io.IOException
import java.util.LinkedList

/**
 * Describes a shape in a Specctra dsn file.
 */
abstract class Shape protected constructor(@JvmField val layer: Layer) {

    /**
     * Writes a shape scope to a Specctra dsn file.
     */
    @Throws(IOException::class)
    abstract fun write_scope(p_file: IndentFileWriter, p_identifier: IdentifierType)

    /**
     * Writes a shape scope to a Specctra session file. In a session file all coordinates must be integer.
     */
    @Throws(IOException::class)
    abstract fun write_scope_int(p_file: IndentFileWriter, p_identifier: IdentifierType)

    @Throws(IOException::class)
    fun write_hole_scope(p_file: IndentFileWriter, p_identifier_type: IdentifierType) {
        p_file.start_scope()
        p_file.write("window")
        this.write_scope(p_file, p_identifier_type)
        p_file.end_scope()
    }

    /**
     * Transforms a specctra dsn shape to a geometry.planar.Shape.
     */
    abstract fun transform_to_board(p_coordinate_transform: CoordinateTransform): app.freerouting.geometry.planar.Shape?

    /**
     * Returns the smallest axis parallel rectangle containing this shape.
     */
    abstract fun bounding_box(): Rectangle?

    /**
     * Transforms the relative (vector) coordinates of a specctra dsn shape to a geometry.planar.Shape.
     */
    abstract fun transform_to_board_rel(p_coordinate_transform: CoordinateTransform): app.freerouting.geometry.planar.Shape?

    /**
     * Contains the result of the function read_area_scope. area_name or clearance_class_name may be null, which means they are not provided.
     */
    class ReadAreaScopeResult(
        @JvmField var area_name: String?,
        @JvmField val shape_list: Collection<Shape>,
        @JvmField val clearance_class_name: String?
    )

    companion object {
        /**
         * Reads shape scope from a Specctra dsn file. If p_layer_structure == null, only Layer.PCB and Layer.Signal are expected, no individual layers.
         */
        @JvmStatic
        fun read_scope(p_scanner: IJFlexScanner, p_layer_structure: LayerStructure?): Shape? {
            var result: Shape? = null
            try {
                var next_token = p_scanner.next_token()
                if (next_token === Keyword.OPEN_BRACKET) {
                    // overread the open bracket
                    next_token = p_scanner.next_token()
                }

                if (next_token === Keyword.RECTANGLE) {
                    result = read_rectangle_scope(p_scanner, p_layer_structure)
                } else if (next_token === Keyword.POLYGON) {
                    result = read_polygon_scope(p_scanner, p_layer_structure)
                } else if (next_token === Keyword.CIRCLE) {
                    result = read_circle_scope(p_scanner, p_layer_structure)
                } else if (next_token === Keyword.POLYGON_PATH) {
                    result = read_polygon_path_scope(p_scanner, p_layer_structure)
                } else {
                    // not a shape scope, skip it.
                    ScopeKeyword.skip_scope(p_scanner)
                }
            } catch (e: IOException) {
                FRLogger.error("Shape.read_scope: IO error scanning file", e)
                return result
            }
            return result
        }

        /**
         * Gets the layer with a certain name from the layer structure
         *
         * @param p_layer_structure Layer structure to scan
         * @param layer_name        Name of the layer to scan for
         * @return Layer object with the defined name
         */
        private fun get_layer(p_layer_structure: LayerStructure?, layer_name: String): Layer? {
            val layer: Layer?
            if (layer_name == Keyword.PCB_SCOPE.get_name()) {
                layer = Layer.PCB
            } else if (layer_name == Keyword.SIGNAL.get_name()) {
                layer = Layer.SIGNAL
            } else {
                if (p_layer_structure == null) {
                    FRLogger.warn("Shape.read_circle_scope: p_layer_structure != null expected")
                    return null
                }

                val layer_no = p_layer_structure.get_no(layer_name)
                if (layer_no < 0 || layer_no >= p_layer_structure.arr.size) {
                    FRLogger.warn("Shape.read_circle_scope: layer with name '$layer_name' not found in layer structure.")
                    return null
                } else {
                    layer = p_layer_structure.arr[layer_no]
                }
            }
            return layer
        }

        /**
         * Reads an object of type PolylinePath from the dsn-file.
         */
        @JvmStatic
        fun read_polyline_path_scope(p_scanner: IJFlexScanner, p_layer_structure: LayerStructure?): PolylinePath? {
            try {
                val layer_name = p_scanner.next_string() ?: return null
                val layer = get_layer(p_layer_structure, layer_name) ?: return null

                var next_token: Any?
                val corner_list = LinkedList<Any>()
                // read the width and the corners of the path
                while (true) {
                    next_token = p_scanner.next_token()
                    if (next_token === Keyword.CLOSED_BRACKET) {
                        break
                    }
                    if (next_token != null) {
                        corner_list.add(next_token)
                    }
                }
                if (corner_list.size < 5) {
                    FRLogger.warn("PolylinePath.read_scope: too few numbers in scope at '${p_scanner.get_scope_identifier()}'")
                    return null
                }
                val it = corner_list.iterator()
                var width = 0.0
                val next_object = it.next()
                if (next_object is Double) {
                    width = next_object
                } else if (next_object is Int) {
                    width = next_object.toDouble()
                } else {
                    FRLogger.warn("PolylinePath.read_scope: number expected at '${p_scanner.get_scope_identifier()}'")
                    return null
                }
                val corner_arr = DoubleArray(corner_list.size - 1)
                for (i in corner_arr.indices) {
                    val obj = it.next()
                    if (obj is Double) {
                        corner_arr[i] = obj
                    } else if (obj is Int) {
                        corner_arr[i] = obj.toDouble()
                    } else {
                        FRLogger.warn("Shape.read_polygon_path_scope: number expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                }
                return PolylinePath(layer, width, corner_arr)
            } catch (e: IOException) {
                FRLogger.error("PolylinePath.read_scope: IO error scanning file", e)
                return null
            }
        }

        /**
         * Reads a shape , which may contain holes from a specctra dsn-file. The first shape in the shape_list of the result is the border of the area. The other shapes in the shape_list are holes
         * (windows).
         */
        @JvmStatic
        fun read_area_scope(p_scanner: IJFlexScanner, p_layer_structure: LayerStructure?, p_skip_window_scopes: Boolean): ReadAreaScopeResult? {
            val shape_list = LinkedList<Shape>()
            var clearance_class_name: String? = null
            var area_name: String? = null
            var result_ok = true
            var next_token: Any?
            try {
                next_token = p_scanner.next_token()
            } catch (e: IOException) {
                FRLogger.warn("Shape.read_area_scope: IO error scanning file at '${p_scanner.get_scope_identifier()}'")
                return null
            }
            if (next_token is String) {
                p_scanner.set_scope_identifier(next_token)
                if (next_token.isNotEmpty()) {
                    area_name = next_token
                }
            }
            val curr_shape = read_scope(p_scanner, p_layer_structure)
            if (curr_shape == null) {
                FRLogger.warn("Shape.read_area_scope: could not read shape at '${p_scanner.get_scope_identifier()}'")
                result_ok = false
            } else {
                shape_list.add(curr_shape)
            }
            next_token = null
            while (true) {
                val prev_token = next_token
                try {
                    next_token = p_scanner.next_token()
                } catch (e: IOException) {
                    FRLogger.error("Shape.read_area_scope: IO error scanning file", e)
                    return null
                }
                if (next_token == null) {
                    FRLogger.warn("Shape.read_area_scope: unexpected end of file at '${p_scanner.get_scope_identifier()}'")
                    return null
                }
                if (next_token === Keyword.CLOSED_BRACKET) {
                    // end of scope
                    break
                }

                if (prev_token === Keyword.OPEN_BRACKET) {
                    // a new scope is expected
                    if (next_token === Keyword.WINDOW && !p_skip_window_scopes) {
                        val hole_shape = read_scope(p_scanner, p_layer_structure)
                        if (hole_shape != null) {
                            shape_list.add(hole_shape)
                        }
                        // overread closing bracket
                        try {
                            next_token = p_scanner.next_token()
                        } catch (e: IOException) {
                            FRLogger.error("Shape.read_area_scope: IO error scanning file", e)
                            return null
                        }
                        if (next_token !== Keyword.CLOSED_BRACKET) {
                            FRLogger.warn("Shape.read_area_scope: closed bracket expected at '${p_scanner.get_scope_identifier()}'")
                            return null
                        }
                    } else if (next_token === Keyword.CLEARANCE_CLASS) {
                        clearance_class_name = DsnFile.read_string_scope(p_scanner)
                    } else {
                        // skip unknown scope
                        ScopeKeyword.skip_scope(p_scanner)
                    }
                }
            }
            if (!result_ok) {
                return null
            }
            return ReadAreaScopeResult(area_name, shape_list, clearance_class_name)
        }

        /**
         * Reads a rectangle scope from a Specctra dsn file. If p_layer_structure == null, only Layer.PCB and Layer.Signal are expected, no individual layers.
         */
        @JvmStatic
        fun read_rectangle_scope(p_scanner: IJFlexScanner, p_layer_structure: LayerStructure?): Rectangle? {
            try {
                val layer_name = p_scanner.next_string() ?: return null
                var rect_layer = get_layer(p_layer_structure, layer_name)
                if (rect_layer == null) {
                    rect_layer = get_layer(p_layer_structure, Keyword.SIGNAL.get_name())
                }

                var next_token: Any?
                val rect_coor = DoubleArray(4)
                // fill the rectangle
                for (i in 0..3) {
                    next_token = p_scanner.next_token()
                    if (next_token is Double) {
                        rect_coor[i] = next_token
                    } else if (next_token is Int) {
                        rect_coor[i] = next_token.toDouble()
                    } else {
                        FRLogger.warn("Shape.read_rectangle_scope: number expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                }
                // overread the closing bracket
                next_token = p_scanner.next_token()
                if (next_token !== Keyword.CLOSED_BRACKET) {
                    FRLogger.warn("Shape.read_rectangle_scope ) expected at '${p_scanner.get_scope_identifier()}'")
                    return null
                }
                if (rect_layer == null) {
                    return null
                }
                return Rectangle(rect_layer, rect_coor)
            } catch (e: IOException) {
                FRLogger.error("Shape.read_rectangle_scope: IO error scanning file", e)
                return null
            }
        }

        /**
         * Reads a closed polygon scope from a Specctra dsn file. If p_layer_structure == null, only Layer.PCB and Layer.Signal are expected, no individual layers.
         */
        @JvmStatic
        fun read_polygon_scope(p_scanner: IJFlexScanner, p_layer_structure: LayerStructure?): Polygon? {
            try {
                var polygon_layer: Layer? = null
                var layer_ok = true
                var next_token = p_scanner.next_token()
                if (next_token === Keyword.PCB_SCOPE) {
                    polygon_layer = Layer.PCB
                } else if (next_token === Keyword.SIGNAL) {
                    polygon_layer = Layer.SIGNAL
                } else {
                    if (p_layer_structure == null) {
                        FRLogger.warn("Shape.read_polygon_scope: only layer types pcb or signal expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                    if (next_token !is String) {
                        FRLogger.warn("Shape.read_polygon_scope: layer name string expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                    val layer_no = p_layer_structure.get_no(next_token)
                    if (layer_no < 0 || layer_no >= p_layer_structure.arr.size) {
                        FRLogger.warn("Shape.read_polygon_scope: layer name '$next_token' not found in layer structure  at '${p_scanner.get_scope_identifier()}'")
                        layer_ok = false
                    } else {
                        polygon_layer = p_layer_structure.arr[layer_no]
                    }
                }

                // overread the aperture width
                next_token = p_scanner.next_token()

                val coor_list = LinkedList<Any>()

                // read the coordinates of the polygon
                while (true) {
                    next_token = p_scanner.next_token()
                    if (next_token === Keyword.OPEN_BRACKET) {
                        // unknown scope
                        ScopeKeyword.skip_scope(p_scanner)
                        next_token = p_scanner.next_token()
                    }
                    if (next_token == null) {
                        FRLogger.warn("Shape.read_polygon_scope: unexpected end of file at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                    if (next_token === Keyword.CLOSED_BRACKET) {
                        break
                    }
                    coor_list.add(next_token)
                }
                if (!layer_ok) {
                    return null
                }
                val coor_arr = DoubleArray(coor_list.size)
                val it = coor_list.iterator()
                for (i in coor_arr.indices) {
                    val next_object = it.next()
                    if (next_object is Double) {
                        coor_arr[i] = next_object
                    } else if (next_object is Int) {
                        coor_arr[i] = next_object.toDouble()
                    } else {
                        FRLogger.warn("Shape.read_polygon_scope: number expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                }
                if (polygon_layer == null) return null
                return Polygon(polygon_layer, coor_arr)
            } catch (e: IOException) {
                FRLogger.error("Rectangle.read_scope: IO error scanning file", e)
                return null
            }
        }

        /**
         * Reads a circle scope from a Specctra dsn file.
         */
        @JvmStatic
        fun read_circle_scope(p_scanner: IJFlexScanner, p_layer_structure: LayerStructure?): Circle? {
            try {
                val layer_name = p_scanner.next_string() ?: return null
                val circle_layer = get_layer(p_layer_structure, layer_name)

                if (circle_layer == null) {
                    FRLogger.warn("Circle.read_circle_scope: layer with name '$layer_name' not found in layer structure at '${p_scanner.get_scope_identifier()}'")
                }

                // fill the coordinates
                var next_token: Any?
                val circle_coor = DoubleArray(3)
                var curr_index = 0
                while (true) {
                    next_token = p_scanner.next_token()
                    if (next_token === Keyword.CLOSED_BRACKET) {
                        break
                    }
                    if (curr_index > 2) {
                        FRLogger.warn("Shape.read_circle_scope: closed bracket expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                    if (next_token is Double) {
                        circle_coor[curr_index] = next_token
                    } else if (next_token is Int) {
                        circle_coor[curr_index] = next_token.toDouble()
                    } else {
                        FRLogger.warn("Shape.read_circle_scope: number expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                    ++curr_index
                }

                if (circle_layer == null) {
                    return null
                }
                return Circle(circle_layer, circle_coor)
            } catch (e: IOException) {
                FRLogger.error("Shape.read_rectangle_scope: IO error scanning file", e)
                return null
            }
        }

        /**
         * Reads an object of type Path from the dsn-file.
         */
        @JvmStatic
        fun read_polygon_path_scope(p_scanner: IJFlexScanner, p_layer_structure: LayerStructure?): PolygonPath? {
            try {
                val layer_name = p_scanner.next_string() ?: return null
                val layer = get_layer(p_layer_structure, layer_name)

                var next_token: Any?
                val corner_list = LinkedList<Any>()
                // read the width and the corners of the path
                while (true) {
                    next_token = p_scanner.next_token()
                    if (next_token === Keyword.OPEN_BRACKET) {
                        // unknown scope
                        ScopeKeyword.skip_scope(p_scanner)
                        next_token = p_scanner.next_token()
                    }
                    if (next_token === Keyword.CLOSED_BRACKET) {
                        break
                    }
                    if (next_token != null) {
                        corner_list.add(next_token)
                    }
                }

                // corner_list contains width + coordinate pairs
                if (corner_list.size < 5) {
                    // Single-point paths are not valid traces, skip them
                    FRLogger.debug("Shape.read_polygon_path_scope: skipping path with too few coordinates (need at least 2 points) at '${p_scanner.get_scope_identifier()}'")
                    return null
                }
                if (layer == null) {
                    return null
                }
                val it = corner_list.iterator()
                var width = 0.0
                val next_object = it.next()
                if (next_object is Double) {
                    width = next_object
                } else if (next_object is Int) {
                    width = next_object.toDouble()
                } else {
                    FRLogger.warn("Shape.read_polygon_path_scope: number expected at '${p_scanner.get_scope_identifier()}'")
                    return null
                }
                val coordinate_arr = DoubleArray(corner_list.size - 1)
                for (i in coordinate_arr.indices) {
                    val obj = it.next()
                    if (obj is Double) {
                        coordinate_arr[i] = obj
                    } else if (obj is Int) {
                        coordinate_arr[i] = obj.toDouble()
                    } else {
                        FRLogger.warn("Shape.read_polygon_path_scope: number expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                }
                return PolygonPath(layer, width, coordinate_arr)
            } catch (e: IOException) {
                FRLogger.error("Shape.read_polygon_path_scope: IO error scanning file", e)
                return null
            }
        }

        /**
         * Transforms a shape with holes to the board coordinate system. The first shape in the Collection p_area is the border, the other shapes are holes of the area.
         */
        @JvmStatic
        fun transform_area_to_board(p_area: Collection<Shape>, p_coordinate_transform: CoordinateTransform): Area? {
            val hole_count = p_area.size - 1
            if (hole_count <= -1) {
                FRLogger.warn("Shape.transform_area_to_board: p_area.size() > 0 expected")
                return null
            }
            val areaIterator = p_area.iterator()
            val boundary = areaIterator.next()
            val boundary_shape = boundary.transform_to_board(p_coordinate_transform) ?: return null
            val result: Area
            if (hole_count == 0) {
                result = boundary_shape
            } else {
                // Area with holes
                if (boundary_shape !is PolylineShape) {
                    FRLogger.warn("Shape.transform_area_to_board: PolylineShape expected")
                    return null
                }
                val holes = Array(hole_count) {
                    val hole_shape = areaIterator.next().transform_to_board(p_coordinate_transform)
                    if (hole_shape !is PolylineShape) {
                        FRLogger.warn("Shape.transform_area_to_board: PolylineShape expected")
                        throw IllegalArgumentException("PolylineShape expected")
                    }
                    hole_shape
                }
                result = PolylineArea(boundary_shape, holes)
            }
            return result
        }

        /**
         * Transforms the relative coordinates of a shape with holes to the board coordinate system. The first shape in the Collection p_area is the border, the other shapes are holes of the area.
         */
        @JvmStatic
        fun transform_area_to_board_rel(p_area: Collection<Shape>, p_coordinate_transform: CoordinateTransform): Area? {
            val hole_count = p_area.size - 1
            if (hole_count <= -1) {
                FRLogger.warn("Shape.transform_area_to_board_rel: p_area.size() > 0 expected")
                return null
            }
            val areaIterator = p_area.iterator()
            val boundary = areaIterator.next()
            val boundary_shape = boundary.transform_to_board_rel(p_coordinate_transform) ?: return null
            val result: Area
            if (hole_count == 0) {
                result = boundary_shape
            } else {
                // Area with holes
                if (boundary_shape !is PolylineShape) {
                    FRLogger.warn("Shape.transform_area_to_board_rel: PolylineShape expected")
                    return null
                }
                val holes = Array(hole_count) {
                    val hole_shape = areaIterator.next().transform_to_board_rel(p_coordinate_transform)
                    if (hole_shape !is PolylineShape) {
                        FRLogger.warn("Shape.transform_area_to_board: PolylineShape expected")
                        throw IllegalArgumentException("PolylineShape expected")
                    }
                    hole_shape
                }
                result = PolylineArea(boundary_shape, holes)
            }
            return result
        }
    }
}
