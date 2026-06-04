package app.freerouting.core

import app.freerouting.board.BasicBoard
import app.freerouting.board.DrillItem
import app.freerouting.datastructures.UndoableObjects
import java.io.Serializable
import java.util.Arrays
import java.util.Vector

/**
 * Describes a board library of packages and padstacks.
 */
class BoardLibrary : Serializable {

    @JvmField
    var padstacks: Padstacks? = null

    @JvmField
    var packages: Packages? = null

    /**
     * Contains information for gate swap and pin swap in the Specctra-dsn format.
     */
    @JvmField
    var logical_parts: LogicalParts = LogicalParts()

    /**
     * The subset of padstacks in the board library, which can be used in routing for inserting vias.
     */
    private var via_padstacks: MutableList<Padstack>? = null

    /**
     * Creates a new instance of BoardLibrary
     */
    constructor(p_padstacks: Padstacks, p_packages: Packages) {
        padstacks = p_padstacks
        packages = p_packages
        logical_parts = LogicalParts()
    }

    /**
     * Creates a new instance of BoardLibrary
     */
    constructor()

    /**
     * The count of padstacks from this.padstacks, which can be used in routing
     */
    fun via_padstack_count(): Int {
        val list = this.via_padstacks
        return list?.size ?: 0
    }

    /**
     * Gets the via padstack for routing with index p_no
     */
    fun get_via_padstack(p_no: Int): Padstack? {
        val list = this.via_padstacks
        if (list == null || p_no < 0 || p_no >= list.size) {
            return null
        }
        return list[p_no]
    }

    /**
     * Gets the via padstack with name p_name, or null, if no such padstack exists.
     */
    fun get_via_padstack(p_name: String): Padstack? {
        val list = this.via_padstacks ?: return null
        for (curr_padstack in list) {
            if (curr_padstack.name == p_name) {
                return curr_padstack
            }
        }
        return null
    }

    /**
     * Returns the via padstacks, which can be used for routing.
     */
    fun get_via_padstacks(): Array<Padstack> {
        val list = this.via_padstacks ?: return emptyArray()
        return list.toTypedArray()
    }

    /**
     * Sets the subset of padstacks from this.padstacks, which can be used in routing for inserting vias.
     */
    fun set_via_padstacks(p_padstacks: Array<Padstack>) {
        this.via_padstacks = Vector(Arrays.asList(*p_padstacks))
    }

    /**
     * Appends p_padstack to the list of via padstacks. Returns false, if the list contains already a padstack with p_padstack.name.
     */
    fun add_via_padstack(p_padstack: Padstack): Boolean {
        if (get_via_padstack(p_padstack.name) != null) {
            return false
        }

        var list = this.via_padstacks
        if (list == null) {
            list = Vector()
            this.via_padstacks = list
        }

        list.add(p_padstack)
        return true
    }

    /**
     * Removes p_padstack from the via padstack list. Returns false, if p_padstack was not found in the list. If the padstack is no more used on the board, it will also be removed from the board
     * padstacks.
     */
    fun remove_via_padstack(p_padstack: Padstack, p_board: BasicBoard?): Boolean {
        val list = this.via_padstacks ?: return false
        return list.remove(p_padstack)
    }

    /**
     * Gets the via padstack mirrored to the back side of the board. Returns null, if no such via padstack exists.
     */
    fun get_mirrored_via_padstack(p_via_padstack: Padstack): Padstack? {
        val padstacksRef = this.padstacks ?: return null
        val layer_count = padstacksRef.board_layer_structure.arr.size
        if (p_via_padstack.from_layer() == 0 && p_via_padstack.to_layer() == layer_count - 1) {
            return p_via_padstack
        }
        val new_from_layer = layer_count - p_via_padstack.to_layer() - 1
        val new_to_layer = layer_count - p_via_padstack.from_layer() - 1
        val list = via_padstacks ?: return null
        for (curr_via_padstack in list) {
            if (curr_via_padstack.from_layer() == new_from_layer && curr_via_padstack.to_layer() == new_to_layer) {
                return curr_via_padstack
            }
        }
        return null
    }

    /**
     * Looks, if the input padstack is used on p_board in a Package or in drill.
     */
    fun is_used(p_padstack: Padstack, p_board: BasicBoard): Boolean {
        val it = p_board.item_list.start_read_object()
        while (true) {
            val curr_item = p_board.item_list.read_object(it) ?: break
            if (curr_item is DrillItem) {
                if (curr_item.get_padstack() === p_padstack) {
                    return true
                }
            }
        }
        val pkgs = this.packages ?: return false
        for (i in 1..pkgs.count()) {
            val curr_package = pkgs.get(i) ?: continue
            for (j in 0 until curr_package.pin_count()) {
                val pin = curr_package.get_pin(j)
                if (pin != null && pin.padstack_no == p_padstack.no) {
                    return true
                }
            }
        }
        return false
    }
}
