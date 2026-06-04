package app.freerouting.io.specctra.parser


/**
 * Describes a layer structure read from a dsn file.
 */
class LayerStructure {
    @JvmField
    val arr: Array<Layer>

    /**
     * Creates a new instance of LayerStructure from a list of layers
     */
    constructor(p_layer_list: Collection<Layer>) {
        arr = p_layer_list.toTypedArray()
    }

    /**
     * Creates a dsn-LayerStructure from a board LayerStructure.
     */
    constructor(p_board_layer_structure: app.freerouting.board.LayerStructure) {
        arr = Array(p_board_layer_structure.arr.size) { i ->
            val boardLayer = p_board_layer_structure.arr[i]
            Layer(boardLayer.name, i, boardLayer.is_signal)
        }
    }

    /**
     * returns the number of the layer with the name p_name, -1, if no layer with name p_name exists.
     */
    fun get_no(p_name: String): Int {
        for (i in arr.indices) {
            if (p_name == arr[i].name) {
                return i
            }
        }
        // check for special layers of the Electra autorouter used for the outline
        if (p_name.contains("Top")) {
            return 0
        }
        if (p_name.contains("Bottom")) {
            return arr.size - 1
        }
        return -1
    }

    fun signal_layer_count(): Int {
        var result = 0
        for (currLayer in arr) {
            if (currLayer.is_signal) {
                ++result
            }
        }
        return result
    }

    /**
     * Returns, if the net with name p_net_name contains a power plane.
     */
    fun contains_plane(p_net_name: String): Boolean {
        for (currLayer in arr) {
            if (!currLayer.is_signal) {
                if (currLayer.net_names.contains(p_net_name)) {
                    return true
                }
            }
        }
        return false
    }
}
