package app.freerouting.rules

import app.freerouting.board.AngleRestriction
import app.freerouting.board.Item
import app.freerouting.board.LayerStructure
import app.freerouting.core.Padstack
import app.freerouting.geometry.planar.ConvexShape
import app.freerouting.logger.FRLogger
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.Vector

/**
 * Contains the rules and constraints required for items to be inserted into a routing board
 */
class BoardRules(
    private val layer_structure: LayerStructure,
    @JvmField val clearance_matrix: ClearanceMatrix
) : Serializable {

    @JvmField val nets: Nets = Nets()
    @JvmField val via_infos: ViaInfos = ViaInfos()
    @JvmField val via_rules: Vector<ViaRule> = Vector()
    @JvmField val net_classes: NetClasses = NetClasses()

    /**
     * The angle restriction for traces: 90 degree, 45 degree or none.
     */
    @Transient
    private var trace_angle_restriction: AngleRestriction = AngleRestriction.FORTYFIVE_DEGREE

    /**
     * If true, the router ignores conduction areas.
     */
    private var ignore_conduction = true

    /**
     * The smallest of all default trace half widths
     */
    private var min_trace_half_width = 100000

    /**
     * The biggest of all default trace half widths
     */
    private var max_trace_half_width = 100

    /**
     * The minimum distance of the pad border to the first turn of a connected trace to a pin with restricted exit directions. If the value is <= 0, there are no exit restrictions.
     */
    private var pin_edge_to_turn_dist = 0.0

    private var use_slow_autoroute_algorithm = false

    init {
        this.min_trace_half_width = 100000
        this.max_trace_half_width = 100
    }

    companion object {
        /**
         * Gets the default item clearance class
         */
        @JvmStatic
        fun default_clearance_class(): Int {
            return 1
        }

        /**
         * For items with no clearances
         */
        @JvmStatic
        fun clearance_class_none(): Int {
            return 0
        }
    }

    /**
     * Returns the trace halfwidth used for routing with the input net on the input layer.
     */
    fun get_trace_half_width(p_net_no: Int, p_layer: Int): Int {
        val curr_net = nets.get(p_net_no) ?: throw IllegalArgumentException("Net $p_net_no not found")
        return curr_net
            .get_class()
            .get_trace_half_width(p_layer)
    }

    /**
     * Returns true, if the trace widths used for routing for the input net are equal on all layers. If p_net_no < 0, the default trace widths for all nets are checked.
     */
    fun trace_widths_are_layer_dependent(p_net_no: Int): Boolean {
        val compare_width = get_trace_half_width(p_net_no, 0)
        for (i in 1 until this.layer_structure.arr.size) {
            if (get_trace_half_width(p_net_no, i) != compare_width) {
                return true
            }
        }
        return false
    }

    /**
     * Returns the smallest of all default trace half widths
     */
    fun get_min_trace_half_width(): Int {
        return min_trace_half_width
    }

    /**
     * Returns the biggest of all default trace half widths
     */
    fun get_max_trace_half_width(): Int {
        return max_trace_half_width
    }

    /**
     * Changes the default trace halfwidth used for routing on the input layer.
     */
    fun set_default_trace_half_width(p_layer: Int, p_value: Int) {
        this
            .get_default_net_class()
            .set_trace_half_width(p_layer, p_value)
        min_trace_half_width = Math.min(min_trace_half_width, p_value)
        max_trace_half_width = Math.max(max_trace_half_width, p_value)
    }

    fun get_default_trace_half_width(p_layer: Int): Int {
        return this
            .get_default_net_class()
            .get_trace_half_width(p_layer)
    }

    /**
     * Changes the default trace halfwidth used for routing on all layers to the input value.
     */
    fun set_default_trace_half_widths(p_value: Int) {
        if (p_value <= 0) {
            FRLogger.warn("BoardRules.set_trace_half_widths: p_value out of range")
            return
        }
        this
            .get_default_net_class()
            .set_trace_half_width(p_value)
        min_trace_half_width = Math.min(min_trace_half_width, p_value)
        max_trace_half_width = Math.max(max_trace_half_width, p_value)
    }

    /**
     * Returns the net rule used for all nets, for which no special rule was set.
     */
    fun get_default_net_class(): NetClass {
        if (this.net_classes.count() <= 0) {
            // net rules not yet initialized
            this.create_default_net_class()
        }
        return this.net_classes.get(0)
    }

    /**
     * Returns an empty new net rule with an internally created name.
     */
    fun get_new_net_class(): NetClass {
        val result = this.net_classes.append(this.layer_structure, this.clearance_matrix)
        result.set_trace_clearance_class(
            this
                .get_default_net_class()
                .get_trace_clearance_class()
        )
        result.set_via_rule(this.get_default_via_rule())
        result.set_trace_half_width(
            this
                .get_default_net_class()
                .get_trace_half_width(0)
        )
        return result
    }

    /**
     * Returns an empty new net rule with an internally created name.
     */
    fun get_new_net_class(p_name: String): NetClass {
        val result = this.net_classes.append(p_name, this.layer_structure, this.clearance_matrix, false)
        result.set_trace_clearance_class(
            this
                .get_default_net_class()
                .get_trace_clearance_class()
        )
        result.set_via_rule(this.get_default_via_rule())
        result.set_trace_half_width(
            this
                .get_default_net_class()
                .get_trace_half_width(0)
        )
        return result
    }

    /**
     * Create a default via rule for p_net_class with name p_name. If more than one via infos with the same layer range are found, only the via info with the smallest pad size is inserted.
     */
    fun create_default_via_rule(p_net_class: NetClass, p_name: String) {
        if (this.via_infos.count() == 0) {
            return
        }
        // Add the rule containing all vias.
        val default_rule = ViaRule(p_name)
        val default_via_cl_class = p_net_class.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.VIA)
        for (i in 0 until this.via_infos.count()) {
            val curr_via_info = this.via_infos.get(i)
            if (curr_via_info.get_clearance_class() == default_via_cl_class) {
                val curr_padstack = curr_via_info.get_padstack()
                val curr_from_layer = curr_padstack.from_layer()
                val curr_to_layer = curr_padstack.to_layer()
                val existing_via = default_rule.get_layer_range(curr_from_layer, curr_to_layer)
                if (existing_via != null) {
                    val new_shape = curr_padstack.get_shape(curr_from_layer)
                    val existing_shape = existing_via
                        .get_padstack()
                        .get_shape(curr_from_layer)
                    if (new_shape.max_width() < existing_shape.max_width()) {
                        // The via with the smallest pad shape is preferred
                        default_rule.remove_via(existing_via)
                        default_rule.append_via(curr_via_info)
                    }
                } else {
                    default_rule.append_via(curr_via_info)
                }
            }
        }
        this.via_rules.add(default_rule)
        p_net_class.set_via_rule(default_rule)
    }

    fun create_default_net_class() {
        // add the default net rule
        val default_net_class = this.net_classes.append("default", this.layer_structure, this.clearance_matrix, false)
        val default_trace_half_width = 1500
        default_net_class.set_trace_half_width(default_trace_half_width)
        default_net_class.set_trace_clearance_class(1)
    }

    /**
     * Appends a new net class initialized with default data and a default name.
     */
    fun append_net_class(): NetClass {
        val new_class = this.net_classes.append(this.layer_structure, this.clearance_matrix)
        val default_class = this.net_classes.get(0)
        new_class.set_via_rule(default_class.get_via_rule())
        new_class.set_trace_half_width(default_class.get_trace_half_width(0))
        new_class.set_trace_clearance_class(default_class.get_trace_clearance_class())
        return new_class
    }

    /**
     * Appends a new net class initialized with default data and returns that class. If a class with p_name exists, this class is returned without appending a new class.
     */
    fun append_net_class(p_name: String): NetClass {
        val found_class = this.net_classes.get(p_name)
        if (found_class != null) {
            return found_class
        }
        val new_class = this.net_classes.append(p_name, this.layer_structure, this.clearance_matrix, false)
        val default_class = this.net_classes.get(0)
        new_class.default_item_clearance_classes = DefaultItemClearanceClasses(default_class.default_item_clearance_classes)
        new_class.set_via_rule(default_class.get_via_rule())
        new_class.set_trace_half_width(default_class.get_trace_half_width(0))
        new_class.set_trace_clearance_class(default_class.get_trace_clearance_class())
        return new_class
    }

    /**
     * Returns the default via rule for routing or null, if no via rule exists.
     */
    fun get_default_via_rule(): ViaRule? {
        if (this.via_rules.isEmpty()) {
            return null
        }
        return this.via_rules.firstElement()
    }

    /**
     * Returns the via rule with name p_name, or null, if no such rule exists.
     */
    fun get_via_rule(p_name: String): ViaRule? {
        for (curr_rule in via_rules) {
            if (curr_rule.name == p_name) {
                return curr_rule
            }
        }
        return null
    }

    /**
     * Changes the clearance class index of all objects on the board with index p_from_no to p_to_no.
     */
    fun change_clearance_class_no(p_from_no: Int, p_to_no: Int, p_board_items: Collection<Item>) {
        for (curr_item in p_board_items) {
            if (curr_item.clearance_class_no() == p_from_no) {
                curr_item.set_clearance_class_no(p_to_no)
            }
        }

        for (i in 0 until this.net_classes.count()) {
            val curr_net_class = this.net_classes.get(i)
            if (curr_net_class.get_trace_clearance_class() == p_from_no) {
                curr_net_class.set_trace_clearance_class(p_to_no)
            }
            for (curr_item_class in DefaultItemClearanceClasses.ItemClass.entries) {
                if (curr_net_class.default_item_clearance_classes.get(curr_item_class) == p_from_no) {
                    curr_net_class.default_item_clearance_classes.set(curr_item_class, p_to_no)
                }
            }
        }

        for (i in 0 until this.via_infos.count()) {
            val curr_via = this.via_infos.get(i)
            if (curr_via.get_clearance_class() == p_from_no) {
                curr_via.set_clearance_class(p_to_no)
            }
        }
    }

    /**
     * Removes the clearance class with number p_index. Returns false, if that was not possible, because there were still items assigned to this class.
     */
    fun remove_clearance_class(p_index: Int, p_board_items: Collection<Item>): Boolean {
        for (curr_item in p_board_items) {
            if (curr_item.clearance_class_no() == p_index) {
                return false
            }
        }
        for (i in 0 until this.net_classes.count()) {
            val curr_net_class = this.net_classes.get(i)
            if (curr_net_class.get_trace_clearance_class() == p_index) {
                return false
            }
            for (curr_item_class in DefaultItemClearanceClasses.ItemClass.entries) {
                if (curr_net_class.default_item_clearance_classes.get(curr_item_class) == p_index) {
                    return false
                }
            }
        }

        for (i in 0 until this.via_infos.count()) {
            val curr_via = this.via_infos.get(i)
            if (curr_via.get_clearance_class() == p_index) {
                return false
            }
        }

        for (curr_item in p_board_items) {
            if (curr_item.clearance_class_no() > p_index) {
                curr_item.set_clearance_class_no(curr_item.clearance_class_no() - 1)
            }
        }

        for (i in 0 until this.net_classes.count()) {
            val curr_net_class = this.net_classes.get(i)
            if (curr_net_class.get_trace_clearance_class() > p_index) {
                curr_net_class.set_trace_clearance_class(curr_net_class.get_trace_clearance_class() - 1)
            }
            for (curr_item_class in DefaultItemClearanceClasses.ItemClass.entries) {
                val curr_class_no = curr_net_class.default_item_clearance_classes.get(curr_item_class)
                if (curr_class_no > p_index) {
                    curr_net_class.default_item_clearance_classes.set(curr_item_class, curr_class_no - 1)
                }
            }
        }

        for (i in 0 until this.via_infos.count()) {
            val curr_via = this.via_infos.get(i)
            if (curr_via.get_clearance_class() > p_index) {
                curr_via.set_clearance_class(curr_via.get_clearance_class() - 1)
            }
        }
        this.clearance_matrix.remove_class(p_index)
        return true
    }

    /**
     * Returns the minimum distance between the pin border and the next corner of a connected trace por a pin with connection restrictions. If the result is <= 0, there are no exit
     * restrictions.
     */
    fun get_pin_edge_to_turn_dist(): Double {
        return this.pin_edge_to_turn_dist
    }

    /**
     * Sets the minimum distance between the pin border and the next corner of a connected trace por a pin with connection restrictions. if p_value is <= 0, there are no exit restrictions.
     */
    fun set_pin_edge_to_turn_dist(p_value: Double) {
        this.pin_edge_to_turn_dist = p_value
    }

    /**
     * If true, the router ignores conduction areas.
     */
    fun get_ignore_conduction(): Boolean {
        return this.ignore_conduction
    }

    /**
     * Tells the router, if conduction areas should be ignored.
     */
    fun set_ignore_conduction(p_value: Boolean) {
        this.ignore_conduction = p_value
    }

    /**
     * The angle restriction for traces: 90 degree, 45 degree or none.
     */
    fun get_trace_angle_restriction(): AngleRestriction {
        return this.trace_angle_restriction
    }

    /**
     * Sets the angle restriction for traces: 90 degree, 45 degree or none.
     */
    fun set_trace_angle_restriction(p_angle_restriction: AngleRestriction) {
        this.trace_angle_restriction = p_angle_restriction
    }

    /**
     * If true, shapes of type Simplex are always used in the autorouter algorithm. If false, shapes of type IntBox are used in 90 degree autorouting and shapes of type IntOctagon are used in 45 degree
     * autorouting.
     */
    fun get_use_slow_autoroute_algorithm(): Boolean {
        return use_slow_autoroute_algorithm
    }

    /**
     * If true, shapes of type Simplex are always used in the autorouter algorithm. If false, shapes of type IntBox are used in 90 degree autorouting and shapes of type IntOctagon are used in 45 degree
     * autorouting.
     */
    fun set_use_slow_autoroute_algorithm(p_value: Boolean) {
        use_slow_autoroute_algorithm = p_value
    }

    /**
     * Returns the Maximum of the diameter of the default via on its first and last layer.
     */
    fun get_default_via_diameter(): Double {
        val default_via_rule = this.get_default_via_rule() ?: return 0.0
        if (default_via_rule.via_count() <= 0) {
            return 0.0
        }
        val via_padstack = default_via_rule
            .get_via(0)
            .get_padstack()
        var curr_shape = via_padstack.get_shape(via_padstack.from_layer())
        var result = curr_shape.max_width()
        curr_shape = via_padstack.get_shape(via_padstack.to_layer())
        result = Math.max(result, curr_shape.max_width())
        return result
    }

    /**
     * Writes an instance of this class to a file
     */
    @Throws(IOException::class)
    private fun writeObject(p_stream: ObjectOutputStream) {
        p_stream.defaultWriteObject()
        p_stream.writeInt(trace_angle_restriction.value)
    }

    /**
     * Reads an instance of this class from a file
     */
    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(p_stream: ObjectInputStream) {
        p_stream.defaultReadObject()
        val snap_angle_no = p_stream.readInt()
        this.trace_angle_restriction = AngleRestriction.valueOf(snap_angle_no)
    }
}
