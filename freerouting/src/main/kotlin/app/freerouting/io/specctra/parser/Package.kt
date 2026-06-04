package app.freerouting.io.specctra.parser

import app.freerouting.board.Item
import app.freerouting.core.Padstack
import app.freerouting.logger.FRLogger
import java.io.IOException
import java.util.LinkedList

/**
 * Class for reading and writing package scopes from dsn-files.
 */
class Package(
    @JvmField val name: String,
    @JvmField val pin_info_arr: Array<PinInfo>,
    @JvmField val outline: Collection<Shape>,
    @JvmField val keepouts: Collection<Shape.ReadAreaScopeResult>,
    @JvmField val via_keepouts: Collection<Shape.ReadAreaScopeResult>,
    @JvmField val place_keepouts: Collection<Shape.ReadAreaScopeResult>,
    @JvmField val is_front: Boolean
) {

    class PinInfo(
        @JvmField val padstack_name: String,
        @JvmField val pin_name: String,
        @JvmField val rel_coor: DoubleArray,
        @JvmField val rotation: Double
    )

    companion object {
        @JvmStatic
        fun read_scope(p_scanner: IJFlexScanner, p_layer_structure: LayerStructure): Package? {
            try {
                var is_front = true
                val outline = LinkedList<Shape>()
                val keepouts = LinkedList<Shape.ReadAreaScopeResult>()
                val via_keepouts = LinkedList<Shape.ReadAreaScopeResult>()
                val place_keepouts = LinkedList<Shape.ReadAreaScopeResult>()
                var next_token = p_scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("Package.read_scope: String expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                val package_name = next_token
                p_scanner.set_scope_identifier(package_name)
                val pin_info_list = LinkedList<PinInfo>()
                while (true) {
                    val prev_token = next_token
                    next_token = p_scanner.next_token()

                    if (next_token == null) {
                        FRLogger.warn("Package.read_scope: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }
                    if (next_token === Keyword.CLOSED_BRACKET) {
                        // end of scope
                        break
                    }
                    if (prev_token === Keyword.OPEN_BRACKET) {
                        if (next_token === Keyword.PIN) {
                            val next_pin = read_pin_info(p_scanner) ?: return null
                            pin_info_list.add(next_pin)
                        } else if (next_token === Keyword.SIDE) {
                            is_front = read_placement_side(p_scanner)
                        } else if (next_token === Keyword.OUTLINE) {
                            val curr_shape = Shape.read_scope(p_scanner, p_layer_structure)
                            if (curr_shape != null) {
                                outline.add(curr_shape)
                            }
                            // overread closing bracket
                            next_token = p_scanner.next_token()
                            if (next_token !== Keyword.CLOSED_BRACKET) {
                                FRLogger.warn("Package.read_scope: closed bracket expected at '" + p_scanner.get_scope_identifier() + "'")
                                return null
                            }
                        } else if (next_token === Keyword.KEEPOUT) {
                            val keepout_area = Shape.read_area_scope(p_scanner, p_layer_structure, false)
                            if (keepout_area != null) {
                                keepouts.add(keepout_area)
                            } else {
                                FRLogger.error("Package.read_scope: could not read keepout area of package '$package_name'", null)
                            }
                        } else if (next_token === Keyword.VIA_KEEPOUT) {
                            val keepout_area = Shape.read_area_scope(p_scanner, p_layer_structure, false)
                            if (keepout_area != null) {
                                via_keepouts.add(keepout_area)
                            }
                        } else if (next_token === Keyword.PLACE_KEEPOUT) {
                            val keepout_area = Shape.read_area_scope(p_scanner, p_layer_structure, false)
                            if (keepout_area != null) {
                                place_keepouts.add(keepout_area)
                            }
                        } else {
                            ScopeKeyword.skip_scope(p_scanner)
                        }
                    }
                }
                val pin_info_arr = pin_info_list.toTypedArray()
                return Package(package_name, pin_info_arr, outline, keepouts, via_keepouts, place_keepouts, is_front)
            } catch (e: IOException) {
                FRLogger.error("Package.read_scope: IO error scanning file", e)
                return null
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_par: WriteScopeParameter, p_package: app.freerouting.core.Package) {
            p_par.file.start_scope()
            p_par.file.write("image ")
            p_par.identifier_type.write(p_package.name, p_par.file)
            // write the placement side of the package
            p_par.file.new_line()
            p_par.file.write("(side ")
            if (p_package.is_front) {
                p_par.file.write("front)")
            } else {
                p_par.file.write("back)")
            }
            // write the pins of the package
            val padstacks = p_par.board.library.padstacks
            for (i in 0 until p_package.pin_count()) {
                val curr_pin = p_package.get_pin(i) ?: continue
                p_par.file.new_line()
                p_par.file.write("(pin ")
                val curr_padstack = padstacks?.get(curr_pin.padstack_no)
                if (curr_padstack != null) {
                    p_par.identifier_type.write(curr_padstack.name, p_par.file)
                }
                p_par.file.write(" ")
                p_par.identifier_type.write(curr_pin.name, p_par.file)
                val rel_coor = p_par.coordinate_transform.board_to_dsn(curr_pin.relative_location)
                for (j in rel_coor.indices) {
                    p_par.file.write(" ")
                    p_par.file.write(rel_coor[j].toString())
                }
                val rotation = Math.round(curr_pin.rotation_in_degree).toInt()
                if (rotation != 0) {
                    p_par.file.write("(rotate ")
                    p_par.file.write(rotation.toString())
                    p_par.file.write(")")
                }
                p_par.file.write(")")
            }
            // write the keepouts belonging to the package.
            for (i in p_package.keepout_arr.indices) {
                write_package_keepout(p_package.keepout_arr[i], p_par, false)
            }
            for (i in p_package.via_keepout_arr.indices) {
                write_package_keepout(p_package.via_keepout_arr[i], p_par, true)
            }
            // write the package outline.
            val outline = p_package.outline
            if (outline != null) {
                for (i in outline.indices) {
                    p_par.file.start_scope()
                    p_par.file.write("outline")
                    val curr_outline = p_par.coordinate_transform.board_to_dsn_rel(outline[i], Layer.SIGNAL)
                    if (curr_outline != null) {
                        curr_outline.write_scope(p_par.file, p_par.identifier_type)
                    }
                    p_par.file.end_scope()
                }
            }
            p_par.file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_package_keepout(
            p_keepout: app.freerouting.core.Package.Keepout,
            p_par: WriteScopeParameter,
            p_is_via_keepout: Boolean
        ) {
            val keepout_layer: Layer
            if (p_keepout.layer >= 0) {
                val board_layer = p_par.board.layer_structure.arr[p_keepout.layer]
                keepout_layer = Layer(board_layer.name, p_keepout.layer, board_layer.is_signal)
            } else {
                keepout_layer = Layer.SIGNAL
            }
            val boundary_shape: app.freerouting.geometry.planar.Shape
            val holes: Array<out app.freerouting.geometry.planar.Shape>
            val area = p_keepout.area
            if (area is app.freerouting.geometry.planar.Shape) {
                boundary_shape = area
                holes = emptyArray()
            } else {
                boundary_shape = area.get_border()
                holes = area.get_holes()
            }
            p_par.file.start_scope()
            if (p_is_via_keepout) {
                p_par.file.write("via_keepout")
            } else {
                p_par.file.write("keepout")
            }
            val dsn_shape = p_par.coordinate_transform.board_to_dsn(boundary_shape, keepout_layer)
            if (dsn_shape != null) {
                dsn_shape.write_scope(p_par.file, p_par.identifier_type)
            }
            for (j in holes.indices) {
                val dsn_hole = p_par.coordinate_transform.board_to_dsn(holes[j], keepout_layer)
                if (dsn_hole != null) {
                    dsn_hole.write_hole_scope(p_par.file, p_par.identifier_type)
                }
            }
            p_par.file.end_scope()
        }

        @JvmStatic
        private fun read_pin_info(p_scanner: IJFlexScanner): PinInfo? {
            try {
                // Read the padstack name.
                p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                var next_token = p_scanner.next_token()
                if (next_token !is String && next_token !is Int) {
                    FRLogger.warn("Package.read_pin_info: String or Integer expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                val padstack_name = next_token.toString()
                var rotation = 0.0

                p_scanner.yybegin(SpecctraDsnStreamReader.NAME) // to be able to handle pin names starting with a digit.
                next_token = p_scanner.next_token()
                if (next_token === Keyword.OPEN_BRACKET) {
                    // read the padstack rotation
                    next_token = p_scanner.next_token()
                    if (next_token === Keyword.ROTATE) {
                        rotation = read_rotation(p_scanner)
                    } else {
                        ScopeKeyword.skip_scope(p_scanner)
                    }
                    p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                    next_token = p_scanner.next_token()
                }
                // Read the pin name.
                if (next_token !is String && next_token !is Int) {
                    FRLogger.warn("Package.read_pin_info: String or Integer expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                val pin_name = next_token.toString()

                val pin_coor = DoubleArray(2)
                for (i in 0..1) {
                    next_token = p_scanner.next_token()
                    if (next_token is Double) {
                        pin_coor[i] = next_token
                    } else if (next_token is Int) {
                        pin_coor[i] = next_token.toDouble()
                    } else {
                        FRLogger.warn("Package.read_pin_info: number expected at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }
                }
                // Handle scopes at the end of the pin scope.
                while (true) {
                    val prev_token = next_token
                    next_token = p_scanner.next_token()

                    if (next_token == null) {
                        FRLogger.warn("Package.read_pin_info: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }
                    if (next_token === Keyword.CLOSED_BRACKET) {
                        // end of scope
                        break
                    }
                    if (prev_token === Keyword.OPEN_BRACKET) {
                        if (next_token === Keyword.ROTATE) {
                            rotation = read_rotation(p_scanner)
                        } else {
                            ScopeKeyword.skip_scope(p_scanner)
                        }
                    }
                }
                return PinInfo(padstack_name, pin_name, pin_coor, rotation)
            } catch (e: IOException) {
                FRLogger.error("Package.read_pin_info: IO error while scanning file", e)
                return null
            }
        }

        @JvmStatic
        private fun read_rotation(p_scanner: IJFlexScanner): Double {
            var result = 0.0
            try {
                val next_string = p_scanner.next_string()
                if (next_string != null) {
                    result = next_string.toDouble()
                }

                // Overread the closing bracket.
                val next_token = p_scanner.next_token()
                if (next_token !== Keyword.CLOSED_BRACKET) {
                    FRLogger.warn("Package.read_rotation: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'")
                }
            } catch (e: IOException) {
                FRLogger.error("Package.read_rotation: IO error while scanning file", e)
            }
            return result
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_placement_scope(p_par: WriteScopeParameter, p_package: app.freerouting.core.Package) {
            val board_items = p_par.board.get_items()
            var component_found = false
            for (i in 1..p_par.board.components.count()) {
                val curr_component = p_par.board.components.get(i)
                if (curr_component.get_package() === p_package) {
                    // check, if not all items of the component are deleted
                    var undeleted_item_found = false
                    for (curr_item in board_items) {
                        if (curr_item.get_component_no() == curr_component.no) {
                            undeleted_item_found = true
                            break
                        }
                    }
                    if (undeleted_item_found || !curr_component.is_placed) {
                        if (!component_found) {
                            // write the scope header
                            p_par.file.start_scope()
                            p_par.file.write("component ")
                            p_par.identifier_type.write(p_package.name, p_par.file)
                            component_found = true
                        }
                        Component.write_scope(p_par, curr_component)
                    }
                }
            }
            if (component_found) {
                p_par.file.end_scope()
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun read_placement_side(p_scanner: IJFlexScanner): Boolean {
            var next_token = p_scanner.next_token()
            val result = next_token !== Keyword.BACK

            next_token = p_scanner.next_token()
            if (next_token !== Keyword.CLOSED_BRACKET) {
                FRLogger.warn("Package.read_placement_side: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'")
            }
            return result
        }
    }
}
