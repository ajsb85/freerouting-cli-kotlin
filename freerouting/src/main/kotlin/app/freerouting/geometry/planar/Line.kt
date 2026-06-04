package app.freerouting.geometry.planar

import app.freerouting.datastructures.Signum
import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.math.BigInteger

/**
 * Implements functionality for lines in the plane.
 */
class Line : Comparable<Line>, Serializable {

    @JvmField
    val a: Point
    @JvmField
    val b: Point

    @Transient
    private var dir: Direction? = null // should only be accessed from direction()

    /**
     * creates a directed Line from two Points
     */
    constructor(p_a: Point, p_b: Point) {
        a = p_a
        b = p_b
        dir = null
        if (!(a is IntPoint && b is IntPoint)) {
            FRLogger.warn("Line(p_a, p_b) only implemented for IntPoints till now")
        }
    }

    /**
     * creates a directed Line from four integer Coordinates
     */
    constructor(p_a_x: Int, p_a_y: Int, p_b_x: Int, p_b_y: Int) {
        a = IntPoint(p_a_x, p_a_y)
        b = IntPoint(p_b_x, p_b_y)
        dir = null
    }

    /**
     * creates a directed Line from a Point and a Direction
     */
    constructor(p_a: Point, p_dir: Direction) {
        a = p_a
        b = p_a.translate_by(p_dir.get_vector())
        dir = p_dir
        if (!(a is IntPoint && b is IntPoint)) {
            FRLogger.warn("Line(p_a, p_dir) only implemented for IntPoints till now")
        }
    }

    /**
     * returns true, if this and p_ob define the same line
     */
    fun get_id_no(): Int {
        return 31 * a.get_id_no() + b.get_id_no()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (other !is Line) {
            return false
        }
        if (side_of(other.a) != Side.COLLINEAR) {
            return false
        }
        return direction().equals(other.direction())
    }

    override fun hashCode(): Int {
        return get_id_no()
    }

    /**
     * Returns true, if this and p_other define the same line. Is designed for good performance, but works only for lines consisting of IntPoints.
     */
    fun fast_equals(p_other: Line): Boolean {
        val this_a = a as IntPoint
        val this_b = b as IntPoint
        val other_a = p_other.a as IntPoint
        val dx1 = other_a.x.toDouble() - this_a.x
        val dy1 = other_a.y.toDouble() - this_a.y
        val dx2 = this_b.x.toDouble() - this_a.x
        val dy2 = this_b.y.toDouble() - this_a.y
        val det = dx1 * dy2 - dx2 * dy1
        if (det != 0.0) {
            return false
        }
        return direction().equals(p_other.direction())
    }

    /**
     * get the direction of this directed line
     */
    fun direction(): Direction {
        var result = dir
        if (result == null) {
            val d = b.difference_by(a)
            result = Direction.get_instance(d)
            dir = result
        }
        return result
    }

    /**
     * The function returns Side.ON_THE_LEFT, if this Line is on the left of p_point, Side.ON_THE_RIGHT, if this Line is on the right of p_point and Side.COLLINEAR, if this Line contains p_point.
     */
    fun side_of(p_point: Point): Side {
        val result = p_point.side_of(this)
        return result.negate()
    }

    /**
     * Returns Side.COLLINEAR, if p_point is on the line with tolerance p_tolerance. Otherwise, Side.ON_THE_LEFT, if this line is on the left of p_point, or Side.ON_THE_RIGHT, if this line is on the
     * right of p_point,
     */
    fun side_of(p_point: FloatPoint, p_tolerance: Double): Side {
        // only implemented for IntPoint lines for performance reasons
        val this_a = a as IntPoint
        val this_b = b as IntPoint
        val det = (this_b.y - this_a.y).toDouble() * (p_point.x - this_a.x) - (this_b.x - this_a.x).toDouble() * (p_point.y - this_a.y)
        val result: Side
        if (det - p_tolerance > 0) {
            result = Side.ON_THE_LEFT
        } else if (det + p_tolerance < 0) {
            result = Side.ON_THE_RIGHT
        } else {
            result = Side.COLLINEAR
        }

        return result
    }

    /**
     * returns Side.ON_THE_LEFT, if this line is on the left of p_point, Side.ON_THE_RIGHT, if this line is on the right of p_point, Side.COLLINEAR otherwise.
     */
    fun side_of(p_point: FloatPoint): Side {
        return side_of(p_point, 0.0)
    }

    /**
     * Returns Side.ON_THE_LEFT, if this line is on the left of the intersection of p_1 and p_2, Side.ON_THE_RIGHT, if this line is on the right of the intersection, and Side.COLLINEAR, if all 3 lines
     * intersect in exactly 1 point.
     */
    fun side_of_intersection(p_1: Line, p_2: Line): Side {
        val intersection_approx = p_1.intersection_approx(p_2)
        var result = this.side_of(intersection_approx, 1.0)
        if (result == Side.COLLINEAR) {
            // Previous calculation was with FloatPoints and a tolerance
            // for performance reasons. Make an exact check for
            // collinearity now with class Point instead of FloatPoint.
            val intersection = p_1.intersection(p_2)
            result = this.side_of(intersection)
        }
        return result
    }

    /**
     * Looks, if all interior points of p_tile are on the right side of this line.
     */
    fun is_on_the_left(p_tile: TileShape): Boolean {
        for (i in 0 until p_tile.border_line_count()) {
            if (this.side_of(p_tile.corner(i)) == Side.ON_THE_RIGHT) {
                return false
            }
        }
        return true
    }

    /**
     * Looks, if all interior points of p_tile are on the left side of this line.
     */
    fun is_on_the_right(p_tile: TileShape): Boolean {
        for (i in 0 until p_tile.border_line_count()) {
            if (this.side_of(p_tile.corner(i)) == Side.ON_THE_LEFT) {
                return false
            }
        }
        return true
    }

    /**
     * Returns the signed distance of this line from p_point. The result will be positive, if the line is on the left of p_point, else negative.
     */
    fun signed_distance(p_point: FloatPoint): Double {
        // only implemented for IntPoint lines for performance reasons
        val this_a = a as IntPoint
        val this_b = b as IntPoint
        val dx = (this_b.x - this_a.x).toDouble()
        val dy = (this_b.y - this_a.y).toDouble()
        val det = dy * (p_point.x - this_a.x) - dx * (p_point.y - this_a.y)
        // area of the parallelogramm spanned by the 3 points
        val length = Math.sqrt(dx * dx + dy * dy)
        return det / length
    }

    /**
     * returns true, if the 2 lines define the same set of points, but may have opposite directions
     */
    fun overlaps(p_other: Line): Boolean {
        return side_of(p_other.a) == Side.COLLINEAR && side_of(p_other.b) == Side.COLLINEAR
    }

    /**
     * Returns the line defining the same set of points, but with opposite direction
     */
    fun opposite(): Line {
        return Line(b, a)
    }

    /**
     * Returns the intersection point of the 2 lines. If the lines are parallel result.is_infinite() will be true.
     */
    fun intersection(p_other: Line): Point {
        // this function is at the moment only implemented for lines
        // consisting of IntPoints.
        // The general implementation is still missing.
        val delta_1 = b.difference_by(a) as IntVector
        val delta_2 = p_other.b.difference_by(p_other.a) as IntVector
        // Separate handling for orthogonal and 45 degree lines for better performance
        if (delta_1.x == 0) // this line is vertical
        {
            if (delta_2.y == 0) // other line is horizontal
            {
                return IntPoint((this.a as IntPoint).x, (p_other.a as IntPoint).y)
            }
            if (delta_2.x == delta_2.y) // other line is right diagonal
            {
                val this_x = (this.a as IntPoint).x
                val other_a = p_other.a as IntPoint
                return IntPoint(this_x, other_a.y + this_x - other_a.x)
            }
            if (delta_2.x == -delta_2.y) // other line is left diagonal
            {
                val this_x = (this.a as IntPoint).x
                val other_a = p_other.a as IntPoint
                return IntPoint(this_x, other_a.y + other_a.x - this_x)
            }
        } else if (delta_1.y == 0) // this line is horizontal
        {
            if (delta_2.x == 0) // other line is vertical
            {
                return IntPoint((p_other.a as IntPoint).x, (this.a as IntPoint).y)
            }
            if (delta_2.x == delta_2.y) // other line is right diagonal
            {
                val this_y = (this.a as IntPoint).y
                val other_a = p_other.a as IntPoint
                return IntPoint(other_a.x + this_y - other_a.y, this_y)
            }
            if (delta_2.x == -delta_2.y) // other line is left diagonal
            {
                val this_y = (this.a as IntPoint).y
                val other_a = p_other.a as IntPoint
                return IntPoint(other_a.x + other_a.y - this_y, this_y)
            }
        } else if (delta_1.x == delta_1.y) // this line is right diagonal
        {
            if (delta_2.x == 0) // other line is vertical
            {
                val other_x = (p_other.a as IntPoint).x
                val this_a = this.a as IntPoint
                return IntPoint(other_x, this_a.y + other_x - this_a.x)
            }
            if (delta_2.y == 0) // other line is horizontal
            {
                val other_y = (p_other.a as IntPoint).y
                val this_a = this.a as IntPoint
                return IntPoint(this_a.x + other_y - this_a.y, other_y)
            }
        } else if (delta_1.x == -delta_1.y) // this line is left diagonal
        {
            if (delta_2.x == 0) // other line is vertical
            {
                val other_x = (p_other.a as IntPoint).x
                val this_a = this.a as IntPoint
                return IntPoint(other_x, this_a.y + this_a.x - other_x)
            }
            if (delta_2.y == 0) // other line is horizontal
            {
                val other_y = (p_other.a as IntPoint).y
                val this_a = this.a as IntPoint
                return IntPoint(this_a.x + this_a.y - other_y, other_y)
            }
        }

        val det_1 = BigInteger.valueOf((a as IntPoint).determinant(b as IntPoint).toLong())
        val det_2 = BigInteger.valueOf((p_other.a as IntPoint).determinant(p_other.b as IntPoint).toLong())
        var det = BigInteger.valueOf(delta_2.determinant(delta_1).toLong())
        var is_x = det_1.multiply(BigInteger.valueOf(delta_2.x.toLong())).subtract(det_2.multiply(BigInteger.valueOf(delta_1.x.toLong())))
        var is_y = det_1.multiply(BigInteger.valueOf(delta_2.y.toLong())).subtract(det_2.multiply(BigInteger.valueOf(delta_1.y.toLong())))
        val signum = det.signum()
        if (signum != 0) {
            if (signum < 0) {
                det = det.negate()
                is_x = is_x.negate()
                is_y = is_y.negate()
            }
            if (is_x.mod(det).signum() == 0 && is_y.mod(det).signum() == 0) {
                is_x = is_x.divide(det)
                is_y = is_y.divide(det)
                if (Math.abs(is_x.toDouble()) <= Limits.CRIT_INT && Math.abs(is_y.toDouble()) <= Limits.CRIT_INT) {
                    return IntPoint(is_x.toInt(), is_y.toInt())
                }
                det = BigInteger.ONE
            }
        }
        return RationalPoint(is_x, is_y, det)
    }

    /**
     * Returns an approximation of the intersection of the 2 lines by a FloatPoint. If the lines are parallel the result coordinates will be Integer.MAX_VALUE. Useful in situations where performance is
     * more important than accuracy.
     */
    fun intersection_approx(p_other: Line): FloatPoint {
        // this function is at the moment only implemented for lines
        // consisting of IntPoints.
        // The general implementation is still missing.
        val this_a = a as IntPoint
        val this_b = b as IntPoint
        val other_a = p_other.a as IntPoint
        val other_b = p_other.b as IntPoint
        val d1x = (this_b.x - this_a.x).toDouble()
        val d1y = (this_b.y - this_a.y).toDouble()
        val d2x = (other_b.x - other_a.x).toDouble()
        val d2y = (other_b.y - other_a.y).toDouble()
        val det_1 = this_a.x.toDouble() * this_b.y - this_a.y.toDouble() * this_b.x
        val det_2 = other_a.x.toDouble() * other_b.y - other_a.y.toDouble() * other_b.x
        val det = d2x * d1y - d2y * d1x
        val is_x: Double
        val is_y: Double
        if (det == 0.0) {
            is_x = Integer.MAX_VALUE.toDouble()
            is_y = Integer.MAX_VALUE.toDouble()
        } else {
            is_x = (d2x * det_1 - d1x * det_2) / det
            is_y = (d2y * det_1 - d1y * det_2) / det
        }
        return FloatPoint(is_x, is_y)
    }

    /**
     * returns the perpendicular projection of p_point onto this line
     */
    fun perpendicular_projection(p_point: Point): Point {
        return p_point.perpendicular_projection(this)
    }

    /**
     * translates the line perpendicular at about p_dist. If p_dist > 0, the line will be translated to the left, else to the right
     */
    fun translate(p_dist: Double): Line {
        // this function is at the moment only implemented for lines
        // consisting of IntPoints.
        // The general implementation is still missing.
        val ai = a as IntPoint
        val v = direction().get_vector() as IntVector
        val vxvx = v.x.toDouble() * v.x
        val vyvy = v.y.toDouble() * v.y
        val length = Math.sqrt(vxvx + vyvy)
        val new_a: IntPoint
        if (vxvx <= vyvy) {
            // translate along the x axis
            val rel_x = Math.round((p_dist * length) / v.y).toInt()
            new_a = IntPoint(ai.x - rel_x, ai.y)
        } else {
            // translate along the  y axis
            val rel_y = Math.round((p_dist * length) / v.x).toInt()
            new_a = IntPoint(ai.x, ai.y + rel_y)
        }
        return get_instance(new_a, direction())
    }

    /**
     * translates the line by p_vector
     */
    fun translate_by(p_vector: Vector): Line {
        if (p_vector == Vector.ZERO) {
            return this
        }
        val new_a = a.translate_by(p_vector)
        val new_b = b.translate_by(p_vector)
        return Line(new_a, new_b)
    }

    /**
     * returns true, if the line is axis_parallel
     */
    fun is_orthogonal(): Boolean {
        return direction().is_orthogonal()
    }

    /**
     * returns true, if this line is diagonal
     */
    fun is_diagonal(): Boolean {
        return direction().is_diagonal()
    }

    /**
     * returns true, if the direction of this line is a multiple of 45 degree
     */
    fun is_multiple_of_45_degree(): Boolean {
        return direction().is_multiple_of_45_degree()
    }

    /**
     * checks, if this Line and p_other are parallel
     */
    fun is_parallel(p_other: Line): Boolean {
        return this.direction().side_of(p_other.direction()) == Side.COLLINEAR
    }

    /**
     * checks, if this Line and p_other are perpendicular
     */
    fun is_perpendicular(p_other: Line): Boolean {
        val v1 = direction().get_vector()
        val v2 = p_other.direction().get_vector()
        return v1.projection(v2) == Signum.ZERO
    }

    /**
     * returns true, if this and p_ob define the same line
     */
    fun is_equal_or_opposite(p_other: Line): Boolean {
        return side_of(p_other.a) == Side.COLLINEAR && side_of(p_other.b) == Side.COLLINEAR
    }

    /**
     * calculates the cosinus of the angle between this line and p_other
     */
    fun cos_angle(p_other: Line): Double {
        val v1 = b.difference_by(a)
        val v2 = p_other.b.difference_by(p_other.a)
        return v1.cos_angle(v2)
    }

    /**
     * A line l_1 is defined bigger than a line l_2, if the direction of l_1 is bigger than the direction of l_2. Implements the comparable interface. Throws a cast exception, if p_other is not a Line.
     * Fast implementation only for lines consisting of IntPoints because of critical performance
     */
    override fun compareTo(other: Line): Int {
        val this_a = a as IntPoint
        val this_b = b as IntPoint
        val other_a = other.a as IntPoint
        val other_b = other.b as IntPoint
        val dx1 = this_b.x - this_a.x
        val dy1 = this_b.y - this_a.y
        val dx2 = other_b.x - other_a.x
        val dy2 = other_b.y - other_a.y
        if (dy1 > 0) {
            if (dy2 < 0) {
                return -1
            }
            if (dy2 == 0) {
                if (dx2 > 0) {
                    return 1
                }
                return -1
            }
        } else if (dy1 < 0) {
            if (dy2 >= 0) {
                return 1
            }
        } else { // dy1 == 0
            if (dx1 > 0) {
                if (dy2 != 0 || dx2 < 0) {
                    return -1
                }
                return 0
            }
            // dx1 < 0
            if (dy2 > 0 || (dy2 == 0 && dx2 > 0)) {
                return 1
            }
            if (dy2 < 0) {
                return -1
            }
            return 0
        }

        // now this direction and p_other are located in the same
        // open horizontal half plane

        val determinant = dx2.toDouble() * dy1 - dy2.toDouble() * dx1
        return Signum.as_int(determinant)
    }

    /**
     * Calculates an approximation of the function value of this line at p_x, if the line is not vertical.
     */
    fun function_value_approx(p_x: Double): Double {
        val p1 = a.to_float()
        val p2 = b.to_float()
        val dx = p2.x - p1.x
        if (dx == 0.0) {
            FRLogger.warn("function_value_approx: line is vertical")
            return 0.0
        }
        val dy = p2.y - p1.y
        val det = p1.x * p2.y - p2.x * p1.y
        return (dy * p_x - det) / dx
    }

    /**
     * Calculates an approximation of the function value in y of this line at p_y, if the line is not horizontal.
     */
    fun function_in_y_value_approx(p_y: Double): Double {
        val p1 = a.to_float()
        val p2 = b.to_float()
        val dy = p2.y - p1.y
        if (dy == 0.0) {
            FRLogger.warn("function_in_y_value_approx: line is horizontal")
            return 0.0
        }
        val dx = p2.x - p1.x
        val det = p1.x * p2.y - p2.x * p1.y
        return (dx * p_y + det) / dy
    }

    /**
     * Calculates the direction from p_from_point to the nearest point on this line to p_fro_point. Returns null, if p_from_point is contained in this line.
     */
    fun perpendicular_direction(p_from_point: Point): Direction? {
        val line_side = this.side_of(p_from_point)
        if (line_side == Side.COLLINEAR) {
            return null
        }
        val dir1 = this.direction().turn_45_degree(2)
        val dir2 = this.direction().turn_45_degree(6)

        val check_point_1 = p_from_point.translate_by(dir1.get_vector())
        if (this.side_of(check_point_1) != line_side) {
            return dir1
        }
        val check_point_2 = p_from_point.translate_by(dir2.get_vector())
        if (this.side_of(check_point_2) != line_side) {
            return dir2
        }
        val nearest_line_point = p_from_point.to_float().projection_approx(this)
        val result: Direction
        if (nearest_line_point.distance_square(check_point_1.to_float()) <= nearest_line_point.distance_square(check_point_2.to_float())) {
            result = dir1
        } else {
            result = dir2
        }
        return result
    }

    /**
     * Turns this line by p_factor times 90 degree around p_pole.
     */
    fun turn_90_degree(p_factor: Int, p_pole: IntPoint): Line {
        val new_a = a.turn_90_degree(p_factor, p_pole)
        val new_b = b.turn_90_degree(p_factor, p_pole)
        return Line(new_a, new_b)
    }

    /**
     * Mirrors this line at the vertical line through p_pole
     */
    fun mirror_vertical(p_pole: IntPoint): Line {
        val new_a = b.mirror_vertical(p_pole)
        val new_b = a.mirror_vertical(p_pole)
        return Line(new_a, new_b)
    }

    /**
     * Mirrors this line at the horizontal line through p_pole
     */
    fun mirror_horizontal(p_pole: IntPoint): Line {
        val new_a = b.mirror_horizontal(p_pole)
        val new_b = a.mirror_horizontal(p_pole)
        return Line(new_a, new_b)
    }

    fun length(): Float {
        val ipa = a as IntPoint
        val ipb = b as IntPoint

        return Math.sqrt(((ipb.x - ipa.x) * (ipb.x - ipa.x) + (ipb.y - ipa.y) * (ipb.y - ipa.y)).toDouble()).toFloat()
    }

    companion object {
        /**
         * create a directed line from an IntPoint and an IntDirection
         */
        @JvmStatic
        fun get_instance(p_a: Point, p_dir: Direction): Line {
            val b = p_a.translate_by(p_dir.get_vector())
            return Line(p_a, b)
        }
    }
}
