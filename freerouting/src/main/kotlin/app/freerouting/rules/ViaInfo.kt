package app.freerouting.rules

import app.freerouting.board.ObjectInfoPanel
import app.freerouting.core.Padstack
import app.freerouting.management.TextManager
import java.io.Serializable
import java.util.Locale

/**
 * Information about a combination of via_padstack, via clearance class and drill_to_smd_allowed used in interactive and automatic routing.
 */
class ViaInfo(
    private var name: String,
    private var padstack: Padstack,
    private var clearance_class: Int,
    private var attach_smd_allowed: Boolean,
    private val board_rules: BoardRules
) : Comparable<ViaInfo>, ObjectInfoPanel.Printable, Serializable {

    fun get_name(): String {
        return name
    }

    fun set_name(p_name: String) {
        name = p_name
    }

    override fun toString(): String {
        return this.name
    }

    fun get_padstack(): Padstack {
        return padstack
    }

    fun set_padstack(p_padstack: Padstack) {
        padstack = p_padstack
    }

    fun get_clearance_class(): Int {
        return clearance_class
    }

    fun set_clearance_class(p_clearance_class: Int) {
        clearance_class = p_clearance_class
    }

    fun attach_smd_allowed(): Boolean {
        return attach_smd_allowed
    }

    fun set_attach_smd_allowed(p_attach_smd_allowed: Boolean) {
        attach_smd_allowed = p_attach_smd_allowed
    }

    override fun compareTo(other: ViaInfo): Int {
        return this.name.compareTo(other.name)
    }

    override fun print_info(p_window: ObjectInfoPanel, p_locale: Locale) {
        val tm = TextManager(this.javaClass, p_locale)

        p_window.append_bold(tm.getText("via") + " ")
        p_window.append_bold(this.name)
        p_window.append_bold(": ")
        p_window.append(tm.getText("padstack") + " ")
        p_window.append(this.padstack.name, tm.getText("padstack_info"), this.padstack)
        p_window.append(", " + tm.getText("clearance_class") + " ")
        val curr_name = board_rules.clearance_matrix.get_name(this.clearance_class)
        p_window.append(curr_name, tm.getText("clearance_class_2"), board_rules.clearance_matrix.get_row(this.clearance_class))
        p_window.append(", " + tm.getText("attach_smd") + " ")
        if (attach_smd_allowed) {
            p_window.append(" " + tm.getText("on"))
        } else {
            p_window.append(" " + tm.getText("off"))
        }
        p_window.newline()
    }
}
