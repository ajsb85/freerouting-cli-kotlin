package app.freerouting.io.specctra.parser

import java.util.LinkedList

/**
 * Describes the placement data of a library component
 */
class ComponentPlacement(
    @JvmField val lib_name: String
) {
    /**
     * The list of ComponentLocations of the library component on the board.
     */
    @JvmField
    val locations: MutableCollection<ComponentLocation> = LinkedList<ComponentLocation>()

    /**
     * The structure of an entry in the list locations.
     */
    class ComponentLocation(
        @JvmField val name: String,
        /**
         * the x- and the y-coordinate of the location.
         */
        @JvmField val coor: DoubleArray,
        /**
         * True, if the component is placed at the component side. Else the component is placed at the solder side.
         */
        @JvmField val is_front: Boolean,
        /**
         * The rotation of the component in degree.
         */
        @JvmField val rotation: Double,
        /**
         * If true, the component cannot be moved.
         */
        @JvmField val position_fixed: Boolean,
        /**
         * The entries of this map are of type ItemClearanceInfo, the keys are the pin names.
         */
        @JvmField val pin_infos: Map<String, ItemClearanceInfo>,
        @JvmField val keepout_infos: Map<String, ItemClearanceInfo>,
        @JvmField val via_keepout_infos: Map<String, ItemClearanceInfo>,
        @JvmField val place_keepout_infos: Map<String, ItemClearanceInfo>
    )

    class ItemClearanceInfo(
        @JvmField val name: String,
        @JvmField val clearance_class: String
    )
}
