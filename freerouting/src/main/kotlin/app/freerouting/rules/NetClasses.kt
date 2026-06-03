package app.freerouting.rules

import app.freerouting.board.LayerStructure
import java.io.Serializable
import java.util.Vector

/**
 * Contains the array of net classes for interactive routing.
 */
class NetClasses : Serializable {

    private val class_arr = Vector<NetClass>()

    /**
     * Returns the number of classes in this array.
     */
    fun count(): Int {
        return class_arr.size
    }

    /**
     * Returns the net class with index p_index.
     */
    fun get(p_index: Int): NetClass {
        assert(p_index in 0 until class_arr.size)
        return class_arr[p_index]
    }

    /**
     * Returns the net class with name p_name, or null, if no such class exists.
     */
    fun get(p_name: String): NetClass? {
        for (curr_class in this.class_arr) {
            if (curr_class.get_name() == p_name) {
                return curr_class
            }
        }
        return null
    }

    /**
     * Appends a new empty class with name p_name to the class array
     */
    fun append(
        p_name: String,
        p_layer_structure: LayerStructure,
        p_clearance_matrix: ClearanceMatrix,
        p_is_ignored_by_autorouter: Boolean
    ): NetClass {
        val new_class = NetClass(p_name, p_layer_structure, p_clearance_matrix, p_is_ignored_by_autorouter)
        class_arr.add(new_class)
        return new_class
    }

    /**
     * Appends a new empty class to the class array. A name for the class is created internally
     */
    fun append(p_layer_structure: LayerStructure, p_clearance_matrix: ClearanceMatrix): NetClass {
        var new_name: String
        var index = 0
        do {
            ++index
            new_name = "class$index"
        } while (this.get(new_name) != null)
        return append(new_name, p_layer_structure, p_clearance_matrix, false)
    }

    /**
     * Looks, if the list contains a net class with trace half widths all equal to p_trace_half_width, trace clearance class equal to p_trace_clearance_class and via rule equal to p_cia_rule. Returns
     * null, if no such net class was found.
     */
    fun find(p_trace_half_width: Int, p_trace_clearance_class: Int, p_via_rule: ViaRule?): NetClass? {
        for (curr_class in this.class_arr) {
            if (curr_class.get_trace_clearance_class() == p_trace_clearance_class && curr_class.get_via_rule() == p_via_rule) {
                var trace_widths_equal = true
                for (i in 0 until curr_class.layer_count()) {
                    if (curr_class.get_trace_half_width(i) != p_trace_half_width) {
                        trace_widths_equal = false
                        break
                    }
                }
                if (trace_widths_equal) {
                    return curr_class
                }
            }
        }
        return null
    }

    /**
     * Looks, if the list contains a net class with trace half width[i] all equal to p_trace_half_width_arr[i] for 0 <= i < layer_count, trace clearance class equal to
     * p_trace_clearance_class and via rule equal to p_via_rule. Returns null, if no such net class was found.
     */
    fun find(p_trace_half_width_arr: IntArray, p_trace_clearance_class: Int, p_via_rule: ViaRule?): NetClass? {
        for (curr_class in this.class_arr) {
            if (curr_class.get_trace_clearance_class() == p_trace_clearance_class && curr_class.get_via_rule() == p_via_rule && p_trace_half_width_arr.size == curr_class.layer_count()) {
                var trace_widths_equal = true
                for (i in 0 until curr_class.layer_count()) {
                    if (curr_class.get_trace_half_width(i) != p_trace_half_width_arr[i]) {
                        trace_widths_equal = false
                        break
                    }
                }
                if (trace_widths_equal) {
                    return curr_class
                }
            }
        }
        return null
    }

    /**
     * Removes p_net_class from this list. Returns false, if p_net_class was not contained in the list.
     */
    fun remove(p_net_class: NetClass): Boolean {
        return this.class_arr.remove(p_net_class)
    }
}
