package app.freerouting.rules

import app.freerouting.board.BasicBoard
import app.freerouting.logger.FRLogger
import app.freerouting.management.TextManager
import java.io.Serializable
import java.util.LinkedList
import java.util.Locale
import java.util.Vector

/**
 * Describes the electrical Nets on a board.
 */
class Nets : Serializable {

    /**
     * The list of electrical nets on the board
     */
    private val net_arr: Vector<Net> = Vector()
    private var board: BasicBoard? = null

    /**
     * Returns the biggest net number on the board.
     */
    fun max_net_no(): Int {
        return net_arr.size
    }

    /**
     * Returns the net with the input name and subnet_number , or null, if no such net exists.
     */
    fun get(p_name: String, p_subnet_number: Int): Net? {
        for (curr_net in net_arr) {
            if (curr_net != null && curr_net.name.equals(p_name, ignoreCase = true)) {
                if (curr_net.subnet_number == p_subnet_number) {
                    return curr_net
                }
            }
        }
        return null
    }

    /**
     * Returns all subnets with the input name.
     */
    fun get(p_name: String): Collection<Net> {
        val result: MutableCollection<Net> = LinkedList()
        for (curr_net in net_arr) {
            if (curr_net != null && curr_net.name.equals(p_name, ignoreCase = true)) {
                result.add(curr_net)
            }
        }
        return result
    }

    /**
     * Returns the net with the input net number or null, if no such net exists.
     */
    fun get(p_net_no: Int): Net? {
        if (p_net_no < 1 || p_net_no > net_arr.size) {
            return null
        }
        val result = net_arr[p_net_no - 1]
        if (result != null && result.net_number != p_net_no) {
            FRLogger.warn("Nets.get: inconsistent net_no")
        }
        return result
    }

    /**
     * Generates a new net number.
     */
    fun new_net(p_locale: Locale): Net {
        val tm = TextManager(NetClasses::class.java, p_locale)

        val net_name = tm.getText("net#") + (net_arr.size + 1)
        return add(net_name, 1, false)
    }

    /**
     * Adds a new net with default properties with the input name. p_subnet_number is used only if a net is divided internally because of fromto rules for example. For normal nets it is always 1.
     */
    fun add(p_name: String, p_subnet_number: Int, p_contains_plane: Boolean): Net {
        val new_net_no = net_arr.size + 1
        if (new_net_no >= max_legal_net_no) {
            FRLogger.warn("Nets.add_net: max_net_no out of range")
        }
        val new_net = Net(p_name, p_subnet_number, new_net_no, this, p_contains_plane)
        net_arr.add(new_net)
        return new_net
    }

    /**
     * Gets the Board of this net list. Used for example to get access to the Items of the net.
     */
    fun get_board(): BasicBoard {
        return this.board!!
    }

    /**
     * Sets the Board of this net list. Used for example to get access to the Items of the net.
     */
    fun set_board(p_board: BasicBoard?) {
        this.board = p_board
    }

    companion object {
        /**
         * The maximum legal net number for nets.
         */
        @JvmField
        val max_legal_net_no: Int = 9999999

        /**
         * auxiliary net number for internal use
         */
        @JvmField
        val hidden_net_no: Int = 10000001

        /**
         * Returns false, if p_net_no belongs to a net internally used for special purposes.
         */
        @JvmStatic
        fun is_normal_net_no(p_net_no: Int): Boolean {
            return p_net_no > 0 && p_net_no <= max_legal_net_no
        }
    }
}
