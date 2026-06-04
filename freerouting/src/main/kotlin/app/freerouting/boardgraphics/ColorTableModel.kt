package app.freerouting.boardgraphics

import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Locale
import javax.swing.table.AbstractTableModel

/**
 * Abstract class to store colors used for drawing the board.
 */
abstract class ColorTableModel : AbstractTableModel {

    @JvmField
    protected val data: Array<Array<Any?>>
    @JvmField
    protected val locale: Locale

    protected constructor(p_row_count: Int, p_locale: Locale) {
        this.data = Array(p_row_count) { emptyArray() }
        this.locale = p_locale
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    protected constructor(p_stream: ObjectInputStream) {
        @Suppress("UNCHECKED_CAST")
        this.data = p_stream.readObject() as Array<Array<Any?>>
        this.locale = p_stream.readObject() as Locale
    }

    override fun getRowCount(): Int {
        return data.size
    }

    override fun getValueAt(p_row: Int, p_col: Int): Any? {
        return data[p_row][p_col]
    }

    override fun setValueAt(p_value: Any?, p_row: Int, p_col: Int) {
        data[p_row][p_col] = p_value
        fireTableCellUpdated(p_row, p_col)
    }

    /**
     * JTable uses this method to determine the default renderer/ editor for each cell. If we didn't implement this method, then the last column would contain text ("true"/"false"), rather than a check
     * box.
     */
    override fun getColumnClass(p_c: Int): Class<*> {
        return getValueAt(0, p_c)!!.javaClass
    }

    @Throws(IOException::class)
    open fun write_object(p_stream: ObjectOutputStream) {
        p_stream.writeObject(this.data)
        p_stream.writeObject(this.locale)
    }
}
