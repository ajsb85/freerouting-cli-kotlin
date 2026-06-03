package app.freerouting.drc

import app.freerouting.board.Item

/**
 * Information about an unconnected NET (not individual items).
 *
 * IMPORTANT: Despite the name "unconnected_items", this class represents an
 * unconnected NET.
 * Each instance represents ONE net that has multiple disconnected groups of
 * items.
 * The items list contains ALL items from the net to show which components/pins
 * are affected.
 */
class UnconnectedItems {

    @JvmField val first_item: Item
    @JvmField val second_item: Item?
    @JvmField val all_items: List<Item>
    @JvmField val type: String

    /**
     * Creates a new instance of UnconnectedItems with two representative items
     */
    constructor(p_first_item: Item, p_second_item: Item?) : this(p_first_item, p_second_item, listOfNotNull(p_first_item, p_second_item), "unconnected_items")

    /**
     * Creates a new instance of UnconnectedItems with all items from the net
     */
    constructor(p_first_item: Item, p_second_item: Item?, p_all_items: List<Item>) : this(p_first_item, p_second_item, p_all_items, "unconnected_items")

    /**
     * Creates a new instance of UnconnectedItems with a specific type
     */
    constructor(p_first_item: Item, p_second_item: Item?, p_type: String) : this(p_first_item, p_second_item, listOfNotNull(p_first_item, p_second_item), p_type)

    /**
     * Creates a new instance of UnconnectedItems with all items and a specific type
     */
    constructor(p_first_item: Item, p_second_item: Item?, p_all_items: List<Item>?, p_type: String) {
        first_item = p_first_item
        second_item = p_second_item
        all_items = if (p_all_items != null) ArrayList(p_all_items) else listOfNotNull(p_first_item, p_second_item)
        type = p_type
    }
}
