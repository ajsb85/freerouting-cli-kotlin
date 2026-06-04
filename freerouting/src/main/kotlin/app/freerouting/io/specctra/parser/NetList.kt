package app.freerouting.io.specctra.parser

import java.util.LinkedList
import java.util.TreeMap

/**
 * Describes a list of nets sorted by its names. The net number is generated internally.
 */
class NetList {

    /**
     * The entries of this map are of type Net, the keys are the net_ids.
     */
    private val nets: MutableMap<Net.Id, Net> = TreeMap()

    /**
     * Returns true, if the netlist contains a net with the input name.
     */
    fun contains(p_net_id: Net.Id): Boolean {
        return nets.containsKey(p_net_id)
    }

    /**
     * Adds a new net mit the input name to the net list. Returns null, if a net with p_name already exists in the net list. In this case no new net is added.
     */
    fun add_net(p_net_id: Net.Id): Net? {
        val result: Net?
        if (nets.containsKey(p_net_id)) {
            result = null
        } else {
            result = Net(p_net_id)
            nets[p_net_id] = result
        }
        return result
    }

    /**
     * Returns the net with the input name, or null, if the netlist does not contain a net with the input name.
     */
    fun get_net(p_net_id: Net.Id): Net? {
        return nets[p_net_id]
    }

    /**
     * Returns all nets in this net list containing the input pin.
     */
    fun get_nets(p_component_name: String, p_pin_name: String): Collection<Net> {
        val result = LinkedList<Net>()
        val search_pin = Net.Pin(p_component_name, p_pin_name)
        val net_list = nets.values
        for (curr_net in net_list) {
            val net_pins = curr_net.get_pins()
            if (net_pins != null && net_pins.contains(search_pin)) {
                result.add(curr_net)
            }
        }
        return result
    }
}
