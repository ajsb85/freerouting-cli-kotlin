package app.freerouting.rules

import app.freerouting.board.LayerStructure
import app.freerouting.board.ObjectInfoPanel
import app.freerouting.geometry.planar.Point
import app.freerouting.logger.FRLogger
import app.freerouting.management.TextManager
import java.io.Serializable
import java.util.Locale

/**
 * NxN Matrix describing the spacing restrictions between N clearance classes on a fixed set of layers.
 */
class ClearanceMatrix(
    p_class_count: Int,
    private val layer_structure: LayerStructure,
    p_name_arr: Array<String>
) : Serializable {

    private val max_value_on_layer: IntArray // maximum clearance value for each layer

    /**
     * count of clearance classes
     */
    private var class_count: Int = Math.max(p_class_count, 1)
    private var row: Array<Row> // vector of class_count rows of the clearance matrix

    init {
        row = Array(class_count) { i -> Row(p_name_arr[i]) }
        max_value_on_layer = IntArray(layer_structure.arr.size)
    }

    /**
     * Returns the number of the clearance class with the input name, or -1, if no such clearance class exists.
     */
    fun get_no(p_name: String): Int {
        for (i in 0 until class_count) {
            if (row[i].name.equals(p_name, ignoreCase = true)) {
                return i
            }
        }
        return -1
    }

    /**
     * Gets the name of the clearance class with the input number.
     */
    fun get_name(p_cl_class: Int): String? {
        if (p_cl_class < 0 || p_cl_class >= row.size) {
            FRLogger.warn("ClearanceMatrix.get_name: p_cl_class out of range")
            return null
        }
        return row[p_cl_class].name
    }

    /**
     * Sets the value of all clearance classes with number >= 1 to p_value on all layers.
     */
    fun set_default_value(p_value: Int) {
        for (i in layer_structure.arr.indices) {
            set_default_value(i, p_value)
        }
    }

    /**
     * Sets the value of all clearance classes with number >= 1 to p_value on p_layer.
     */
    fun set_default_value(p_layer: Int, p_value: Int) {
        for (i in 1 until class_count) {
            for (j in 1 until class_count) {
                set_value(i, j, p_layer, p_value)
            }
        }
    }

    /**
     * Sets the value of an entry in the clearance matrix to p_value on all layers.
     */
    fun set_value(p_i: Int, p_j: Int, p_value: Int) {
        for (layer in layer_structure.arr.indices) {
            set_value(p_i, p_j, layer, p_value)
        }
    }

    /**
     * Sets the value of an entry in the clearance matrix to p_value on all inner layers.
     */
    fun set_inner_value(p_i: Int, p_j: Int, p_value: Int) {
        for (layer in 1 until layer_structure.arr.size - 1) {
            set_value(p_i, p_j, layer, p_value)
        }
    }

    /**
     * Sets the value of an entry in the clearance matrix to p_value.
     */
    fun set_value(p_i: Int, p_j: Int, p_layer: Int, p_value: Int) {
        val curr_row = row[p_j]
        val curr_entry = curr_row.column[p_i]

        // assure, that the clearance value is positive and even, and round it up, if it is odd
        // NOTE: why does it need to be even?
        var value = Math.max(p_value, 0)
        if (value % 2 != 0) {
            if (value == Int.MAX_VALUE) {
                value--
            } else {
                value++
            }
        }

        curr_entry.layer[p_layer] = value
        curr_row.max_value[p_layer] = Math.max(curr_row.max_value[p_layer], value)
        max_value_on_layer[p_layer] = Math.max(max_value_on_layer[p_layer], value)
    }

    /**
     * Gets the required spacing of clearance classes with index p_i and p_j on p_layer. This value will be always an even integer.
     */
    fun get_value(p_i: Int, p_j: Int, p_layer: Int, p_add_safety_margin: Boolean): Int {
        if (p_i < 0 || p_i >= class_count || p_j < 0 || p_j >= class_count || p_layer < 0 || p_layer >= layer_structure.arr.size) {
            FRLogger.trace(
                "ClearanceMatrix.get_value", "out_of_bounds",
                "Clearance request out of bounds: class_i=" + p_i + " (max=" + (class_count - 1) + ")"
                        + ", class_j=" + p_j + " (max=" + (class_count - 1) + ")"
                        + ", layer=" + p_layer + " (max=" + (layer_structure.arr.size - 1) + ")"
                        + ", returning 0",
                "Clearance Check",
                emptyArray<Point>()
            )
            return 0
        }

        val value_from_the_matrix = row[p_j].column[p_i].layer[p_layer]
        val final_value = if (p_add_safety_margin) value_from_the_matrix + clearance_safety_margin else value_from_the_matrix

        FRLogger.trace(
            "ClearanceMatrix.get_value", "clearance_retrieved",
            "Clearance value: class_i=" + p_i + " (" + (if (p_i < row.size) row[p_i].name else "?") + ")"
                    + ", class_j=" + p_j + " (" + (if (p_j < row.size) row[p_j].name else "?") + ")"
                    + ", layer=" + p_layer + " (" + (if (p_layer < layer_structure.arr.size) layer_structure.arr[p_layer].name else "?") + ")"
                    + ", base_value=" + value_from_the_matrix + " (" + (value_from_the_matrix / 10000.0) + "mm)"
                    + ", safety_margin=" + (if (p_add_safety_margin) clearance_safety_margin else 0)
                    + ", final_value=" + final_value + " (" + (final_value / 10000.0) + "mm)",
            "Clearance Check",
            emptyArray<Point>()
        )

        return final_value
    }

    /**
     * Returns the maximal required spacing of clearance class with index p_i to all other clearance classes on layer p_layer.
     */
    fun max_value(p_i: Int, p_layer: Int): Int {
        var i = Math.max(p_i, 0)
        i = Math.min(i, class_count - 1)
        var layer = Math.max(p_layer, 0)
        layer = Math.min(layer, layer_structure.arr.size - 1)
        return row[i].max_value[layer]
    }

    fun max_value(p_layer: Int): Int {
        var layer = Math.max(p_layer, 0)
        layer = Math.min(layer, layer_structure.arr.size - 1)
        return max_value_on_layer[layer]
    }

    /**
     * Returns true, if the values of the clearance matrix in the p_i-th column and the p_j-th row are not equal on all layers.
     */
    fun is_layer_dependent(p_i: Int, p_j: Int): Boolean {
        val compare_value = row[p_j].column[p_i].layer[0]
        for (l in 1 until layer_structure.arr.size) {
            if (row[p_j].column[p_i].layer[l] != compare_value) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true, if the values of the clearance matrix in the p_i-th column and the p_j-th row are not equal on all inner layers.
     */
    fun is_inner_layer_dependent(p_i: Int, p_j: Int): Boolean {
        if (layer_structure.arr.size <= 2) {
            return false // no inner layers
        }
        val compare_value = row[p_j].column[p_i].layer[1]
        for (l in 2 until layer_structure.arr.size - 1) {
            if (row[p_j].column[p_i].layer[l] != compare_value) {
                return true
            }
        }
        return false
    }

    /**
     * Returns the row with index p_no
     */
    fun get_row(p_no: Int): Row? {
        if (p_no < 0 || p_no >= row.size) {
            FRLogger.warn("ClearanceMatrix.get_row: p_no out of range")
            return null
        }
        return row[p_no]
    }

    fun get_class_count(): Int {
        return class_count
    }

    /**
     * Return the layer count of this clearance matrix;#
     */
    fun get_layer_count(): Int {
        return layer_structure.arr.size
    }

    /**
     * Returns the clearance compensation value of p_clearance_class_no on layer p_layer.
     */
    fun clearance_compensation_value(p_clearance_class_no: Int, p_layer: Int): Int {
        return (get_value(p_clearance_class_no, p_clearance_class_no, p_layer, false) + 1) / 2
    }

    /**
     * Appends a new clearance class to the clearance matrix and initializes it with the values of the default class. Returns false, oif a clearance class with name p_class_name is already existing.
     */
    fun append_class(p_class_name: String): Boolean {
        if (get_no(p_class_name) >= 0) {
            return false
        }
        val old_class_count = class_count
        ++class_count

        val new_row = Array(class_count) { i ->
            if (i < old_class_count) {
                val curr_old_row = row[i]
                val r = Row(curr_old_row.name)
                r.max_value = curr_old_row.max_value
                System.arraycopy(curr_old_row.column, 0, r.column, 0, old_class_count)
                r.column[old_class_count] = MatrixEntry()
                r
            } else {
                Row(p_class_name)
            }
        }

        row = new_row

        // Set the new matrix elements to default values.
        for (i in 0 until old_class_count) {
            for (j in layer_structure.arr.indices) {
                val default_value = get_value(1, i, j, false)
                set_value(old_class_count, i, j, default_value)
                set_value(i, old_class_count, j, default_value)
            }
        }

        for (j in layer_structure.arr.indices) {
            val default_value = get_value(1, 1, j, false)
            set_value(old_class_count, old_class_count, j, default_value)
        }
        return true
    }

    /**
     * Removes the class with index p_index from the clearance matrix.
     */
    fun remove_class(p_index: Int) {
        val old_class_count = class_count
        --class_count

        val new_row = Array(class_count) { i ->
            val old_i = if (i >= p_index) i + 1 else i
            val curr_old_row = row[old_i]
            val r = Row(curr_old_row.name)

            var new_column_index = 0
            for (j in 0 until old_class_count) {
                if (j == p_index) {
                    continue
                }
                r.column[new_column_index] = curr_old_row.column[j]
                ++new_column_index
            }
            r
        }
        row = new_row
    }

    /**
     * Returns true, if all clearance values of the class with index p_1 are equal to the clearance values of index p_2.
     */
    fun is_equal(p_1: Int, p_2: Int): Boolean {
        if (p_1 == p_2) {
            return true
        }
        if (p_1 < 0 || p_2 < 0 || p_1 >= class_count || p_2 >= class_count) {
            return false
        }
        val row_1 = row[p_1]
        val row_2 = row[p_2]
        for (i in 1 until class_count) {
            if (!row_1.column[i].equals(row_2.column[i])) {
                return false
            }
        }
        return true
    }

    /**
     * contains a row of entries of the clearance matrix
     */
    inner class Row(val name: String) : ObjectInfoPanel.Printable, Serializable {
        val column: Array<MatrixEntry> = Array(class_count) { MatrixEntry() }
        var max_value: IntArray = IntArray(layer_structure.arr.size)

        override fun print_info(p_window: ObjectInfoPanel, p_locale: Locale) {
            val tm = TextManager(this::class.java, p_locale)

            p_window.append_bold(tm.getText("spacing_from_clearance_class") + " ")
            p_window.append_bold(name)
            for (i in 1 until column.size) {
                p_window.newline()
                p_window.indent()
                p_window.append(" " + tm.getText("to_class") + " ")
                p_window.append(row[i].name)
                val curr_column = column[i]
                if (curr_column.is_layer_dependent()) {
                    p_window.append(" " + tm.getText("on_layer") + " ")
                    for (j in layer_structure.arr.indices) {
                        p_window.newline()
                        p_window.indent()
                        p_window.indent()
                        p_window.append(layer_structure.arr[j].name)
                        p_window.append(" = ")
                        p_window.append(curr_column.layer[j].toDouble())
                    }
                } else {
                    p_window.append(" = ")
                    p_window.append(curr_column.layer[0].toDouble())
                }
            }
        }
    }

    /**
     * a single entry of the clearance matrix
     */
    inner class MatrixEntry : Serializable {
        val layer: IntArray = IntArray(layer_structure.arr.size)

        fun equals(p_other: MatrixEntry): Boolean {
            for (i in layer_structure.arr.indices) {
                if (layer[i] != p_other.layer[i]) {
                    return false
                }
            }
            return true
        }

        fun is_layer_dependent(): Boolean {
            val compare_value = layer[0]
            for (i in 1 until layer_structure.arr.size) {
                if (layer[i] != compare_value) {
                    return true
                }
            }
            return false
        }
    }

    companion object {
        @JvmField
        val clearance_safety_margin: Int = 16

        /**
         * Creates a new instance with the 2 clearance classes "none" and "default" and initializes it with p_default_value.
         */
        @JvmStatic
        fun get_default_instance(p_layer_structure: LayerStructure, p_default_value: Int): ClearanceMatrix {
            val name_arr = arrayOf("null", "default")
            val result = ClearanceMatrix(2, p_layer_structure, name_arr)
            result.set_default_value(p_default_value)
            return result
        }
    }
}
