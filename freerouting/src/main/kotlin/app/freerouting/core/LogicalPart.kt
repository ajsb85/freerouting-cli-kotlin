package app.freerouting.core

import app.freerouting.board.ObjectInfoPanel
import app.freerouting.logger.FRLogger
import app.freerouting.management.TextManager
import java.io.Serializable
import java.util.Locale

/**
 * Contains information for gate swap and pin swap for a single component.
 */
class LogicalPart(
    @JvmField val name: String,
    @JvmField val no: Int,
    private val part_pin_arr: Array<PartPin>
) : ObjectInfoPanel.Printable, Serializable {

    fun pin_count(): Int {
        return part_pin_arr.size
    }

    /**
     * Returns the pin with index p_no. Pin numbers are from 0 to pin_count - 1
     */
    fun get_pin(p_no: Int): PartPin? {
        if (p_no < 0 || p_no >= part_pin_arr.size) {
            FRLogger.warn("LogicalPart.get_pin: p_no out of range")
            return null
        }
        return part_pin_arr[p_no]
    }

    override fun print_info(p_window: ObjectInfoPanel, p_locale: Locale) {
        val tm = TextManager(this.javaClass, p_locale)

        p_window.append_bold(tm.getText("logical_part_2") + " ")
        p_window.append_bold(this.name)
        for (i in this.part_pin_arr.indices) {
            val curr_pin = this.part_pin_arr[i]
            p_window.newline()
            p_window.indent()
            p_window.append(tm.getText("pin") + " ")
            p_window.append(curr_pin.pin_name)
            p_window.append(", " + tm.getText("gate") + " ")
            p_window.append(curr_pin.gate_name)
            p_window.append(", " + tm.getText("swap_code") + " ")
            val gate_swap_code = curr_pin.gate_swap_code
            p_window.append(gate_swap_code.toString())
            p_window.append(", " + tm.getText("gate_pin") + " ")
            p_window.append(curr_pin.gate_pin_name)
            p_window.append(", " + tm.getText("swap_code") + " ")
            val pin_swap_code = curr_pin.gate_pin_swap_code
            p_window.append(pin_swap_code.toString())
        }
        p_window.newline()
        p_window.newline()
    }

    class PartPin(
        @JvmField val pin_no: Int,
        @JvmField val pin_name: String,
        @JvmField val gate_name: String,
        @JvmField val gate_swap_code: Int,
        @JvmField val gate_pin_name: String,
        @JvmField val gate_pin_swap_code: Int
    ) : Comparable<PartPin>, Serializable {

        override fun compareTo(other: PartPin): Int {
            return this.pin_no - other.pin_no
        }
    }
}
