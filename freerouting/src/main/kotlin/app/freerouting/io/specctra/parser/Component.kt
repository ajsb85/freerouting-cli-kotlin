package app.freerouting.io.specctra.parser

import app.freerouting.board.BasicBoard
import app.freerouting.board.Item
import app.freerouting.board.ObstacleArea
import app.freerouting.board.Pin
import app.freerouting.core.`Package`
import app.freerouting.logger.FRLogger
import java.io.IOException
import java.util.TreeMap

/**
 * Handles the placement data of a library component.
 */
class Component : ScopeKeyword("component") {

    /**
     * Overwrites the function read_scope in ScopeKeyword
     */
    override fun read_scope(p_par: ReadScopeParameter): Boolean {
        try {
            val component_placement = read_scope(p_par.scanner) ?: return false
            p_par.placement_list.add(component_placement)
        } catch (e: IOException) {
            FRLogger.error("Component.read_scope: IO error scanning file", e)
            return false
        }
        return true
    }

    companion object {
        /**
         * Used also when reading a session file.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun read_scope(p_scanner: IJFlexScanner): ComponentPlacement? {
            val next_token = p_scanner.next_token()
            if (next_token !is String) {
                FRLogger.warn("Component.read_scope: component name expected at '" + p_scanner.get_scope_identifier() + "'")
                return null
            }
            val component_placement = ComponentPlacement(next_token)
            var prev_token: Any? = next_token
            var current_token = p_scanner.next_token()
            while (current_token !== Keyword.CLOSED_BRACKET) {
                if (prev_token === Keyword.OPEN_BRACKET && current_token === Keyword.PLACE) {
                    val next_location = read_place_scope(p_scanner)
                    if (next_location != null) {
                        component_placement.locations.add(next_location)
                    }
                }
                prev_token = current_token
                current_token = p_scanner.next_token()
            }
            return component_placement
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_par: WriteScopeParameter, p_component: app.freerouting.board.Component) {
            p_par.file.start_scope()
            p_par.file.write("place ")
            p_par.file.new_line()
            p_par.identifier_type.write(p_component.name, p_par.file)
            if (p_component.is_placed) {
                val coor = p_par.coordinate_transform.board_to_dsn(p_component.get_location().to_float())
                for (i in coor.indices) {
                    p_par.file.write(" ")
                    p_par.file.write(coor[i].toString())
                }
                if (p_component.placed_on_front()) {
                    p_par.file.write(" front ")
                } else {
                    p_par.file.write(" back ")
                }
                val rotation = Math.round(p_component.get_rotation_in_degree()).toInt()
                p_par.file.write(rotation.toString())
            }
            if (p_component.position_fixed) {
                p_par.file.new_line()
                p_par.file.write(" (lock_type position)")
            }
            val pin_count = p_component.get_package().pin_count()
            for (i in 0 until pin_count) {
                write_pin_info(p_par, p_component, i)
            }
            write_keepout_infos(p_par, p_component)
            p_par.file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_pin_info(
            p_par: WriteScopeParameter,
            p_component: app.freerouting.board.Component,
            p_pin_no: Int
        ) {
            if (!p_component.is_placed) {
                return
            }
            val package_pin = p_component.get_package().get_pin(p_pin_no)
            if (package_pin == null) {
                FRLogger.warn("Component.write_pin_info: package pin not found at '" + p_component.name + "'")
                return
            }
            val component_pin = p_par.board.get_pin(p_component.no, p_pin_no)
            if (component_pin == null) {
                FRLogger.warn("Component.write_pin_info: component pin not found at '" + p_component.name + "'")
                return
            }
            val cl_class_name = p_par.board.rules.clearance_matrix.get_name(component_pin.clearance_class_no())
            if (cl_class_name == null) {
                FRLogger.warn("Component.write_pin_info: clearance class  name not found at '" + p_component.name + "'")
                return
            }
            p_par.file.new_line()
            p_par.file.write("(pin ")
            p_par.identifier_type.write(package_pin.name, p_par.file)
            p_par.file.write(" (clearance_class ")
            p_par.identifier_type.write(cl_class_name, p_par.file)
            p_par.file.write("))")
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_keepout_infos(p_par: WriteScopeParameter, p_component: app.freerouting.board.Component) {
            if (!p_component.is_placed) {
                return
            }
            for (j in 0..2) {
                val curr_keepout_arr = when (j) {
                    0 -> p_component.get_package().keepout_arr
                    1 -> p_component.get_package().via_keepout_arr
                    else -> p_component.get_package().place_keepout_arr
                }
                val keepout_type = when (j) {
                    0 -> "(keepout "
                    1 -> "(via_keepout "
                    else -> "(place_keepout "
                }
                for (i in curr_keepout_arr.indices) {
                    val curr_keepout = curr_keepout_arr[i]
                    val curr_obstacle_area = get_keepout(p_par.board, p_component.no, curr_keepout.name)
                    if (curr_obstacle_area == null || curr_obstacle_area.clearance_class_no() == 0) {
                        continue
                    }
                    val cl_class_name =
                        p_par.board.rules.clearance_matrix.get_name(curr_obstacle_area.clearance_class_no())
                    if (cl_class_name == null) {
                        FRLogger.warn("Component.write_keepout_infos: clearance class name not found at '" + p_component.name + "'")
                        return
                    }
                    p_par.file.new_line()
                    p_par.file.write(keepout_type)
                    p_par.identifier_type.write(curr_keepout.name, p_par.file)
                    p_par.file.write(" (clearance_class ")
                    p_par.identifier_type.write(cl_class_name, p_par.file)
                    p_par.file.write("))")
                }
            }
        }

        @JvmStatic
        private fun get_keepout(p_board: BasicBoard, p_component_no: Int, p_name: String): ObstacleArea? {
            val `it` = p_board.item_list.start_read_object()
            while (true) {
                val curr_item = p_board.item_list.read_object(`it`) as Item? ?: break
                if (curr_item.get_component_no() == p_component_no && curr_item is ObstacleArea) {
                    if (curr_item.name != null && curr_item.name == p_name) {
                        return curr_item
                    }
                }
            }
            return null
        }

        @JvmStatic
        internal fun read_place_scope(p_scanner: IJFlexScanner): ComponentPlacement.ComponentLocation? {
            try {
                val pin_infos = TreeMap<String, ComponentPlacement.ItemClearanceInfo>()
                val keepout_infos = TreeMap<String, ComponentPlacement.ItemClearanceInfo>()
                val via_keepout_infos = TreeMap<String, ComponentPlacement.ItemClearanceInfo>()
                val place_keepout_infos = TreeMap<String, ComponentPlacement.ItemClearanceInfo>()

                val name = p_scanner.next_string(true) ?: ""

                var next_token: Any?
                val location = DoubleArray(2)
                for (i in 0..1) {
                    next_token = p_scanner.next_token()
                    if (next_token is Double) {
                        location[i] = next_token
                    } else if (next_token is Int) {
                        location[i] = next_token.toDouble()
                    } else if (next_token === Keyword.CLOSED_BRACKET) {
                        // component is not yet placed
                        return ComponentPlacement.ComponentLocation(
                            name,
                            DoubleArray(0), // empty instead of null to prevent potential Kotlin NullPointerExceptions on coor
                            true,
                            0.0,
                            false,
                            pin_infos,
                            keepout_infos,
                            via_keepout_infos,
                            place_keepout_infos
                        )
                    } else {
                        FRLogger.warn("Component.read_place_scope: Double was expected as the second and third parameter of the component/place command at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }
                }

                next_token = p_scanner.next_token()
                var is_front = true
                if (next_token === Keyword.BACK) {
                    is_front = false
                } else if (next_token !== Keyword.FRONT) {
                    FRLogger.warn("Component.read_place_scope: Keyword.FRONT expected at '" + p_scanner.get_scope_identifier() + "'")
                }
                val rotation: Double
                next_token = p_scanner.next_token()
                if (next_token is Double) {
                    rotation = next_token
                } else if (next_token is Int) {
                    rotation = next_token.toDouble()
                } else {
                    FRLogger.warn("Component.read_place_scope: number expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                var position_fixed = false
                next_token = p_scanner.next_token()
                while (next_token === Keyword.OPEN_BRACKET) {
                    next_token = p_scanner.next_token()
                    if (next_token === Keyword.LOCK_TYPE) {
                        position_fixed = read_lock_type(p_scanner)
                    } else if (next_token === Keyword.PIN) {
                        val curr_pin_info = read_item_clearance_info(p_scanner) ?: return null
                        pin_infos[curr_pin_info.name] = curr_pin_info
                    } else if (next_token === Keyword.KEEPOUT) {
                        val curr_keepout_info = read_item_clearance_info(p_scanner) ?: return null
                        keepout_infos[curr_keepout_info.name] = curr_keepout_info
                    } else if (next_token === Keyword.VIA_KEEPOUT) {
                        val curr_keepout_info = read_item_clearance_info(p_scanner) ?: return null
                        via_keepout_infos[curr_keepout_info.name] = curr_keepout_info
                    } else if (next_token === Keyword.PLACE_KEEPOUT) {
                        val curr_keepout_info = read_item_clearance_info(p_scanner) ?: return null
                        place_keepout_infos[curr_keepout_info.name] = curr_keepout_info
                    } else {
                        skip_scope(p_scanner)
                    }
                    next_token = p_scanner.next_token()
                }
                if (next_token !== Keyword.CLOSED_BRACKET) {
                    FRLogger.warn("Component.read_place_scope: ) expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                return ComponentPlacement.ComponentLocation(
                    name,
                    location,
                    is_front,
                    rotation,
                    position_fixed,
                    pin_infos,
                    keepout_infos,
                    via_keepout_infos,
                    place_keepout_infos
                )
            } catch (e: IOException) {
                FRLogger.error("Component.read_scope: IO error scanning file", e)
                return null
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun read_item_clearance_info(p_scanner: IJFlexScanner): ComponentPlacement.ItemClearanceInfo? {
            p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
            var next_token = p_scanner.next_token()
            if (next_token !is String) {
                FRLogger.warn("Component.read_item_clearance_info: String expected at '" + p_scanner.get_scope_identifier() + "'")
                return null
            }
            val name = next_token
            var cl_class_name: String? = null
            next_token = p_scanner.next_token()
            while (next_token === Keyword.OPEN_BRACKET) {
                next_token = p_scanner.next_token()
                if (next_token === Keyword.CLEARANCE_CLASS) {
                    cl_class_name = DsnFile.read_string_scope(p_scanner)
                } else {
                    skip_scope(p_scanner)
                }
                next_token = p_scanner.next_token()
            }
            if (next_token !== Keyword.CLOSED_BRACKET) {
                FRLogger.warn("Component.read_item_clearance_info: ) expected at '" + p_scanner.get_scope_identifier() + "'")
                return null
            }
            if (cl_class_name == null) {
                FRLogger.warn("Component.read_item_clearance_info: clearance class name not found at '" + p_scanner.get_scope_identifier() + "'")
                return null
            }
            return ComponentPlacement.ItemClearanceInfo(name, cl_class_name)
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun read_lock_type(p_scanner: IJFlexScanner): Boolean {
            var result = false
            while (true) {
                val next_token = p_scanner.next_token()
                if (next_token === Keyword.CLOSED_BRACKET) {
                    break
                }
                if (next_token === Keyword.POSITION) {
                    result = true
                }
            }
            return result
        }
    }
}
