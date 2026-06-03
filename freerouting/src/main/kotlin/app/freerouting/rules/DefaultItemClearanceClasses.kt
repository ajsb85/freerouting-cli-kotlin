package app.freerouting.rules

import java.io.Serializable

class DefaultItemClearanceClasses : Serializable {

    private val arr: IntArray

    /**
     * Creates a new instance of DefaultItemClearancesClasses
     */
    constructor() {
        this.arr = IntArray(ItemClass.entries.size)
        this.set_all(1)
    }

    constructor(p_classes: DefaultItemClearanceClasses) {
        this.arr = p_classes.arr.clone()
    }

    /**
     * Returns the number of the default clearance class for the input item class.
     */
    fun get(p_item_class: ItemClass): Int {
        return this.arr[p_item_class.ordinal]
    }

    /**
     * Sets the index of the default clearance class of the input item class in the clearance matrix to p_index.
     */
    fun set(p_item_class: ItemClass, p_index: Int) {
        this.arr[p_item_class.ordinal] = p_index
    }

    /**
     * Sets the indices of all default item clearance classes to p_index.
     */
    fun set_all(p_index: Int) {
        for (i in 1 until this.arr.size) {
            arr[i] = p_index
        }
    }

    /**
     * Used in the function get_default_clearance_class to get the default clearance classes for item classes.
     */
    enum class ItemClass {
        NONE, TRACE, VIA, PIN, SMD, AREA
    }
}
