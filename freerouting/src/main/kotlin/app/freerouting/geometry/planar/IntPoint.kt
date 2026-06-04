package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.math.BigInteger
import kotlin.math.abs

/**
 * Implementation of the abstract class Point as a tuple of integers.
 */
class IntPoint(
    @JvmField val x: Int,
    @JvmField val y: Int
) : Point(), Serializable {

    init {
        if (abs(x) > Limits.CRIT_INT) {
            FRLogger.debug("IntPoint: p_x is out of range")
        }
        if (abs(y) > Limits.CRIT_INT) {
            FRLogger.debug("IntPoint: p_y is out of range")
        }
    }

    /**
     * Returns true, if this IntPoint is equal to p_ob
     */
    override fun equals(p_ob: Any?): Boolean {
        if (this === p_ob) {
            return true
        }
        if (p_ob == null) {
            return false
        }
        if (javaClass != p_ob.javaClass) {
            return false
        }
        val other = p_ob as IntPoint
        return x == other.x && y == other.y
    }

    override fun hashCode(): Int {
        return get_id_no()
    }

    override fun is_infinite(): Boolean {
        return false
    }

    override fun surrounding_box(): IntBox {
        return IntBox(this, this)
    }

    override fun surrounding_octagon(): IntOctagon {
        val tmp_1 = x - y
        val tmp_2 = x + y
        return IntOctagon(x, y, x, y, tmp_1, tmp_1, tmp_2, tmp_2)
    }

    override fun is_contained_in(p_box: IntBox): Boolean {
        return x >= p_box.ll.x && y >= p_box.ll.y && x <= p_box.ur.x && y <= p_box.ur.y
    }

    /**
     * returns the translation of this point by p_vector
     */
    override fun translate_by(p_vector: Vector): Point {
        if (p_vector.equals(Vector.ZERO)) {
            return this
        }
        return p_vector.add_to(this)
    }

    override fun translate_by(p_vector: IntVector): Point {
        return IntPoint(x + p_vector.x, y + p_vector.y)
    }

    override fun translate_by(p_vector: RationalVector): Point {
        return p_vector.add_to(this)
    }

    /**
     * returns the difference vector of this point and p_other
     */
    override fun difference_by(p_other: Point): Vector {
        val tmp = p_other.difference_by(this)
        return tmp.negate()
    }

    override fun difference_by(p_other: RationalPoint): Vector {
        val tmp = p_other.difference_by(this)
        return tmp.negate()
    }

    override fun difference_by(p_other: IntPoint): IntVector {
        return IntVector(x - p_other.x, y - p_other.y)
    }

    override fun side_of(p_line: Line): Side {
        val v1 = difference_by(p_line.a)
        val v2 = p_line.b.difference_by(p_line.a)
        return v1.side_of(v2)
    }

    /**
     * converts this point to a FloatPoint.
     */
    override fun to_float(): FloatPoint {
        return FloatPoint(this)
    }

    override fun get_id_no(): Int {
        return 31 * x + y
    }

    /**
     * returns the determinant of the vectors (x, y) and (p_other.x, p_other.y)
     */
    fun determinant(p_other: IntPoint): Long {
        return x.toLong() * p_other.y - y.toLong() * p_other.x
    }

    override fun perpendicular_projection(p_line: Line): Point {
        // this function is at the moment only implemented for lines
        // consisting of IntPoints.
        // The general implementation is still missing.
        val v = p_line.b.difference_by(p_line.a) as IntVector
        var vxvx = BigInteger.valueOf(v.x.toLong() * v.x)
        var vyvy = BigInteger.valueOf(v.y.toLong() * v.y)
        val vxvy = BigInteger.valueOf(v.x.toLong() * v.y)
        var denominator = vxvx.add(vyvy)
        val det = BigInteger.valueOf((p_line.a as IntPoint).determinant(p_line.b as IntPoint))
        val point_x = BigInteger.valueOf(x.toLong())
        val point_y = BigInteger.valueOf(y.toLong())

        var tmp1 = vxvx.multiply(point_x)
        var tmp2 = vxvy.multiply(point_y)
        tmp1 = tmp1.add(tmp2)
        tmp2 = det.multiply(BigInteger.valueOf(v.y.toLong()))
        var proj_x = tmp1.add(tmp2)

        tmp1 = vxvy.multiply(point_x)
        tmp2 = vyvy.multiply(point_y)
        tmp1 = tmp1.add(tmp2)
        tmp2 = det.multiply(BigInteger.valueOf(v.x.toLong()))
        var proj_y = tmp1.subtract(tmp2)

        val signum = denominator.signum()
        if (signum != 0) {
            if (signum < 0) {
                denominator = denominator.negate()
                proj_x = proj_x.negate()
                proj_y = proj_y.negate()
            }
            if (proj_x.mod(denominator).signum() == 0 && proj_y.mod(denominator).signum() == 0) {
                proj_x = proj_x.divide(denominator)
                proj_y = proj_y.divide(denominator)
                return IntPoint(proj_x.toInt(), proj_y.toInt())
            }
        }
        return RationalPoint(proj_x, proj_y, denominator)
    }

    /**
     * Returns the signed area of the parallelogramm spanned by the vectors p_2 - p_1 and this - p_1
     */
    fun signed_area(p_1: IntPoint, p_2: IntPoint): Double {
        val d21 = p_2.difference_by(p_1)
        val d01 = this.difference_by(p_1)
        return d21.determinant(d01).toDouble()
    }

    /**
     * calculates the square of the distance between this point and p_to_point
     */
    fun distance_square(p_to_point: IntPoint): Double {
        val dx = p_to_point.x.toDouble() - this.x
        val dy = p_to_point.y.toDouble() - this.y
        return dx * dx + dy * dy
    }

    /**
     * calculates the distance between this point and p_to_point
     */
    fun distance(p_to_point: IntPoint): Double {
        return Math.sqrt(distance_square(p_to_point))
    }

    /**
     * Calculates the nearest point to this point on the horizontal or vertical line through p_other (Snaps this point to on orthogonal line through p_other).
     */
    fun orthogonal_projection(p_other: IntPoint): IntPoint {
        val horizontal_distance = abs(this.x - p_other.x)
        val vertical_distance = abs(this.y - p_other.y)
        return if (horizontal_distance <= vertical_distance) {
            // projection onto the vertical line through p_other
            IntPoint(p_other.x, this.y)
        } else {
            // projection onto the horizontal line through p_other
            IntPoint(this.x, p_other.y)
        }
    }

    /**
     * Calculates the nearest point to this point on an orthogonal or diagonal line through p_other (Snaps this point to on 45 degree line through p_other).
     */
    fun fortyfive_degree_projection(p_other: IntPoint): IntPoint {
        val dx = this.x - p_other.x
        val dy = this.y - p_other.y
        val dist_arr = DoubleArray(4)
        dist_arr[0] = abs(dx).toDouble()
        dist_arr[1] = abs(dy).toDouble()
        val diagonal_1 = (dy.toDouble() - dx.toDouble()) / 2
        val diagonal_2 = (dy.toDouble() + dx.toDouble()) / 2
        dist_arr[2] = abs(diagonal_1)
        dist_arr[3] = abs(diagonal_2)
        var min_dist = dist_arr[0]
        for (i in 1..3) {
            if (dist_arr[i] < min_dist) {
                min_dist = dist_arr[i]
            }
        }
        return when (min_dist) {
            dist_arr[0] -> {
                // projection onto the vertical line through p_other
                IntPoint(p_other.x, this.y)
            }
            dist_arr[1] -> {
                // projection onto the horizontal line through p_other
                IntPoint(this.x, p_other.y)
            }
            dist_arr[2] -> {
                // projection onto the right diagonal line through p_other
                val diagonal_value = diagonal_2.toInt()
                IntPoint(p_other.x + diagonal_value, p_other.y + diagonal_value)
            }
            else -> {
                // projection onto the left diagonal line through p_other
                val diagonal_value = diagonal_1.toInt()
                IntPoint(p_other.x - diagonal_value, p_other.y + diagonal_value)
            }
        }
    }

    /**
     * Calculates a corner point p so that the lines through this point and p and from p to p_to_point are multiples of 45 degree, and that the angle at p will be 45 degree. If p_left_turn, p_to_point
     * will be on the left of the line from this point to p, else on the right. Returns null, if the line from this point to p_to_point is already a multiple of 45 degree.
     */
    fun fortyfive_degree_corner(p_to_point: IntPoint, p_left_turn: Boolean): IntPoint? {
        val dx = p_to_point.x - this.x
        val dy = p_to_point.y - this.y

        // handle the 8 sections between the 45 degree lines

        return if (dy > 0 && dy < dx) {
            if (p_left_turn) {
                IntPoint(p_to_point.x - dy, this.y)
            } else {
                IntPoint(this.x + dy, p_to_point.y)
            }
        } else if (dx > 0 && dy > dx) {
            if (p_left_turn) {
                IntPoint(p_to_point.x, this.y + dx)
            } else {
                IntPoint(this.x, p_to_point.y - dx)
            }
        } else if (dx < 0 && dy > -dx) {
            if (p_left_turn) {
                IntPoint(this.x, p_to_point.y + dx)
            } else {
                IntPoint(p_to_point.x, this.y - dx)
            }
        } else if (dy > 0 && dy < -dx) {
            if (p_left_turn) {
                IntPoint(this.x - dy, p_to_point.y)
            } else {
                IntPoint(p_to_point.x + dy, this.y)
            }
        } else if (dy < 0 && dy > dx) {
            if (p_left_turn) {
                IntPoint(p_to_point.x - dy, this.y)
            } else {
                IntPoint(this.x + dy, p_to_point.y)
            }
        } else if (dx < 0 && dy < dx) {
            if (p_left_turn) {
                IntPoint(p_to_point.x, this.y + dx)
            } else {
                IntPoint(this.x, p_to_point.y - dx)
            }
        } else if (dx > 0 && dy < -dx) {
            if (p_left_turn) {
                IntPoint(this.x, p_to_point.y + dx)
            } else {
                IntPoint(p_to_point.x, this.y - dx)
            }
        } else if (dy < 0 && dy > -dx) {
            if (p_left_turn) {
                IntPoint(this.x - dy, p_to_point.y)
            } else {
                IntPoint(p_to_point.x + dy, this.y)
            }
        } else {
            // the line from this point to p_to_point is already a multiple of 45 degree
            null
        }
    }

    /**
     * Calculates a corner point p so that the lines through this point and p and from p to p_to_point are horizontal or vertical, and that the angle at p will be 90 degree. If p_left_turn, p_to_point
     * will be on the left of the line from this point to p, else on the right. Returns null, if the line from this point to p_to_point is already orthogonal.
     */
    fun ninety_degree_corner(p_to_point: IntPoint, p_left_turn: Boolean): IntPoint? {
        val dx = p_to_point.x - this.x
        val dy = p_to_point.y - this.y

        // handle the 4 quadrants

        return if (dx > 0 && dy > 0 || dx < 0 && dy < 0) {
            if (p_left_turn) {
                IntPoint(p_to_point.x, this.y)
            } else {
                IntPoint(this.x, p_to_point.y)
            }
        } else if (dx < 0 && dy > 0 || dx > 0 && dy < 0) {
            if (p_left_turn) {
                IntPoint(this.x, p_to_point.y)
            } else {
                IntPoint(p_to_point.x, this.y)
            }
        } else {
            // the line from this point to p_to_point is already orthogonal
            null
        }
    }

    override fun compare_x(p_other: Point): Int {
        return -p_other.compare_x(this)
    }

    override fun compare_y(p_other: Point): Int {
        return -p_other.compare_y(this)
    }

    override fun compare_x(p_other: IntPoint): Int {
        return when {
            this.x > p_other.x -> 1
            this.x == p_other.x -> 0
            else -> -1
        }
    }

    override fun compare_y(p_other: IntPoint): Int {
        return when {
            this.y > p_other.y -> 1
            this.y == p_other.y -> 0
            else -> -1
        }
    }

    override fun compare_x(p_other: RationalPoint): Int {
        return -p_other.compare_x(this)
    }

    override fun compare_y(p_other: RationalPoint): Int {
        return -p_other.compare_y(this)
    }

    override fun toString(): String {
        return "($x,$y)"
    }
}
