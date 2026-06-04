package app.freerouting.boardgraphics

import app.freerouting.board.LayerStructure
import app.freerouting.management.TextManager
import java.awt.Color
import java.io.IOException
import java.io.ObjectInputStream
import java.io.Serializable
import java.util.Locale

/**
 * Stores the layer dependent colors used for drawing for the items on the board.
 */
class ItemColorTableModel : ColorTableModel, Serializable {

    @Transient
    private var item_colors_precalculated: Boolean = false
    @Transient
    private var precalculated_item_colors: Array<Array<Color>>? = null

    // Create the default color table for the layers
    constructor(p_layer_structure: LayerStructure, p_locale: Locale) : super(p_layer_structure.arr.size, p_locale) {
        val row_count = p_layer_structure.arr.size
        val item_type_count = ColumnNames.values().size - 1
        var signal_layer_no = 0
        for (layer in 0 until row_count) {
            val is_signal_layer = p_layer_structure.arr[layer].is_signal
            data[layer] = arrayOfNulls<Any?>(item_type_count + 1)
            val curr_row = data[layer]
            curr_row[0] = p_layer_structure.arr[layer].name
            if (layer == 0) {
                // F.Cu
                curr_row[ColumnNames.PINS.ordinal] = Color(227, 183, 46)
                curr_row[ColumnNames.TRACES.ordinal] = Color(200, 52, 52)
                curr_row[ColumnNames.CONDUCTION_AREAS.ordinal] = Color(0, 150, 0)
                curr_row[ColumnNames.KEEPOUTS.ordinal] = Color(26, 196, 210)
                curr_row[ColumnNames.PLACE_KEEPOUTS.ordinal] = Color(150, 50, 0)
            } else if (layer == row_count - 1) {
                // B.Cu
                curr_row[ColumnNames.PINS.ordinal] = Color(227, 183, 46)
                curr_row[ColumnNames.TRACES.ordinal] = Color(77, 127, 196)
                curr_row[ColumnNames.CONDUCTION_AREAS.ordinal] = Color(100, 100, 0)
                curr_row[ColumnNames.KEEPOUTS.ordinal] = Color(26, 196, 210)
                curr_row[ColumnNames.PLACE_KEEPOUTS.ordinal] = Color(160, 80, 0)
            } else {
                // Inner layers like In1.Cu, In2.Cu, etc.
                if (is_signal_layer) {
                    // currently 6 different default colors for traces on the inner layers
                    val different_inner_colors = 6
                    val remainder = signal_layer_no % different_inner_colors
                    curr_row[ColumnNames.TRACES.ordinal] = when (remainder % different_inner_colors) {
                        1 -> Color(127, 200, 127)
                        2 -> Color(206, 125, 44)
                        3 -> Color(79, 203, 203)
                        4 -> Color(219, 98, 139)
                        5 -> Color(167, 165, 198)
                        else -> Color(40, 204, 217)
                    }
                } else {
                    // power layer
                    curr_row[ColumnNames.TRACES.ordinal] = Color.BLACK
                }
                curr_row[ColumnNames.PINS.ordinal] = Color(255, 150, 0)
                curr_row[ColumnNames.CONDUCTION_AREAS.ordinal] = Color(0, 200, 60)
                curr_row[ColumnNames.KEEPOUTS.ordinal] = Color(26, 196, 210)
                curr_row[ColumnNames.PLACE_KEEPOUTS.ordinal] = Color(150, 50, 0)
            }
            curr_row[ColumnNames.VIAS.ordinal] = Color(227, 183, 46)
            curr_row[ColumnNames.FIXED_VIAS.ordinal] = curr_row[ColumnNames.VIAS.ordinal]
            curr_row[ColumnNames.FIXED_TRACES.ordinal] = curr_row[ColumnNames.TRACES.ordinal]
            curr_row[ColumnNames.VIA_KEEPOUTS.ordinal] = Color(236, 236, 236)
            if (is_signal_layer) {
                ++signal_layer_no
            }
        }
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    constructor(p_stream: ObjectInputStream) : super(p_stream)

    /**
     * Copy constructor.
     */
    constructor(p_item_color_model: ItemColorTableModel) : super(p_item_color_model.data.size, p_item_color_model.locale) {
        for (i in this.data.indices) {
            this.data[i] = arrayOfNulls<Any?>(p_item_color_model.data[i].size)
            System.arraycopy(p_item_color_model.data[i], 0, this.data[i], 0, this.data[i].size)
        }
    }

    override fun getColumnCount(): Int {
        return ColumnNames.values().size
    }

    override fun getRowCount(): Int {
        return data.size
    }

    override fun getColumnName(p_col: Int): String {
        val tm = TextManager(ColorTableModel::class.java, this.locale)
        return tm.getText(ColumnNames.values()[p_col].toString())
    }

    override fun setValueAt(p_value: Any?, p_row: Int, p_col: Int) {
        super.setValueAt(p_value, p_row, p_col)
        this.item_colors_precalculated = false
    }

    /**
     * Don't need to implement this method unless your table's editable.
     */
    override fun isCellEditable(p_row: Int, p_col: Int): Boolean {
        // Note that the data/cell address is constant,
        // no matter where the cell appears onscreen.
        return p_col >= 1
    }

    fun get_trace_colors(p_fixed: Boolean): Array<Color> {
        if (!item_colors_precalculated) {
            precalculate_item_colors()
        }
        val idx = if (p_fixed) ColumnNames.FIXED_TRACES.ordinal - 1 else ColumnNames.TRACES.ordinal - 1
        return precalculated_item_colors!![idx]
    }

    fun get_via_colors(p_fixed: Boolean): Array<Color> {
        if (!item_colors_precalculated) {
            precalculate_item_colors()
        }
        val idx = if (p_fixed) ColumnNames.FIXED_VIAS.ordinal - 1 else ColumnNames.VIAS.ordinal - 1
        return precalculated_item_colors!![idx]
    }

    fun get_pin_colors(): Array<Color> {
        if (!item_colors_precalculated) {
            precalculate_item_colors()
        }
        return precalculated_item_colors!![ColumnNames.PINS.ordinal - 1]
    }

    fun set_pin_colors(p_color_arr: Array<Color>) {
        set_colors(ColumnNames.PINS.ordinal, p_color_arr)
    }

    fun get_conduction_colors(): Array<Color> {
        if (!item_colors_precalculated) {
            precalculate_item_colors()
        }
        return precalculated_item_colors!![ColumnNames.CONDUCTION_AREAS.ordinal - 1]
    }

    fun set_conduction_colors(p_color_arr: Array<Color>) {
        set_colors(ColumnNames.CONDUCTION_AREAS.ordinal, p_color_arr)
    }

    fun get_obstacle_colors(): Array<Color> {
        if (!item_colors_precalculated) {
            precalculate_item_colors()
        }
        return precalculated_item_colors!![ColumnNames.KEEPOUTS.ordinal - 1]
    }

    fun get_via_obstacle_colors(): Array<Color> {
        if (!item_colors_precalculated) {
            precalculate_item_colors()
        }
        return precalculated_item_colors!![ColumnNames.VIA_KEEPOUTS.ordinal - 1]
    }

    fun get_place_obstacle_colors(): Array<Color> {
        if (!item_colors_precalculated) {
            precalculate_item_colors()
        }
        return precalculated_item_colors!![ColumnNames.PLACE_KEEPOUTS.ordinal - 1]
    }

    fun set_trace_colors(p_color_arr: Array<Color>, p_fixed: Boolean) {
        if (p_fixed) {
            set_colors(ColumnNames.FIXED_TRACES.ordinal, p_color_arr)
        } else {
            set_colors(ColumnNames.TRACES.ordinal, p_color_arr)
        }
    }

    fun set_via_colors(p_color_arr: Array<Color>, p_fixed: Boolean) {
        if (p_fixed) {
            set_colors(ColumnNames.FIXED_VIAS.ordinal, p_color_arr)
        } else {
            set_colors(ColumnNames.VIAS.ordinal, p_color_arr)
        }
    }

    fun set_keepout_colors(p_color_arr: Array<Color>) {
        set_colors(ColumnNames.KEEPOUTS.ordinal, p_color_arr)
    }

    fun set_via_keepout_colors(p_color_arr: Array<Color>) {
        set_colors(ColumnNames.VIA_KEEPOUTS.ordinal, p_color_arr)
    }

    fun set_place_keepout_colors(p_color_arr: Array<Color>) {
        set_colors(ColumnNames.PLACE_KEEPOUTS.ordinal, p_color_arr)
    }

    private fun set_colors(p_item_type: Int, p_color_arr: Array<Color>) {
        for (layer in 0 until this.data.size - 1) {
            val color_index = layer % p_color_arr.size
            this.data[layer][p_item_type] = p_color_arr[color_index]
        }
        data[this.data.size - 1][p_item_type] = p_color_arr[p_color_arr.size - 1]
        this.item_colors_precalculated = false
    }

    private fun precalculate_item_colors() {
        val count = ColumnNames.values().size - 1
        val precalculated = Array(count) { Array(data.size) { Color.BLACK } }
        for (i in 0 until count) {
            val curr_row = precalculated[i]
            for (j in data.indices) {
                curr_row[j] = getValueAt(j, i + 1) as Color
            }
        }
        this.precalculated_item_colors = precalculated
        this.item_colors_precalculated = true
    }

    private enum class ColumnNames {
        LAYER, TRACES, FIXED_TRACES, VIAS, FIXED_VIAS, PINS, CONDUCTION_AREAS, KEEPOUTS, VIA_KEEPOUTS, PLACE_KEEPOUTS
    }
}
