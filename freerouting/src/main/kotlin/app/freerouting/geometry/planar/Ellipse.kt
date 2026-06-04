package app.freerouting.geometry.planar

import java.io.Serializable

/**
 * Describes functionality of an ellipse in the plane. Does not implement the ConvexShape interface, because coordinates are float.
 */
class Ellipse(
    p_center: FloatPoint,
    p_rotation: Double,
    p_radius_1: Double,
    p_radius_2: Double
) : Serializable {

    @JvmField
    val center: FloatPoint = p_center

    @JvmField
    val rotation: Double

    @JvmField
    val bigger_radius: Double

    @JvmField
    val smaller_radius: Double

    init {
        var curr_rotation: Double
        if (p_radius_1 >= p_radius_2) {
            this.bigger_radius = p_radius_1
            this.smaller_radius = p_radius_2
            curr_rotation = p_rotation
        } else {
            this.bigger_radius = p_radius_2
            this.smaller_radius = p_radius_1
            curr_rotation = p_rotation + 0.5 * Math.PI
        }
        while (curr_rotation >= Math.PI) {
            curr_rotation -= Math.PI
        }
        while (curr_rotation < 0.0) {
            curr_rotation += Math.PI
        }
        this.rotation = curr_rotation
    }
}
