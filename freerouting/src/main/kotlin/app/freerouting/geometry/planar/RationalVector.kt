package app.freerouting.geometry.planar

import app.freerouting.datastructures.BigIntAux
import app.freerouting.datastructures.Signum
import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.math.BigInteger

/**
 * Analog RationalPoint, but implementing the functionality of a Vector instead of the functionality of a Point.
 */
class RationalVector : Vector, Serializable {

    @JvmField
    internal val x: BigInteger

    @JvmField
    internal val y: BigInteger

    @JvmField
    internal val z: BigInteger

    /**
     * creates a RationalVector from 3 BigIntegers p_x, p_y and p_z. They represent the 2-dimensional Vector with the rational number Tuple ( p_x / p_z , p_y / p_z).
     */
    constructor(p_x: BigInteger, p_y: BigInteger, p_z: BigInteger) {
        if (p_z.signum() >= 0) {
            x = p_x
            y = p_y
            z = p_z
        } else {
            x = p_x.negate()
            y = p_y.negate()
            z = p_z.negate()
        }
    }

    /**
     * creates a RationalVector from an IntVector
     */
    constructor(p_vector: IntVector) {
        x = BigInteger.valueOf(p_vector.x.toLong())
        y = BigInteger.valueOf(p_vector.y.toLong())
        z = BigInteger.ONE
    }

    /**
     * returns true, if the x and y coordinates of this vector are 0
     */
    override fun is_zero(): Boolean {
        return x.signum() == 0 && y.signum() == 0
    }

    /**
     * returns true, if this RationalVector is equal to p_ob
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (javaClass != other.javaClass) {
            return false
        }
        val o = other as RationalVector
        var det = BigIntAux.determinant(x, o.x, z, o.z)
        if (det.signum() != 0) {
            return false
        }
        det = BigIntAux.determinant(y, o.y, z, o.z)

        return det.signum() == 0
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

    /**
     * returns the Vector such that this plus this.minus() is zero
     */
    override fun negate(): Vector {
        return RationalVector(x.negate(), y.negate(), z)
    }

    /**
     * adds p_other to this vector
     */
    override fun add(p_other: Vector): Vector {
        return p_other.add(this)
    }

    /**
     * Let L be the line from the Zero Vector to p_other. The function returns Side.ON_THE_LEFT, if this Vector is on the left of L Side.ON_THE_RIGHT, if this Vector is on the right of L and
     * Side.COLLINEAR, if this Vector is collinear with L.
     */
    override fun side_of(p_other: Vector): Side {
        val tmp = p_other.side_of(this)
        return tmp.negate()
    }

    override fun is_orthogonal(): Boolean {
        return x.signum() == 0 || y.signum() == 0
    }

    override fun is_diagonal(): Boolean {
        return x.abs() == y.abs()
    }

    /**
     * The function returns Signum.POSITIVE, if the scalar product of this vector and p_other {@literal >} 0, Signum.NEGATIVE, if the scalar product is {@literal <} 0, and Signum.ZERO, if the scalar
     * product is equal 0.
     */
    override fun projection(p_other: Vector): Signum {
        return p_other.projection(this)
    }

    /**
     * calculates the scalar product of this vector and p_other
     */
    override fun scalar_product(p_other: Vector): Double {
        return p_other.scalar_product(this)
    }

    /**
     * approximates the coordinates of this vector by float coordinates
     */
    override fun to_float(): FloatPoint {
        val xd = x.toDouble()
        val yd = y.toDouble()
        val zd = z.toDouble()
        return FloatPoint(xd / zd, yd / zd)
    }

    override fun change_length_approx(p_length: Double): Vector {
        FRLogger.warn("RationalVector: change_length_approx not yet implemented")
        return this
    }

    override fun turn_90_degree(p_factor: Int): Vector {
        var n = p_factor
        while (n < 0) {
            n += 4
        }
        while (n >= 4) {
            n -= 4
        }
        val new_x: BigInteger
        val new_y: BigInteger
        when (n) {
            0 -> { // 0 degree
                new_x = x
                new_y = y
            }
            1 -> { // 90 degree
                new_x = y.negate()
                new_y = x
            }
            2 -> { // 180 degree
                new_x = x.negate()
                new_y = y.negate()
            }
            3 -> { // 270 degree
                new_x = y
                new_y = x.negate()
            }
            else -> {
                return this
            }
        }
        return RationalVector(new_x, new_y, this.z)
    }

    override fun mirror_at_y_axis(): Vector {
        return RationalVector(this.x.negate(), this.y, this.z)
    }

    override fun mirror_at_x_axis(): Vector {
        return RationalVector(this.x, this.y.negate(), this.z)
    }

    override fun to_normalized_direction(): Direction {
        var dx = x
        var dy = y
        val gcd = dx.gcd(y)
        dx = dx.divide(gcd)
        dy = dy.divide(gcd)
        if (dx.abs() <= Limits.CRIT_INT_BIG && dy.abs() <= Limits.CRIT_INT_BIG) {
            return IntDirection(dx.toInt(), dy.toInt())
        }
        return BigIntDirection(dx, dy)
    }

    override fun scalar_product(p_other: IntVector): Double {
        val other = RationalVector(p_other)
        return other.scalar_product(this)
    }

    override fun scalar_product(p_other: RationalVector): Double {
        val v1 = to_float()
        val v2 = p_other.to_float()
        return v1.x * v2.x + v1.y * v2.y
    }

    override fun projection(p_other: IntVector): Signum {
        val other = RationalVector(p_other)
        return other.projection(this)
    }

    override fun projection(p_other: RationalVector): Signum {
        val tmp1 = x.multiply(p_other.x)
        val tmp2 = y.multiply(p_other.y)
        val tmp3 = tmp1.add(tmp2)
        val result = tmp3.signum()
        return Signum.of(result.toDouble())
    }

    override fun add(p_other: IntVector): Vector {
        val other = RationalVector(p_other)
        return add(other)
    }

    override fun add(p_other: RationalVector): Vector {
        val v1 = arrayOf(x, y, z)
        val v2 = arrayOf(p_other.x, p_other.y, p_other.z)
        val result = BigIntAux.add_rational_coordinates(v1, v2)
        return RationalVector(result[0], result[1], result[2])
    }

    override fun add_to(p_point: IntPoint): Point {
        var new_x = z.multiply(BigInteger.valueOf(p_point.x.toLong()))
        new_x = new_x.add(x)
        var new_y = z.multiply(BigInteger.valueOf(p_point.y.toLong()))
        new_y = new_y.add(y)
        return RationalPoint(new_x, new_y, z)
    }

    override fun add_to(p_point: RationalPoint): Point {
        val v1 = arrayOf(x, y, z)
        val v2 = arrayOf(p_point.x, p_point.y, p_point.z)
        val result = BigIntAux.add_rational_coordinates(v1, v2)
        return RationalPoint(result[0], result[1], result[2])
    }

    override fun side_of(p_other: IntVector): Side {
        val other = RationalVector(p_other)
        return side_of(other)
    }

    override fun side_of(p_other: RationalVector): Side {
        val tmp_1 = y.multiply(p_other.x)
        val tmp_2 = x.multiply(p_other.y)
        val determinant = tmp_1.subtract(tmp_2)
        val signum = determinant.signum()
        return Side.of(signum.toDouble())
    }
}
