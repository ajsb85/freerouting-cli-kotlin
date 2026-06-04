package app.freerouting.geometry.planar

import app.freerouting.datastructures.BigIntAux
import app.freerouting.datastructures.Signum
import java.io.Serializable
import kotlin.math.abs

/**
 * Implementation of the interface Vector via a tuple of integers
 */
class IntVector(
    @JvmField val x: Int,
    @JvmField val y: Int
) : Vector(), Serializable {

    /**
     * returns true, if this IntVector is equal to p_ob
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
        val other = p_ob as IntVector
        return x == other.x && y == other.y
    }

    override fun hashCode(): Int {
        return 31 * x + y
    }

    /**
     * returns true, if both coordinates of this vector are 0
     */
    override fun is_zero(): Boolean {
        return x == 0 && y == 0
    }

    /**
     * returns the Vector such that this plus this.minus() is zero
     */
    override fun negate(): Vector {
        return IntVector(-x, -y)
    }

    override fun is_orthogonal(): Boolean {
        return x == 0 || y == 0
    }

    override fun is_diagonal(): Boolean {
        return abs(x) == abs(y)
    }

    /**
     * Calculates the determinant of the matrix consisting of this Vector and p_other.
     */
    fun determinant(p_other: IntVector): Long {
        return x.toLong() * p_other.y - y.toLong() * p_other.x
    }

    override fun turn_90_degree(p_factor: Int): Vector {
        var n = p_factor
        while (n < 0) {
            n += 4
        }
        while (n >= 4) {
            n -= 4
        }
        val new_x: Int
        val new_y: Int
        when (n) {
            0 -> { // 0 degree
                new_x = x
                new_y = y
            }
            1 -> { // 90 degree
                new_x = -y
                new_y = x
            }
            2 -> { // 180 degree
                new_x = -x
                new_y = -y
            }
            3 -> { // 270 degree
                new_x = y
                new_y = -x
            }
            else -> {
                new_x = 0
                new_y = 0
            }
        }
        return IntVector(new_x, new_y)
    }

    override fun mirror_at_y_axis(): Vector {
        return IntVector(-this.x, this.y)
    }

    override fun mirror_at_x_axis(): Vector {
        return IntVector(this.x, -this.y)
    }

    /**
     * adds p_other to this vector
     */
    override fun add(p_other: Vector): Vector {
        return p_other.add(this)
    }

    override fun add(p_other: IntVector): Vector {
        return IntVector(x + p_other.x, y + p_other.y)
    }

    override fun add(p_other: RationalVector): Vector {
        return p_other.add(this)
    }

    /**
     * returns the Point, which results from adding this vector to p_point
     */
    override fun add_to(p_point: IntPoint): Point {
        return IntPoint(p_point.x + x, p_point.y + y)
    }

    override fun add_to(p_point: RationalPoint): Point {
        return p_point.translate_by(this)
    }

    /**
     * Let L be the line from the Zero Vector to p_other. The function returns Side.ON_THE_LEFT, if this Vector is on the left of L Side.ON_THE_RIGHT, if this Vector is on the right of L and
     * Side.COLLINEAR, if this Vector is collinear with L.
     */
    override fun side_of(p_other: Vector): Side {
        val tmp = p_other.side_of(this)
        return tmp.negate()
    }

    override fun side_of(p_other: IntVector): Side {
        val determinant = p_other.x.toDouble() * y - p_other.y.toDouble() * x
        return Side.of(determinant)
    }

    override fun side_of(p_other: RationalVector): Side {
        val tmp = p_other.side_of(this)
        return tmp.negate()
    }

    /**
     * The function returns Signum.POSITIVE, if the scalar product of this vector and p_other {@literal >} 0, Signum.NEGATIVE, if the scalar product Vector is {@literal <} 0, and Signum.ZERO, if the
     * scalar product is equal 0.
     */
    override fun projection(p_other: Vector): Signum {
        return p_other.projection(this)
    }

    override fun scalar_product(p_other: Vector): Double {
        return p_other.scalar_product(this)
    }

    /**
     * converts this vector to a PointFloat.
     */
    override fun to_float(): FloatPoint {
        return FloatPoint(x.toDouble(), y.toDouble())
    }

    override fun change_length_approx(p_length: Double): Vector {
        val new_point = this.to_float().change_size(p_length)
        return new_point.round().difference_by(Point.ZERO)
    }

    override fun to_normalized_direction(): Direction {
        var dx = x
        var dy = y

        val gcd = BigIntAux.binaryGcd(abs(dx), abs(dy))
        if (gcd > 1) {
            dx /= gcd
            dy /= gcd
        }
        return IntDirection(dx, dy)
    }

    /**
     * The function returns Signum.POSITIVE, if the scalar product of this vector and p_other > 0, Signum.NEGATIVE, if the scalar product Vector is < 0, and Signum.ZERO, if the scalar product is equal
     * 0.
     */
    override fun projection(p_other: IntVector): Signum {
        val tmp = x.toDouble() * p_other.x + y.toDouble() * p_other.y
        return Signum.of(tmp)
    }

    override fun scalar_product(p_other: IntVector): Double {
        return x.toDouble() * p_other.x + y.toDouble() * p_other.y
    }

    override fun scalar_product(p_other: RationalVector): Double {
        return p_other.scalar_product(this)
    }

    override fun projection(p_other: RationalVector): Signum {
        return p_other.projection(this)
    }
}
