package app.freerouting.rules

import app.freerouting.board.BasicBoard
import app.freerouting.board.Connectable
import app.freerouting.board.Item
import app.freerouting.board.ObjectInfoPanel
import app.freerouting.board.ObjectInfoPanel.Printable
import app.freerouting.board.Pin
import app.freerouting.board.Trace
import app.freerouting.board.Via
import app.freerouting.management.TextManager
import java.io.Serializable
import java.util.LinkedList
import java.util.Locale

/**
 * Describes properties for an individual electrical net.
 */
class Net(
    @JvmField val name: String,
    @JvmField val subnet_number: Int,
    @JvmField val net_number: Int,
    @JvmField val net_list: Nets,
    private var contains_plane: Boolean
) : Comparable<Net>, ObjectInfoPanel.Printable, Serializable {

    private var net_class: NetClass = net_list.get_board().rules.get_default_net_class()

    override fun toString(): String {
        return "Net #$net_number ($name)"
    }

    /**
     * Compares 2 nets by name. Useful for example to display nets in alphabetic order.
     */
    override fun compareTo(other: Net): Int {
        return this.name.compareTo(other.name, ignoreCase = true)
    }

    /**
     * Returns the class of this net.
     */
    fun get_class(): NetClass {
        return this.net_class
    }

    /**
     * Sets the class of this net
     */
    fun set_class(p_rule: NetClass) {
        this.net_class = p_rule
    }

    /**
     * Returns the pins and conduction areas of this net.
     */
    fun get_terminal_items(): Collection<Item> {
        val result: MutableCollection<Item> = LinkedList()
        val board = this.net_list.get_board()
        val it = board.item_list.start_read_object()
        while (true) {
            val curr_item = board.item_list.read_object(it) as Item? ?: break
            if (curr_item is Connectable) {
                if (curr_item.contains_net(this.net_number) && !curr_item.is_routable()) {
                    result.add(curr_item)
                }
            }
        }
        return result
    }

    /**
     * Returns the pins of this net.
     */
    fun get_pins(): Collection<Pin> {
        val result: MutableCollection<Pin> = LinkedList()
        val board = this.net_list.get_board()
        val it = board.item_list.start_read_object()
        while (true) {
            val curr_item = board.item_list.read_object(it) as Item? ?: break
            if (curr_item is Pin) {
                if (curr_item.contains_net(this.net_number)) {
                    result.add(curr_item)
                }
            }
        }
        return result
    }

    /**
     * Returns all items of this net.
     */
    fun get_items(): Collection<Item> {
        val result: MutableCollection<Item> = LinkedList()
        val board = this.net_list.get_board()
        val it = board.item_list.start_read_object()
        while (true) {
            val curr_item = board.item_list.read_object(it) as Item? ?: break
            if (curr_item.contains_net(this.net_number)) {
                result.add(curr_item)
            }
        }
        return result
    }

    /**
     * Returns the cumulative trace length of all traces on the board belonging to this net.
     */
    fun get_trace_length(): Double {
        var cumulative_trace_length = 0.0
        val net_items = net_list.get_board().get_connectable_items(this.net_number)
        for (curr_item in net_items) {
            if (curr_item is Trace) {
                cumulative_trace_length += curr_item.get_length()
            }
        }
        return cumulative_trace_length
    }

    /**
     * Returns the count of vias on the board belonging to this net.
     */
    fun get_via_count(): Int {
        var result = 0
        val net_items = net_list.get_board().get_connectable_items(this.net_number)
        for (curr_item in net_items) {
            if (curr_item is Via) {
                ++result
            }
        }
        return result
    }

    fun set_contains_plane(p_value: Boolean) {
        contains_plane = p_value
    }

    /**
     * Indicates, if this net contains a power plane. Used by the autorouter for setting the via costs to the cheap plane via costs. May also be true, if a layer covered with a conduction_area of this
     * net is a signal layer.
     */
    fun contains_plane(): Boolean {
        return contains_plane
    }

    override fun print_info(p_window: ObjectInfoPanel, p_locale: Locale) {
        val via_count = this.get_via_count()
        val cumulative_trace_length = this.get_trace_length()
        val terminal_items = this.get_terminal_items()
        val terminals: MutableCollection<Printable> = LinkedList(terminal_items)
        val terminal_item_count = terminals.size

        val tm = TextManager(this.javaClass, p_locale)

        p_window.append_bold(tm.getText("net") + " ")
        p_window.append_bold(this.name)
        p_window.append_bold(": ")
        p_window.append(tm.getText("class") + " ")
        p_window.append(net_class.get_name(), tm.getText("net_class"), net_class)
        p_window.append(", ")
        p_window.append_objects(terminal_item_count.toString(), tm.getText("terminal_items_2"), terminals)
        p_window.append(" " + tm.getText("terminal_items"))
        p_window.append(", " + tm.getText("via_count") + " ")
        p_window.append(via_count.toString())
        p_window.append(", " + tm.getText("trace_length") + " ")
        p_window.append(cumulative_trace_length)
        p_window.newline()
    }
}
