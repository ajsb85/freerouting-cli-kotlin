package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.util.LinkedList

/**
 * Abstract class with functions for shapes, whose borders consist of straight lines.
 */
abstract class PolylineShape : Shape, Serializable {

    /**
     * returns true, if the shape has no infinite part at this corner
     */
    abstract fun corner_is_bounded(p_no: Int): Boolean

    /**
     * Returns the number of borderlines of the shape
     */
    abstract fun border_line_count(): Int

    /**
     * Returns the p_no-th corner of this shape for p_no between 0 and border_line_count() - 1. The corners are sorted starting with the smallest y-coordinate in counterclock sense around the shape. If
     * there are several corners with the smallest y-coordinate, the corner with the smallest x-coordinate comes first. Consecutive corners may be equal.
     */
    abstract fun corner(p_no: Int): Point

    /**
     * Turns this shape by p_factor times 90 degree around p_pole.
     */
    abstract override fun turn_90_degree(p_factor: Int, p_pole: IntPoint): PolylineShape

    /**
     * Rotates this shape around p_pole by p_angle. The result may be not exact.
     */
    abstract override fun rotate_approx(p_angle: Double, p_pole: FloatPoint): PolylineShape

    /**
     * Mirrors this shape at the horizontal line through p_pole.
     */
    abstract override fun mirror_horizontal(p_pole: IntPoint): PolylineShape

    /**
     * Mirrors this shape at the vertical line through p_pole.
     */
    abstract override fun mirror_vertical(p_pole: IntPoint): PolylineShape

    /**
     * Returns the affine translation of the area by p_vector
     */
    abstract override fun translate_by(p_vector: Vector): PolylineShape

    /**
     * Return all bounded corners of this shape.
     */
    fun bounded_corners(): Array<Point> {
        val cornerCount = this.border_line_count()
        val resultList = LinkedList<Point>()
        for (i in 0 until cornerCount) {
            if (this.corner_is_bounded(i)) {
                resultList.add(this.corner(i))
            }
        }
        return resultList.toTypedArray()
    }

    /**
     * Returns an approximation of the p_no-th corner of this shape for p_no between 0 and border_line_count() - 1. If the shape is not bounded at this corner, the coordinates of the result will be set
     * to Integer.MAX_VALUE.
     */
    open fun corner_approx(p_no: Int): FloatPoint {
        return corner(p_no).to_float()
    }

    /**
     * Returns an approximation of all corners of this shape. If the shape is not bounded at a corner, the coordinates will be set to Integer.MAX_VALUE.
     */
    override fun corner_approx_arr(): Array<FloatPoint> {
        val cornerCount = this.border_line_count()
        val result = Array(cornerCount) { i -> this.corner_approx(i) }
        return result
    }

    /**
     * If p_point is equal to a corner of this shape, the number of that corner is returned; -1 otherwise.
     */
    fun equals_corner(p_point: Point): Int {
        val cornerCount = border_line_count()
        for (i in 0 until cornerCount) {
            if (p_point == corner(i)) {
                return i
            }
        }
        return -1
    }

    /**
     * Returns the cumulative border line length of the shape. If the shape is unbounded, Integer.MAX_VALUE is returned.
     */
    override fun circumference(): Double {
        if (!is_bounded()) {
            return Integer.MAX_VALUE.toDouble()
        }
        val cornerCount = border_line_count()
        if (cornerCount == 0) return 0.0
        var result = 0.0
        var prev_corner = corner_approx(cornerCount - 1)
        for (i in 0 until cornerCount) {
            val curr_corner = corner_approx(i)
            result += curr_corner.distance(prev_corner)
            prev_corner = curr_corner
        }
        return result
    }

    /**
     * Returns the arithmetic middle of the corners of this shape
     */
    override fun centre_of_gravity(): FloatPoint {
        val cornerCount = border_line_count()
        if (cornerCount == 0) return FloatPoint(0.0, 0.0)
        var x = 0.0
        var y = 0.0
        for (i in 0 until cornerCount) {
            val curr_point = corner_approx(i)
            x += curr_point.x
            y += curr_point.y
        }
        x /= cornerCount
        y /= cornerCount
        return FloatPoint(x, y)
    }

    /**
     * checks, if this shape is completely contained in p_box.
     */
    override fun is_contained_in(p_box: IntBox): Boolean {
        return p_box.contains(bounding_box())
    }

    /**
     * Returns the index of the corner of the shape, so that all other points of the shape are to the right of the line from p_from_point to this corner
     */
    fun index_of_left_most_corner(p_from_point: FloatPoint): Int {
        var left_most_corner = corner_approx(0)
        val cornerCount = border_line_count()
        var result = 0
        for (i in 1 until cornerCount) {
            val curr_corner = corner_approx(i)
            if (curr_corner.side_of(p_from_point, left_most_corner) == Side.ON_THE_LEFT) {
                left_most_corner = curr_corner
                result = i
            }
        }
        return result
    }

    /**
     * Returns the index of the corner of the shape, so that all other points of the shape are to the left of the line from p_from_point to this corner
     */
    fun index_of_right_most_corner(p_from_point: FloatPoint): Int {
        var right_most_corner = corner_approx(0)
        val cornerCount = border_line_count()
        var result = 0
        for (i in 1 until cornerCount) {
            val curr_corner = corner_approx(i)
            if (curr_corner.side_of(p_from_point, right_most_corner) == Side.ON_THE_RIGHT) {
                right_most_corner = curr_corner
                result = i
            }
        }
        return result
    }

    /**
     * Returns a FloatLine result, so that result.a is an approximation of the left most corner of this shape when viewed from p_from_point, and result.b is an approximation of the right most corner.
     */
    fun polar_line_segment(p_from_point: FloatPoint): FloatLine? {
        if (this.is_empty()) {
            FRLogger.warn("PolylineShape.polar_line_segment: shape is empty")
            return null
        }
        var left_most_corner = corner_approx(0)
        var right_most_corner = corner_approx(0)
        val cornerCount = border_line_count()
        for (i in 1 until cornerCount) {
            val curr_corner = corner_approx(i)
            if (curr_corner.side_of(p_from_point, right_most_corner) == Side.ON_THE_RIGHT) {
                right_most_corner = curr_corner
            }
            if (curr_corner.side_of(p_from_point, left_most_corner) == Side.ON_THE_LEFT) {
                left_most_corner = curr_corner
            }
        }
        return FloatLine(left_most_corner, right_most_corner)
    }

    /**
     * Returns the p_no-th border line of this shape.
     */
    abstract fun border_line(p_no: Int): Line

    /**
     * Returns the previous border line or corner number of this shape.
     */
    fun prev_no(p_no: Int): Int {
        val result: Int
        if (p_no == 0) {
            result = border_line_count() - 1
        } else {
            result = p_no - 1
        }
        return result
    }

    /**
     * Returns the next border line or corner number of this shape.
     */
    fun next_no(p_no: Int): Int {
        return (p_no + 1) % border_line_count()
    }

    override fun get_border(): PolylineShape {
        return this
    }

    override fun get_holes(): Array<Shape> {
        return emptyArray()
    }

    /**
     * Checks, if this shape and p_line have a common point.
     */
    fun intersects(p_line: Line): Boolean {
        val side_of_first_corner = p_line.side_of(corner(0))
        if (side_of_first_corner == Side.COLLINEAR) {
            return true
        }
        val cornerCount = this.border_line_count()
        for (i in 1 until cornerCount) {
            if (p_line.side_of(corner(i)) != side_of_first_corner) {
                return true
            }
        }
        return false
    }

    /**
     * Calculates the left most corner of this shape, when looked at from p_from_point.
     */
    fun left_most_corner(p_from_point: Point): Point {
        if (this.is_empty()) {
            return p_from_point
        }
        var result = this.corner(0)
        val cornerCount = this.border_line_count()
        for (i in 1 until cornerCount) {
            val curr_corner = this.corner(i)
            if (curr_corner.side_of(p_from_point, result) == Side.ON_THE_LEFT) {
                result = curr_corner
            }
        }
        return result
    }

    /**
     * Calculates the right most corner of this shape, when looked at from p_from_point.
     */
    fun right_most_corner(p_from_point: Point): Point {
        if (this.is_empty()) {
            return p_from_point
        }
        var result = this.corner(0)
        val cornerCount = this.border_line_count()
        for (i in 1 until cornerCount) {
            val curr_corner = this.corner(i)
            if (curr_corner.side_of(p_from_point, result) == Side.ON_THE_RIGHT) {
                result = curr_corner
            }
        }
        return result
    }
}
