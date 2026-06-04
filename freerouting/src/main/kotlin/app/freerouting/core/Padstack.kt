package app.freerouting.core

import app.freerouting.board.ObjectInfoPanel
import app.freerouting.geometry.planar.ConvexShape
import app.freerouting.geometry.planar.Direction
import app.freerouting.geometry.planar.IntBox
import app.freerouting.geometry.planar.IntOctagon
import app.freerouting.logger.FRLogger
import app.freerouting.management.TextManager
import java.io.Serializable
import java.util.LinkedList
import java.util.Locale

/**
 * Describes padstack masks for pins or vias located at the origin.
 */
class Padstack(
    @JvmField val name: String,
    @JvmField val no: Int,
    private val shapes: Array<ConvexShape?>,
    @JvmField val attach_allowed: Boolean,
    @JvmField val placed_absolute: Boolean,
    private val padstack_list: Padstacks
) : Comparable<Padstack>, ObjectInfoPanel.Printable, Serializable {

    override fun compareTo(other: Padstack): Int {
        return this.name.compareTo(other.name, ignoreCase = true)
    }

    /**
     * Gets the shape of this padstack on layer p_layer
     */
    fun get_shape(p_layer: Int): ConvexShape? {
        if (p_layer < 0 || p_layer >= shapes.size) {
            FRLogger.warn("Padstack.get_layer p_layer out of range")
            return null
        }
        return shapes[p_layer]
    }

    /**
     * Returns the first layer of this padstack with a shape != null.
     */
    fun from_layer(): Int {
        var result = 0
        while (result < shapes.size && shapes[result] == null) {
            ++result
        }
        return result
    }

    /**
     * Returns the last layer of this padstack with a shape != null.
     */
    fun to_layer(): Int {
        var result = shapes.size - 1
        while (result >= 0 && shapes[result] == null) {
            --result
        }
        return result
    }

    /**
     * Returns the layer count of the board of this padstack.
     */
    fun board_layer_count(): Int {
        return shapes.size
    }

    override fun toString(): String {
        return this.name
    }

    /**
     * Calculates the allowed trace exit directions of the shape of this padstack on layer p_layer. If the length of the pad is smaller than p_factor times the height of the pad, connection also to the
     * long side is allowed.
     */
    fun get_trace_exit_directions(p_layer: Int, p_factor: Double): Collection<Direction> {
        val result = LinkedList<Direction>()
        if (p_layer < 0 || p_layer >= shapes.size) {
            return result
        }
        val curr_shape = shapes[p_layer] ?: return result
        if (curr_shape !is IntBox && curr_shape !is IntOctagon) {
            return result
        }
        val curr_box = curr_shape.bounding_box()

        val all_dirs = Math.max(curr_box.width(), curr_box.height()) < p_factor * Math.min(curr_box.width(), curr_box.height())

        if (all_dirs || curr_box.width() >= curr_box.height()) {
            result.add(Direction.RIGHT)
            result.add(Direction.LEFT)
        }
        if (all_dirs || curr_box.width() <= curr_box.height()) {
            result.add(Direction.UP)
            result.add(Direction.DOWN)
        }
        return result
    }

    override fun print_info(p_window: ObjectInfoPanel, p_locale: Locale) {
        val tm = TextManager(this.javaClass, p_locale)

        p_window.append_bold(tm.getText("padstack") + " ")
        p_window.append_bold(this.name)
        for (i in shapes.indices) {
            val shape = shapes[i]
            if (shape != null) {
                p_window.newline()
                p_window.indent()
                p_window.append(shape, p_locale)
                p_window.append(" " + tm.getText("on_layer") + " ")
                p_window.append(padstack_list.board_layer_structure.arr[i].name)
            }
        }
        p_window.newline()
    }
}
