package app.freerouting.geometry.planar

import java.io.Serializable
import java.math.BigInteger
import kotlin.math.abs

/**
 * Abstract class describing functionality for Points in the plane.
 */
abstract class Point : Serializable {

    /**
     * returns the translation of this point by p_vector
     */
    abstract fun translate_by(p_vector: Vector): Point

    /**
     * returns the difference vector of this point and p_other
     */
    abstract fun difference_by(p_other: Point): Vector

    /**
     * approximates the coordinates of this point by float coordinates
     */
    abstract fun to_float(): FloatPoint

    /**
     * Returns a unique ID for this point for deterministic tie-breaking.
     */
    abstract fun get_id_no(): Int

    /**
     * returns true, if this Point is a RationalPoint with denominator z = 0.
     */
    abstract fun is_infinite(): Boolean

    /**
     * creates the smallest Box with integer coordinates containing this point.
     */
    abstract fun surrounding_box(): IntBox

    /**
     * creates the smallest Octagon with integer coordinates containing this point.
     */
    abstract fun surrounding_octagon(): IntOctagon

    /**
     * Returns true, if this point lies in the interior or on the border of p_box.
     */
    abstract fun is_contained_in(p_box: IntBox): Boolean

    abstract fun side_of(p_line: Line): Side

    /**
     * returns the nearest point to this point on p_line
     */
    abstract fun perpendicular_projection(p_line: Line): Point

    /**
     * The function returns Side.ON_THE_LEFT, if this Point is on the left of the line from p_1 to p_2; Side.ON_THE_RIGHT, if this Point is on the right of the line from p_1 to p_2; and Side.COLLINEAR,
     * if this Point is collinear with p_1 and p_2.
     */
    open fun side_of(p_1: Point, p_2: Point): Side {
        val v1 = difference_by(p_1)
        val v2 = p_2.difference_by(p_1)
        return v1.side_of(v2)
    }

    /**
     * Calculates the perpendicular direction from this point to p_line. Returns Direction. NULL, if this point lies on p_line.
     */
    fun perpendicular_direction(p_line: Line): Direction {
        val side = this.side_of(p_line)
        if (side == Side.COLLINEAR) {
            return Direction.NULL
        }
        val result: Direction = if (side == Side.ON_THE_RIGHT) {
            p_line.direction().turn_45_degree(2)
        } else {
            p_line.direction().turn_45_degree(6)
        }
        return result
    }

    /**
     * Returns 1, if this Point has a strict bigger x coordinate than p_other, 0, if the x coordinates are equal, and -1 otherwise.
     */
    abstract fun compare_x(p_other: Point): Int

    /**
     * Returns 1, if this Point has a strict bigger y coordinate than p_other, 0, if the y coordinates are equal, and -1 otherwise.
     */
    abstract fun compare_y(p_other: Point): Int

    /**
     * The function returns compare_x (p_other), if the result is not 0. Otherwise, it returns compare_y (p_other).
     */
    fun compare_x_y(p_other: Point): Int {
        var result = compare_x(p_other)
        if (result == 0) {
            result = compare_y(p_other)
        }
        return result
    }

    /**
     * Turns this point by p_factor times 90 degree around p_pole.
     */
    fun turn_90_degree(p_factor: Int, p_pole: Point): Point {
        var v = this.difference_by(p_pole)
        v = v.turn_90_degree(p_factor)
        return p_pole.translate_by(v)
    }

    /**
     * Mirrors this point at the vertical line through p_pole.
     */
    fun mirror_vertical(p_pole: Point): Point {
        var v = this.difference_by(p_pole)
        v = v.mirror_at_y_axis()
        return p_pole.translate_by(v)
    }

    /**
     * Mirrors this point at the horizontal line through p_pole.
     */
    fun mirror_horizontal(p_pole: Point): Point {
        var v = this.difference_by(p_pole)
        v = v.mirror_at_x_axis()
        return p_pole.translate_by(v)
    }

    abstract fun translate_by(p_vector: IntVector): Point

    abstract fun translate_by(p_vector: RationalVector): Point

    abstract fun difference_by(p_other: IntPoint): Vector

    abstract fun difference_by(p_other: RationalPoint): Vector

    abstract fun compare_x(p_other: IntPoint): Int

    abstract fun compare_x(p_other: RationalPoint): Int

    abstract fun compare_y(p_other: IntPoint): Int

    abstract fun compare_y(p_other: RationalPoint): Int

    companion object {
        /**
         * Standard implementation of the zero point .
         */
        @JvmField
        val ZERO: IntPoint = IntPoint(0, 0)

        /**
         * creates an IntPoint from p_x and p_y. If p_x or p_y is too big for an IntPoint, a RationalPoint is created.
         */
        @JvmStatic
        fun get_instance(p_x: Int, p_y: Int): Point {
            val result = IntPoint(p_x, p_y)
            if (abs(p_x) > Limits.CRIT_INT || abs(p_y) > Limits.CRIT_INT) {
                return RationalPoint(result)
            }
            return result
        }

        /**
         * factory method for creating a Point from 3 BigIntegers
         */
        @JvmStatic
        fun get_instance(p_x: BigInteger, p_y: BigInteger, p_z: BigInteger): Point {
            var x = p_x
            var y = p_y
            var z = p_z
            if (z.signum() < 0) {
                // the dominator z of a RationalPoint is expected to be positive
                x = x.negate()
                y = y.negate()
                z = z.negate()
            }
            if (x.mod(z).signum() == 0 && y.mod(z).signum() == 0) {
                // p_x and p_y can be divided by p_z
                x = x.divide(z)
                y = y.divide(z)
                z = BigInteger.ONE
            }
            if (z == BigInteger.ONE) {
                if (x.abs() <= Limits.CRIT_INT_BIG && y.abs() <= Limits.CRIT_INT_BIG) {
                    // the Point fits into an IntPoint
                    return IntPoint(x.toInt(), y.toInt())
                }
            }
            return RationalPoint(x, y, z)
        }
    }
}
