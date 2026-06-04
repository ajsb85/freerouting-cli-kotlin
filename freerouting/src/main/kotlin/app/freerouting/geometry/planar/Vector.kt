package app.freerouting.geometry.planar

import app.freerouting.datastructures.Signum
import java.io.Serializable
import java.math.BigInteger
import kotlin.math.abs

/**
 * Abstract class describing functionality of Vectors. Vectors are used for translating Points in the plane.
 */
abstract class Vector : Serializable {

    /**
     * returns true, if this vector is equal to the zero vector.
     */
    abstract fun is_zero(): Boolean

    /**
     * returns the Vector such that this plus this.negate() is zero
     */
    abstract fun negate(): Vector

    /**
     * adds p_other to this vector
     */
    abstract fun add(p_other: Vector): Vector

    /**
     * Let L be the line from the Zero Vector to p_other. The function returns Side.ON_THE_LEFT, if this Vector is on the left of L Side.ON_THE_RIGHT, if this Vector is on the right of L and
     * Side.COLLINEAR, if this Vector is collinear with L.
     */
    abstract fun side_of(p_other: Vector): Side

    /**
     * returns true, if the vector is horizontal or vertical
     */
    abstract fun is_orthogonal(): Boolean

    /**
     * returns true, if the vector is diagonal
     */
    abstract fun is_diagonal(): Boolean

    /**
     * Returns true, if the vector is orthogonal or diagonal
     */
    fun is_multiple_of_45_degree(): Boolean {
        return is_orthogonal() || is_diagonal()
    }

    /**
     * The function returns Signum.POSITIVE, if the scalar product of this vector and p_other {@literal >} 0, Signum.NEGATIVE, if the scalar product Vector is {@literal <} 0, and Signum.ZERO, if the
     * scalar product is equal 0.
     */
    abstract fun projection(p_other: Vector): Signum

    /**
     * Returns an approximation of the scalar product of this vector with p_other by a double.
     */
    abstract fun scalar_product(p_other: Vector): Double

    /**
     * approximates the coordinates of this vector by float coordinates
     */
    abstract fun to_float(): FloatPoint

    /**
     * Turns this vector by p_factor times 90 degree.
     */
    abstract fun turn_90_degree(p_factor: Int): Vector

    /**
     * Mirrors this vector at the x axis.
     */
    abstract fun mirror_at_x_axis(): Vector

    /**
     * Mirrors this vector at the y axis.
     */
    abstract fun mirror_at_y_axis(): Vector

    /**
     * returns an approximation of the Euclidean length of this vector
     */
    fun length_approx(): Double {
        return this.to_float().size()
    }

    /**
     * Returns an approximation of the cosinus of the angle between this vector and p_other by a double.
     */
    fun cos_angle(p_other: Vector): Double {
        var result = this.scalar_product(p_other)
        result /= this.to_float().size() * p_other.to_float().size()
        return result
    }

    /**
     * Returns an approximation of the signed angle between this vector and p_other.
     */
    fun angle_approx(p_other: Vector): Double {
        var result = Math.acos(cos_angle(p_other))
        if (this.side_of(p_other) == Side.ON_THE_LEFT) {
            result = -result
        }
        return result
    }

    /**
     * Returns an approximation of the signed angle between this vector and the x axis.
     */
    fun angle_approx(): Double {
        val other = IntVector(1, 0)
        return other.angle_approx(this)
    }

    /**
     * Returns an approximation vector of this vector with the same direction and length p_length.
     */
    abstract fun change_length_approx(p_length: Double): Vector

    abstract fun to_normalized_direction(): Direction

    // auxiliary functions needed because the virtual function mechanism
    // does not work in parameter position

    abstract fun add(p_other: IntVector): Vector

    abstract fun add(p_other: RationalVector): Vector

    abstract fun add_to(p_point: IntPoint): Point

    abstract fun add_to(p_point: RationalPoint): Point

    abstract fun side_of(p_other: IntVector): Side

    abstract fun side_of(p_other: RationalVector): Side

    abstract fun projection(p_other: IntVector): Signum

    abstract fun projection(p_other: RationalVector): Signum

    abstract fun scalar_product(p_other: IntVector): Double

    abstract fun scalar_product(p_other: RationalVector): Double

    companion object {
        /**
         * Standard implementation of the zero vector .
         */
        @JvmField
        val ZERO: IntVector = IntVector(0, 0)

        /**
         * Creates a Vector (p_x, p_y) in the plane.
         */
        @JvmStatic
        fun get_instance(p_x: Int, p_y: Int): Vector {
            val result = IntVector(p_x, p_y)
            if (abs(p_x) > Limits.CRIT_INT || abs(p_y) > Limits.CRIT_INT) {
                return RationalVector(result)
            }
            return result
        }

        /**
         * Creates a 2-dimensional Vector from the 3 input values. If p_z != 0 it correspondents to the Vector in the plane with rational number coordinates (p_x / p_z, p_y / p_z).
         */
        @JvmStatic
        fun get_instance(p_x: BigInteger, p_y: BigInteger, p_z: BigInteger): Vector {
            var x = p_x
            var y = p_y
            var z = p_z
            if (z.signum() < 0) {
                // the dominator z of a RationalVector is expected to be positive
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
                    return IntVector(x.toInt(), y.toInt())
                }
            }
            return RationalVector(x, y, z)
        }
    }
}
