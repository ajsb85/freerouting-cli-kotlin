package app.freerouting.io.specctra.parser

import app.freerouting.board.Component
import app.freerouting.core.`Package`
import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.logger.FRLogger
import java.io.IOException
import java.util.TreeSet

/**
 * Class for reading and writing net scopes from dsn-files.
 */
class Net(
    @JvmField val id: Id
) {
    private var pin_list: Set<Pin>? = null

    fun get_pins(): Set<Pin>? {
        return pin_list
    }

    fun set_pins(p_pin_list: Collection<Pin>) {
        pin_list = TreeSet(p_pin_list)
    }

    class Id(
        @JvmField val name: String,
        @JvmField val subnet_number: Int
    ) : Comparable<Id> {

        override fun compareTo(other: Id): Int {
            var result = name.compareTo(other.name)
            if (result == 0) {
                result = subnet_number - other.subnet_number
            }
            return result
        }
    }

    /**
     * Sorted tuple of component name and pin name.
     */
    class Pin(
        @JvmField val component_name: String,
        @JvmField val pin_name: String
    ) : Comparable<Pin> {

        override fun compareTo(other: Pin): Int {
            var result = component_name.compareTo(other.component_name)
            if (result == 0) {
                result = pin_name.compareTo(other.pin_name)
            }
            return result
        }

        override fun toString(): String {
            return "Pin{" + component_name + '-' + pin_name + '}'
        }
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(
            p_par: WriteScopeParameter,
            p_net: app.freerouting.rules.Net,
            p_pin_list: Collection<app.freerouting.board.Pin>
        ) {
            p_par.file.start_scope()
            write_net_id(p_net, p_par.file, p_par.identifier_type)
            // write the pins scope
            p_par.file.start_scope()
            p_par.file.write("pins")
            for (curr_pin in p_pin_list) {
                if (curr_pin.contains_net(p_net.net_number)) {
                    write_pin(p_par, curr_pin)
                }
            }
            p_par.file.end_scope()
            p_par.file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_net_id(
            p_net: app.freerouting.rules.Net,
            p_file: IndentFileWriter,
            p_identifier_type: IdentifierType
        ) {
            p_file.write("net ")
            p_identifier_type.write(p_net.name, p_file)
            p_file.write(" ")
            val subnet_number = p_net.subnet_number
            p_file.write(subnet_number.toString())
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_pin(p_par: WriteScopeParameter, p_pin: app.freerouting.board.Pin) {
            val curr_component = p_par.board.components.get(p_pin.get_component_no())
            if (curr_component == null) {
                FRLogger.warn("Net.write_scope: component not found")
                return
            }
            val lib_pin = curr_component.get_package().get_pin(p_pin.get_index_in_package())
            if (lib_pin == null) {
                FRLogger.warn("Net.write_scope:  pin number out of range at '" + curr_component.name + "'")
                return
            }
            p_par.file.new_line()
            p_par.identifier_type.write(curr_component.name, p_par.file)
            p_par.file.write("-")
            p_par.identifier_type.write(lib_pin.name, p_par.file)
        }
    }
}
