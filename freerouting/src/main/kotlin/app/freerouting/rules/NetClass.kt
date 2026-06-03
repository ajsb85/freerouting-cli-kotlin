package app.freerouting.rules

import app.freerouting.board.LayerStructure
import app.freerouting.board.ObjectInfoPanel
import app.freerouting.logger.FRLogger
import app.freerouting.management.TextManager
import java.io.Serializable
import java.util.Locale

/**
 * Describes routing rules for individual nets.
 */
class NetClass(
    p_name: String,
    private val board_layer_structure: LayerStructure,
    private val clearance_matrix: ClearanceMatrix,
    @JvmField var is_ignored_by_autorouter: Boolean
) : Serializable, ObjectInfoPanel.Printable {

    private val trace_half_width_arr: IntArray = IntArray(board_layer_structure.arr.size)
    private val active_routing_layer_arr: BooleanArray = BooleanArray(board_layer_structure.arr.size)

    @JvmField
    var default_item_clearance_classes = DefaultItemClearanceClasses()

    private var name: String = p_name
    private var via_rule: ViaRule? = null
    private var trace_clearance_class: Int = 0

    private var shove_fixed: Boolean = false
    private var pull_tight: Boolean = true
    private var ignore_cycles_with_areas: Boolean = false
    private var minimum_trace_length: Double = 0.0
    private var maximum_trace_length: Double = 0.0

    init {
        for (i in board_layer_structure.arr.indices) {
            active_routing_layer_arr[i] = board_layer_structure.arr[i].is_signal
        }
    }

    override fun toString(): String {
        return this.name
    }

    /**
     * Gets the name of this net class.
     */
    fun get_name(): String {
        return this.name
    }

    /**
     * Changes the name of this net class.
     */
    fun set_name(p_name: String) {
        this.name = p_name
    }

    /**
     * Sets the trace half width used for routing to p_value on all layers.
     */
    fun set_trace_half_width(p_value: Int) {
        trace_half_width_arr.fill(p_value)
    }

    /**
     * Sets the trace half width used for routing to p_value on all inner layers.
     */
    fun set_trace_half_width_on_inner(p_value: Int) {
        for (i in 1 until trace_half_width_arr.size - 1) {
            trace_half_width_arr[i] = p_value
        }
    }

    /**
     * Sets the trace half width used for routing to p_value on the input layer.
     */
    fun set_trace_half_width(p_layer: Int, p_value: Int) {
        trace_half_width_arr[p_layer] = p_value
    }

    fun layer_count(): Int {
        return trace_half_width_arr.size
    }

    /**
     * Gets the trace half width used for routing on the input layer.
     */
    fun get_trace_half_width(p_layer: Int): Int {
        if (p_layer < 0 || p_layer >= trace_half_width_arr.size) {
            FRLogger.warn(" NetClass.get_trace_half_width: p_layer out of range")
            return 0
        }
        return trace_half_width_arr[p_layer]
    }

    /**
     * Gets the clearance class used for routing traces with this net class.
     */
    fun get_trace_clearance_class(): Int {
        return this.trace_clearance_class
    }

    /**
     * Sets the clearance class used for routing traces with this net rclass.
     */
    fun set_trace_clearance_class(p_clearance_class_no: Int) {
        this.trace_clearance_class = p_clearance_class_no
    }

    /**
     * Gets the via rule of this net rule.
     */
    fun get_via_rule(): ViaRule? {
        return this.via_rule
    }

    /**
     * Sets the via rule of this net class.
     */
    fun set_via_rule(p_via_rule: ViaRule?) {
        this.via_rule = p_via_rule
    }

    /**
     * Returns, if traces and vias of this net class can be pushed.
     */
    fun is_shove_fixed(): Boolean {
        return this.shove_fixed
    }

    /**
     * Sets, if traces and vias of this net class can be pushed.
     */
    fun set_shove_fixed(p_value: Boolean) {
        this.shove_fixed = p_value
    }

    /**
     * Returns, if traces of this nets class are pulled tight.
     */
    fun get_pull_tight(): Boolean {
        return this.pull_tight
    }

    /**
     * Sets, if traces of this nets class are pulled tight.
     */
    fun set_pull_tight(p_value: Boolean) {
        this.pull_tight = p_value
    }

    /**
     * Returns, if the cycle remove algorithm ignores cycles, where conduction areas are involved
     */
    fun get_ignore_cycles_with_areas(): Boolean {
        return this.ignore_cycles_with_areas
    }

    /**
     * Sets, if the cycle remove algorithm ignores cycles, where conduction areas are involved
     */
    fun set_ignore_cycles_with_areas(p_value: Boolean) {
        this.ignore_cycles_with_areas = p_value
    }

    /**
     * Returns the minimum trace length of this net class. If the result is <= 0, there is no minimal trace length restriction.
     */
    fun get_minimum_trace_length(): Double {
        return minimum_trace_length
    }

    /**
     * Sets the minimum trace length of this net class to p_value. If p_value is <= 0, there is no minimal trace length restriction.
     */
    fun set_minimum_trace_length(p_value: Double) {
        minimum_trace_length = p_value
    }

    /**
     * Returns the maximum trace length of this net class. If the result is <= 0, there is no maximal trace length restriction.
     */
    fun get_maximum_trace_length(): Double {
        return maximum_trace_length
    }

    /**
     * Sets the maximum trace length of this net class to p_value. If p_value is <= 0, there is no maximal trace length restriction.
     */
    fun set_maximum_trace_length(p_value: Double) {
        maximum_trace_length = p_value
    }

    /**
     * Returns if the layer with index p_layer_no is active for routing
     */
    fun is_active_routing_layer(p_layer_no: Int): Boolean {
        if (p_layer_no < 0 || p_layer_no >= this.active_routing_layer_arr.size) {
            return false
        }
        return this.active_routing_layer_arr[p_layer_no]
    }

    /**
     * Sets the layer with index p_layer_no to p_active.
     */
    fun set_active_routing_layer(p_layer_no: Int, p_active: Boolean) {
        if (p_layer_no < 0 || p_layer_no >= this.active_routing_layer_arr.size) {
            return
        }
        this.active_routing_layer_arr[p_layer_no] = p_active
    }

    /**
     * Activates or deactivates all layers for routing
     */
    fun set_all_layers_active(p_value: Boolean) {
        active_routing_layer_arr.fill(p_value)
    }

    /**
     * Activates or deactivates all inner layers for routing
     */
    fun set_all_inner_layers_active(p_value: Boolean) {
        for (i in 1 until trace_half_width_arr.size - 1) {
            active_routing_layer_arr[i] = p_value
        }
    }

    override fun print_info(p_window: ObjectInfoPanel, p_locale: Locale) {
        val tm = TextManager(this.javaClass, p_locale)

        p_window.append_bold(tm.getText("net_class_2") + " ")
        p_window.append_bold(this.name)
        p_window.append_bold(":")
        p_window.append(" " + tm.getText("trace_clearance_class") + " ")
        val cl_name = clearance_matrix.get_name(this.trace_clearance_class)
        p_window.append(cl_name, tm.getText("trace_clearance_class_2"), clearance_matrix.get_row(this.trace_clearance_class) as ObjectInfoPanel.Printable)
        if (this.shove_fixed) {
            p_window.append(", " + tm.getText("shove_fixed"))
        }
        p_window.append(", " + tm.getText("via_rule") + " ")
        val vr = via_rule
        if (vr != null) {
            p_window.append(vr.name, tm.getText("via_rule_2"), vr)
        }
        if (trace_width_is_layer_dependent()) {
            for (i in trace_half_width_arr.indices) {
                p_window.newline()
                p_window.indent()
                p_window.append(tm.getText("trace_width") + " ")
                p_window.append((2 * trace_half_width_arr[i]).toDouble())
                p_window.append(" " + tm.getText("on_layer") + " ")
                p_window.append(this.board_layer_structure.arr[i].name)
            }
        } else {
            p_window.append(", " + tm.getText("trace_width") + " ")
            p_window.append((2 * trace_half_width_arr[0]).toDouble())
        }
        p_window.newline()
    }

    /**
     * Returns true, if the trace width of this class is not equal on all layers.
     */
    fun trace_width_is_layer_dependent(): Boolean {
        val compare_value = trace_half_width_arr[0]
        for (i in 1 until trace_half_width_arr.size) {
            if (this.board_layer_structure.arr[i].is_signal) {
                if (trace_half_width_arr[i] != compare_value) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Returns true, if the trace width of this class is not equal on all inner layers.
     */
    fun trace_width_is_inner_layer_dependent(): Boolean {
        if (trace_half_width_arr.size <= 3) {
            return false
        }
        var first_inner_layer_no = 1
        while (!this.board_layer_structure.arr[first_inner_layer_no].is_signal) {
            ++first_inner_layer_no
        }
        if (first_inner_layer_no >= trace_half_width_arr.size - 1) {
            return false
        }
        val compare_width = trace_half_width_arr[first_inner_layer_no]
        for (i in first_inner_layer_no + 1 until trace_half_width_arr.size - 1) {
            if (this.board_layer_structure.arr[i].is_signal) {
                if (trace_half_width_arr[i] != compare_width) {
                    return true
                }
            }
        }
        return false
    }
}
