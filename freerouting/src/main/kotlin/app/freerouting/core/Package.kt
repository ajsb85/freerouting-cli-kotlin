package app.freerouting.core

import app.freerouting.board.ObjectInfoPanel
import app.freerouting.geometry.planar.Area
import app.freerouting.geometry.planar.Shape
import app.freerouting.geometry.planar.Vector as PlanarVector
import app.freerouting.logger.FRLogger
import app.freerouting.management.TextManager
import java.io.Serializable
import java.util.Locale

/**
 * Component package templates describing the padstacks and relative locations of the package pins, and optional other stuff like an outline package keepouts.
 */
class Package(
    @JvmField val name: String,
    @JvmField val no: Int,
    private val pin_arr: Array<Pin>,
    @JvmField val outline: Array<Shape>?,
    @JvmField val keepout_arr: Array<Keepout>,
    @JvmField val via_keepout_arr: Array<Keepout>,
    @JvmField val place_keepout_arr: Array<Keepout>,
    @JvmField val is_front: Boolean,
    private val package_list: Packages
) : Comparable<Package>, ObjectInfoPanel.Printable, Serializable {

    /**
     * Compares 2 packages by name. Useful for example to display packages in alphabetic order.
     */
    override fun compareTo(other: Package): Int {
        return this.name.compareTo(other.name, ignoreCase = true)
    }

    /**
     * Returns the pin with the input number from this package.
     */
    fun get_pin(p_no: Int): Pin? {
        if (p_no < 0 || p_no >= pin_arr.size) {
            FRLogger.warn("Package.get_pin: p_no out of range")
            return null
        }
        return pin_arr[p_no]
    }

    /**
     * Returns the pin number of the pin with the input name from this package, or -1, if no such pin exists Pin numbers are from 0 to pin_count - 1.
     */
    fun get_pin_no(p_name: String): Int {
        for (i in pin_arr.indices) {
            if (pin_arr[i].name == p_name) {
                return i
            }
        }
        return -1
    }

    /**
     * Returns the pin count of this package.
     */
    fun pin_count(): Int {
        return pin_arr.size
    }

    override fun toString(): String {
        return this.name
    }

    override fun print_info(p_window: ObjectInfoPanel, p_locale: Locale) {
        val tm = TextManager(this.javaClass, p_locale)

        p_window.append_bold(tm.getText("package") + " ")
        p_window.append_bold(this.name)
        for (i in this.pin_arr.indices) {
            val curr_pin = this.pin_arr[i]
            p_window.newline()
            p_window.indent()
            p_window.append(tm.getText("pin") + " ")
            p_window.append(curr_pin.name)
            p_window.append(", " + tm.getText("padstack") + " ")
            val curr_padstack = this.package_list.padstack_list.get(curr_pin.padstack_no)
            if (curr_padstack != null) {
                p_window.append(curr_padstack.name, tm.getText("padstack_info"), curr_padstack)
            }
            p_window.append(" " + tm.getText("at") + " ")
            p_window.append(curr_pin.relative_location.to_float())
            p_window.append(", " + tm.getText("rotation") + " ")
            p_window.append_without_transforming(curr_pin.rotation_in_degree)
        }
        p_window.newline()
    }

    /**
     * Describes a pin padstack of a package.
     */
    class Pin(
        @JvmField val name: String,
        @JvmField val padstack_no: Int,
        @JvmField val relative_location: PlanarVector,
        @JvmField val rotation_in_degree: Double
    ) : Serializable

    /**
     * Describes a named keepout belonging to a package,
     */
    class Keepout(
        @JvmField val name: String,
        @JvmField val area: Area,
        @JvmField val layer: Int
    ) : Serializable
}
