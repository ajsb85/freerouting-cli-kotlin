package app.freerouting.geometry.planar

/**
 * Interface describing functionality for connected 2-dimensional shapes in the plane. A Shape object is expected to be simply connected, that means, it may not contain holes.
 */
interface Shape : Area {

    /**
     * Returns the length of the border of this shape. If the shape is unbounded, Integer.MAX_VALUE is returned.
     */
    fun circumference(): Double

    /**
     * Returns the content of the area of the shape. If the shape is unbounded, Double.MAX_VALUE is returned.
     */
    fun area(): Double

    /**
     * Returns the gravity point of this shape
     */
    fun centre_of_gravity(): FloatPoint

    /**
     * Returns true, if p_point is not contained in the inside or the boundary of the shape
     */
    fun is_outside(p_point: Point): Boolean

    /**
     * Returns true, if p_point is contained in this shape, but not on the border.
     */
    fun contains_inside(p_point: Point): Boolean

    /**
     * Returns true, if p_point lies exact on the boundary of the shape
     */
    fun contains_on_border(p_point: Point): Boolean

    /**
     * Returns the distance between p_point and its nearest point on the shape. 0, if p_point is contained in this shape
     */
    fun distance(p_point: FloatPoint): Double

    /**
     * Return a bounding TileShape of this shape.
     */
    fun bounding_tile(): TileShape

    /**
     * Returns the bounding RegularTileShape with the fixed directions p_dirs
     */
    fun bounding_shape(p_dirs: ShapeBoundingDirections): RegularTileShape

    /**
     * Returns the distance between p_point and its nearest point on the border of the shape.
     */
    fun border_distance(p_point: FloatPoint): Double

    /**
     * Returns the smallest distance from the centre of gravity to the border of the shape.
     */
    fun smallest_radius(): Double

    /**
     * Returns the offset shape of this shape by offsetting the boundary by p_distance to the outside. The result instance may be of a different class than this instance. (For example an enlarged IntBox
     * is an IntOctagon).
     */
    fun enlarge(p_offset: Double): Shape

    /**
     * Checks, if  this shape and p_other have a nonempty intersection.
     */
    fun intersects(p_other: Shape): Boolean

    /**
     * Cuts out the parts of p_polyline in the interior of this shape and returns a list of the remaining pieces of p_polyline. Pieces completely contained in the border of this shape are not returned.
     */
    fun cutout(p_polyline: Polyline): Array<Polyline>

    /**
     * Auxiliary function to implement the same function with parameter type Shape.
     */
    fun intersects(p_other: IntBox): Boolean

    /**
     * Auxiliary function to implement the same function with parameter type Shape.
     */
    fun intersects(p_other: IntOctagon): Boolean

    /**
     * Auxiliary function to implement the same function with parameter type Shape.
     */
    fun intersects(p_other: Simplex): Boolean

    /**
     * Auxiliary function to implement the same function with parameter type Shape.
     */
    fun intersects(p_other: Circle): Boolean
}
