package app.freerouting.geometry.planar

/**
 * A shape is defined as convex, if for each line segment with both endpoints contained in the shape the whole segment is contained completely in the shape.
 */
interface ConvexShape : Shape {

    /**
     * Calculates the offset shape by p_distance. If p_distance > 0, the shape will be enlarged, else the result shape will be smaller.
     */
    fun offset(p_distance: Double): ConvexShape

    /**
     * Shrinks the shape by p_offset. The result shape will not be empty.
     */
    fun shrink(p_offset: Double): ConvexShape

    /**
     * Returns the maximum diameter of the shape.
     */
    fun max_width(): Double

    /**
     * Returns the minimum diameter of the shape.
     */
    fun min_width(): Double
}
