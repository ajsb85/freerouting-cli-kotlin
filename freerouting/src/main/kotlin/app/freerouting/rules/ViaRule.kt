package app.freerouting.rules

import app.freerouting.board.ObjectInfoPanel
import app.freerouting.core.Padstack
import app.freerouting.management.TextManager
import java.io.Serializable
import java.util.LinkedList
import java.util.Locale

/**
 * Contains an array of vias used for routing. Vias at the beginning of the array are preferred to later vias.
 */
class ViaRule(@JvmField val name: String) : Serializable, ObjectInfoPanel.Printable {

    private val list: MutableList<ViaInfo> = LinkedList()

    fun append_via(p_via: ViaInfo) {
        list.add(p_via)
    }

    /**
     * Removes p_via from the rule. Returns false, if p_via was not contained in the rule.
     */
    fun remove_via(p_via: ViaInfo): Boolean {
        return list.remove(p_via)
    }

    fun via_count(): Int {
        return list.size
    }

    fun get_via(p_index: Int): ViaInfo {
        assert(p_index >= 0 && p_index < list.size)
        return list[p_index]
    }

    override fun toString(): String {
        return this.name
    }

    /**
     * Returns true, if p_via_info is contained in the via list of this rule.
     */
    fun contains(p_via_info: ViaInfo): Boolean {
        for (curr_info in this.list) {
            if (p_via_info === curr_info) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true, if this rule contains a via with padstack p_padstack
     */
    fun contains_padstack(p_padstack: Padstack): Boolean {
        for (curr_info in this.list) {
            if (curr_info.get_padstack() === p_padstack) {
                return true
            }
        }
        return false
    }

    /**
     * Searches a via in this rule with first layer = p_from_layer and last layer = p_to_layer. Returns null, if no such via exists.
     */
    fun get_layer_range(p_from_layer: Int, p_to_layer: Int): ViaInfo? {
        for (curr_info in this.list) {
            if (curr_info.get_padstack().from_layer() == p_from_layer && curr_info.get_padstack().to_layer() == p_to_layer) {
                return curr_info
            }
        }
        return null
    }

    /**
     * Swaps the locations of p_1 and p_2 in the rule. Returns false, if p_1 or p_2 were not found in the list.
     */
    fun swap(p_1: ViaInfo, p_2: ViaInfo): Boolean {
        val index_1 = this.list.indexOf(p_1)
        val index_2 = this.list.indexOf(p_2)
        if (index_1 < 0 || index_2 < 0) {
            return false
        }
        if (index_1 == index_2) {
            return true
        }
        this.list[index_1] = p_2
        this.list[index_2] = p_1
        return true
    }

    override fun print_info(p_window: ObjectInfoPanel, p_locale: Locale) {
        val tm = TextManager(this.javaClass, p_locale)

        p_window.append_bold(tm.getText("via_rule_2") + " ")
        p_window.append_bold(this.name)
        p_window.append_bold(":")
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

    companion object {
        /**
         * Empty via rule. Must not be changed.
         */
        @JvmField
        val EMPTY = ViaRule("empty")
    }
}
