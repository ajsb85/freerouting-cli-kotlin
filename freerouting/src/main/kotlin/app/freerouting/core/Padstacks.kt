package app.freerouting.core

import app.freerouting.board.LayerStructure
import app.freerouting.geometry.planar.ConvexShape
import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.util.Vector

/**
 * Describes a library of padstacks for pins or vias.
 */
class Padstacks(
    @JvmField val board_layer_structure: LayerStructure
) : Serializable {

    /**
     * The array of Padstacks in this object
     */
    private val padstack_arr = Vector<Padstack>()

    /**
     * Returns the padstack with the input name or null, if no such padstack exists.
     */
    fun get(p_name: String): Padstack? {
        for (curr_padstack in padstack_arr) {
            if (curr_padstack != null && curr_padstack.name.equals(p_name, ignoreCase = true)) {
                return curr_padstack
            }
        }
        return null
    }

    /**
     * Returns the count of Padstacks in this object.
     */
    fun count(): Int {
        return padstack_arr.size
    }

    /**
     * Returns the padstack with index p_padstack_no for 1 {@literal <}= p_padstack_no {@literal <}= padstack_count
     */
    fun get(p_padstack_no: Int): Padstack? {
        if (p_padstack_no <= 0 || p_padstack_no > padstack_arr.size) {
            val padstack_count = padstack_arr.size
            FRLogger.warn("Padstacks.get: 1 <= p_padstack_no <= $padstack_count expected")
            return null
        }
        val result = padstack_arr.elementAt(p_padstack_no - 1)
        if (result != null && result.no != p_padstack_no) {
            FRLogger.warn("Padstacks.get: inconsistent padstack number")
        }
        return result
    }

    /**
     * Appends a new padstack with the input shapes to this padstacks. p_shapes is an array of dimension board layer_count. p_drill_allowed indicates, if vias of the own net are allowed to overlap with
     * this padstack If p_placed_absolute is false, the layers of the padstack are mirrored, if it is placed on the back side.
     */
    fun add(p_name: String, p_shapes: Array<ConvexShape?>, p_drill_allowed: Boolean, p_placed_absolute: Boolean): Padstack {
        val new_padstack = Padstack(p_name, padstack_arr.size + 1, p_shapes, p_drill_allowed, p_placed_absolute, this)
        padstack_arr.add(new_padstack)
        return new_padstack
    }

    /**
     * Appends a new padstack with the input shapes to this padstacks. p_shapes is an array of dimension board layer_count. The padstack name is generated internally.
     */
    fun add(p_shapes: Array<ConvexShape?>): Padstack {
        val new_name = "padstack#" + (padstack_arr.size + 1)
        return add(new_name, p_shapes, p_drill_allowed = false, p_placed_absolute = false)
    }

    /**
     * Appends a new padstack with the input shape from p_from_layer to p_to_layer and null on the other layers. The padstack name is generated internally.
     */
    fun add(p_shape: ConvexShape, p_from_layer: Int, p_to_layer: Int): Padstack {
        val shape_arr = arrayOfNulls<ConvexShape>(board_layer_structure.arr.size)
        val from_layer = Math.max(p_from_layer, 0)
        val to_layer = Math.min(p_to_layer, board_layer_structure.arr.size - 1)
        for (i in from_layer..to_layer) {
            shape_arr[i] = p_shape
        }
        return add(shape_arr)
    }
}
