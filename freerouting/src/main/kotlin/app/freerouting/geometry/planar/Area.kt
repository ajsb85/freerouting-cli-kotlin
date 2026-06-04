package app.freerouting.geometry.planar

/**
 * An Area is a not necessarily simply connected Shape, which means, that it may contain holes. The border and the holes of an Area are of class Shape.
 */
interface Area {

    /**
     * returns true, if the area is empty
     */
    fun is_empty(): Boolean

    /**
     * returns true, if the area is contained in a sufficiently large box
     */
    fun is_bounded(): Boolean

    /**
     * returns 2, if the area contains 2 dimensional shapes , 1, if it contains curves, 0, if it is reduced to a points and -1, if it is empty.
     */
    fun dimension(): Int

    /**
     * Checks, if this area is completely contained in p_box.
     */
    fun is_contained_in(p_box: IntBox): Boolean

    /**
     * returns the border shape of this area
     */
    fun get_border(): Shape

    /**
     * Returns the array of holes, of this area.
     */
    fun get_holes(): Array<Shape>

    /**
     * Returns the smallest surrounding box of the area. If the area is not bounded, some coordinates of the resulting box may be equal Integer.MAX_VALUE
     */
    fun bounding_box(): IntBox

    /**
     * Returns the smallest surrounding octagon of the area. If the area is not bounded, some coordinates of the resulting octagon may be equal Integer.MAX_VALUE
     */
    fun bounding_octagon(): IntOctagon

    /**
     * Returns true, if p_point is contained in this area, but not inside a hole. Being on the border is not defined for FloatPoints because of numerical inaccuracy.
     */
    fun contains(p_point: FloatPoint): Boolean

    /**
     * Returns true, if p_point is inside or on the border of this area, but not inside a hole.
     */
    fun contains(p_point: Point): Boolean

    /**
     * Calculates an approximation of the nearest point of the shape to p_from_point
     */
    fun nearest_point_approx(p_from_point: FloatPoint): FloatPoint

    /**
     * Turns this area by p_factor times 90 degree around p_pole.
     */
    fun turn_90_degree(p_factor: Int, p_pole: IntPoint): Area

    /**
     * Rotates the area around p_pole by p_angle. The result may be not exact.
     */
    fun rotate_approx(p_angle: Double, p_pole: FloatPoint): Area

    /**
     * Returns the affine translation of the area by p_vector
     */
    fun translate_by(p_vector: Vector): Area

    /**
     * Mirrors this area at the horizontal line through p_pole.
     */
    fun mirror_horizontal(p_pole: IntPoint): Area

    /**
     * Mirrors this area at the vertical line through p_pole.
     */
    fun mirror_vertical(p_pole: IntPoint): Area

    /**
     * Returns an approximation of the corners of this area.
     */
    fun corner_approx_arr(): Array<FloatPoint>

    /**
     * Returns a division of this area into convex pieces.
     */
    fun split_to_convex(): Array<TileShape>
}
