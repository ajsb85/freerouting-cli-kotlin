package app.freerouting.boardgraphics

import app.freerouting.management.TextManager
import java.awt.Color
import java.io.IOException
import java.io.ObjectInputStream
import java.io.Serializable
import java.util.Locale

/**
 * Stores the colors used for the background and highlighting.
 */
class OtherColorTableModel : ColorTableModel, Serializable {

    constructor(p_locale: Locale) : super(1, p_locale) {
        data[0] = arrayOfNulls<Any?>(ColumnNames.values().size)
        val curr_row = data[0]
        curr_row[ColumnNames.BACKGROUND.ordinal] = Color(0, 16, 35)
        curr_row[ColumnNames.HIGHLIGHT.ordinal] = Color.white
        curr_row[ColumnNames.INCOMPLETES.ordinal] = Color.white
        curr_row[ColumnNames.OUTLINE.ordinal] = Color(100, 150, 255)
        curr_row[ColumnNames.VIOLATIONS.ordinal] = Color.magenta
        curr_row[ColumnNames.COMPONENT_FRONT.ordinal] = Color(255, 38, 226)
        curr_row[ColumnNames.COMPONENT_BACK.ordinal] = Color(38, 233, 255)
        curr_row[ColumnNames.LENGTH_MATCHING_AREA.ordinal] = Color.green
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    constructor(p_stream: ObjectInputStream) : super(p_stream)

    /**
     * Copy constructor.
     */
    constructor(p_item_color_model: OtherColorTableModel) : super(p_item_color_model.data.size, p_item_color_model.locale) {
        for (i in this.data.indices) {
            this.data[i] = arrayOfNulls<Any?>(p_item_color_model.data[i].size)
            System.arraycopy(p_item_color_model.data[i], 0, this.data[i], 0, this.data[i].size)
        }
    }

    override fun getColumnCount(): Int {
        return ColumnNames.values().size
    }

    override fun getColumnName(p_col: Int): String {
        val tm = TextManager(ColorTableModel::class.java, this.locale)
        return tm.getText(ColumnNames.values()[p_col].toString())
    }

    override fun isCellEditable(p_row: Int, p_col: Int): Boolean {
        return true
    }

    fun get_background_color(): Color {
        return data[0][ColumnNames.BACKGROUND.ordinal] as Color
    }

    fun set_background_color(p_color: Color) {
        data[0][ColumnNames.BACKGROUND.ordinal] = p_color
    }

    fun get_hilight_color(): Color {
        return data[0][ColumnNames.HIGHLIGHT.ordinal] as Color
    }

    fun set_hilight_color(p_color: Color) {
        data[0][ColumnNames.HIGHLIGHT.ordinal] = p_color
    }

    fun get_incomplete_color(): Color {
        return data[0][ColumnNames.INCOMPLETES.ordinal] as Color
    }

    fun set_incomplete_color(p_color: Color) {
        data[0][ColumnNames.INCOMPLETES.ordinal] = p_color
    }

    fun get_outline_color(): Color {
        return data[0][ColumnNames.OUTLINE.ordinal] as Color
    }

    fun set_outline_color(p_color: Color) {
        data[0][ColumnNames.OUTLINE.ordinal] = p_color
    }

    fun get_violations_color(): Color {
        return data[0][ColumnNames.VIOLATIONS.ordinal] as Color
    }

    fun set_violations_color(p_color: Color) {
        data[0][ColumnNames.VIOLATIONS.ordinal] = p_color
    }

    fun get_component_color(p_front: Boolean): Color {
        return if (p_front) {
            data[0][ColumnNames.COMPONENT_FRONT.ordinal] as Color
        } else {
            data[0][ColumnNames.COMPONENT_BACK.ordinal] as Color
        }
    }

    fun get_length_matching_area_color(): Color {
        return data[0][ColumnNames.LENGTH_MATCHING_AREA.ordinal] as Color
    }

    fun set_length_matching_area_color(p_color: Color) {
        data[0][ColumnNames.LENGTH_MATCHING_AREA.ordinal] = p_color
    }

    fun set_component_color(p_color: Color, p_front: Boolean) {
        if (p_front) {
            data[0][ColumnNames.COMPONENT_FRONT.ordinal] = p_color
        } else {
            data[0][ColumnNames.COMPONENT_BACK.ordinal] = p_color
        }
    }

    private enum class ColumnNames {
        BACKGROUND, HIGHLIGHT, INCOMPLETES, VIOLATIONS, OUTLINE, COMPONENT_FRONT, COMPONENT_BACK, LENGTH_MATCHING_AREA
    }
}
