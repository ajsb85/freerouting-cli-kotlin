package app.freerouting.rules

import app.freerouting.board.ObjectInfoPanel
import app.freerouting.management.TextManager
import java.io.Serializable
import java.util.LinkedList
import java.util.Locale

/**
 * Contains the lists of different ViaInfo's, which can be used in interactive and automatic routing.
 */
class ViaInfos : Serializable, ObjectInfoPanel.Printable {

    private val list: MutableList<ViaInfo> = LinkedList()

    /**
     * Adds a via info consisting of padstack, clearance class and drill_to_smd_allowed. Return false, if the insertion failed, for example if the name existed already.
     */
    fun add(p_via_info: ViaInfo): Boolean {
        if (name_exists(p_via_info.get_name())) {
            return false
        }
        this.list.add(p_via_info)
        return true
    }

    /**
     * Returns the number of different vias, which can be used for routing.
     */
    fun count(): Int {
        return this.list.size
    }

    /**
     * Returns the p_no-th via af the via types, which can be used for routing.
     */
    fun get(p_no: Int): ViaInfo {
        assert(p_no >= 0 && p_no < this.list.size)
        return this.list[p_no]
    }

    /**
     * Returns the via info with name p_name, or null, if no such via exists.
     */
    fun get(p_name: String): ViaInfo? {
        for (curr_via in this.list) {
            if (curr_via.get_name() == p_name) {
                return curr_via
            }
        }
        return null
    }

    /**
     * Returns true, if a via info with name p_name is already wyisting in the list.
     */
    fun name_exists(p_name: String): Boolean {
        for (curr_via in this.list) {
            if (curr_via.get_name() == p_name) {
                return true
            }
        }
        return false
    }

    /**
     * Removes p_via_info from this list. Returns false, if p_via_info was not contained in the list.
     */
    fun remove(p_via_info: ViaInfo): Boolean {
        return this.list.remove(p_via_info)
    }

    override fun print_info(p_window: ObjectInfoPanel, p_locale: Locale) {
        val tm = TextManager(this.javaClass, p_locale)

        p_window.append_bold(tm.getText("vias") + ": ")
        var counter = 0
        var first_time = true
        val max_vias_per_row = 5
        for (curr_via in this.list) {
            if (first_time) {
                first_time = false
            } else {
                p_window.append(", ")
            }
            if (counter == 0) {
                p_window.newline()
                p_window.indent()
            }
            p_window.append(curr_via.get_name(), tm.getText("via_info"), curr_via)
            counter = (counter + 1) % max_vias_per_row
        }
    }
}
