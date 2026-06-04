package app.freerouting.geometry.planar

import app.freerouting.datastructures.BigIntAux
import java.io.Serializable
import java.math.BigInteger
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Implementation of points in the projective plane represented by 3 coordinates x, y, z, which are infinite precision integers. Two projective points (x1, y1, z1) and (x2, y2 z2) are equal, if they
 * are located on the same line through the zero point, that means, there exist a number r with x2 = r*x1, y2 = r*y1 and z2 = r*z1. The affine Point with rational coordinates represented by the
 * projective Point (x, y, z) is (x/z, y/z). The projective plane with integer coordinates contains in addition to the affine plane with rational coordinates the so-called line at infinity, which
 * consist of all projective points (x, y, z) with z = 0.
 */
class RationalPoint : Point, Serializable {

    @JvmField
    internal val x: BigInteger

    @JvmField
    internal val y: BigInteger

    @JvmField
    internal val z: BigInteger

    /**
     * creates a RationalPoint from 3 BigIntegers p_x, p_y and p_z. They represent the 2-dimensional point with the rational number Tuple ( p_x / p_z , p_y / p_z). Throws IllegalArgumentException if
     * denominator p_z is <= 0
     */
    constructor(p_x: BigInteger, p_y: BigInteger, p_z: BigInteger) {
        x = p_x
        y = p_y
        z = p_z
        if (p_z.signum() < 0) {
            throw IllegalArgumentException("RationalPoint: p_z is expected to be >= 0")
        }
    }

    /**
     * creates a RationalPoint from an IntPoint
     */
    constructor(p_point: IntPoint) {
        x = BigInteger.valueOf(p_point.x.toLong())
        y = BigInteger.valueOf(p_point.y.toLong())
        z = BigInteger.ONE
    }

    /**
     * approximates the coordinates of this point by float coordinates
     */
    override fun to_float(): FloatPoint {
        var xd = x.toDouble()
        var yd = y.toDouble()
        val zd = z.toDouble()
        if (zd == 0.0) {
            xd = Float.MAX_VALUE.toDouble()
            yd = Float.MAX_VALUE.toDouble()
        } else {
            xd /= zd
            yd /= zd
        }

        return FloatPoint(xd, yd)
    }

    /**
     * returns true, if this RationalPoint is equal to p_ob
     */
    override fun get_id_no(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

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
        val o = other as RationalPoint
        var det = BigIntAux.determinant(x, o.x, z, o.z)
        if (det.signum() != 0) {
            return false
        }
        det = BigIntAux.determinant(y, o.y, z, o.z)

        return det.signum() == 0
    }

    override fun hashCode(): Int {
        return get_id_no()
    }

    override fun is_infinite(): Boolean {
        return z.signum() == 0
    }

    override fun surrounding_box(): IntBox {
        val fp = to_float()
        val llx = floor(fp.x).toInt()
        val lly = floor(fp.y).toInt()
        val urx = ceil(fp.x).toInt()
        val ury = ceil(fp.y).toInt()
        return IntBox(llx, lly, urx, ury)
    }

    override fun surrounding_octagon(): IntOctagon {
        val fp = to_float()
        val lx = floor(fp.x).toInt()
        val ly = floor(fp.y).toInt()
        val rx = ceil(fp.x).toInt()
        val uy = ceil(fp.y).toInt()

        var tmp = fp.x - fp.y
        val ulx = floor(tmp).toInt()
        val lrx = ceil(tmp).toInt()

        tmp = fp.x + fp.y
        val llx = floor(tmp).toInt()
        val urx = ceil(tmp).toInt()
        return IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx)
    }

    override fun is_contained_in(p_box: IntBox): Boolean {
        var tmp = BigInteger.valueOf(p_box.ll.x.toLong()).multiply(z)
        if (x < tmp) {
            return false
        }
        tmp = BigInteger.valueOf(p_box.ll.y.toLong()).multiply(z)
        if (y < tmp) {
            return false
        }
        tmp = BigInteger.valueOf(p_box.ur.x.toLong()).multiply(z)
        if (x > tmp) {
            return false
        }
        tmp = BigInteger.valueOf(p_box.ur.y.toLong()).multiply(z)
        return y <= tmp
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
        val vector = RationalVector(p_vector)
        return translate_by(vector)
    }

    override fun translate_by(p_vector: RationalVector): Point {
        val v1 = arrayOf(x, y, z)
        val v2 = arrayOf(p_vector.x, p_vector.y, p_vector.z)
        val result = BigIntAux.add_rational_coordinates(v1, v2)
        return RationalPoint(result[0], result[1], result[2])
    }

    /**
     * returns the difference vector of this point and p_other
     */
    override fun difference_by(p_other: Point): Vector {
        val tmp = p_other.difference_by(this)
        return tmp.negate()
    }

    override fun difference_by(p_other: IntPoint): Vector {
        val other = RationalPoint(p_other)
        return difference_by(other)
    }

    override fun difference_by(p_other: RationalPoint): Vector {
        val v1 = arrayOf(x, y, z)
        val v2 = arrayOf(p_other.x.negate(), p_other.y.negate(), p_other.z)
        val result = BigIntAux.add_rational_coordinates(v1, v2)
        return RationalVector(result[0], result[1], result[2])
    }

    /**
     * The function returns Side.ON_THE_LEFT, if this Point is on the left of the line from p_1 to p_2; Side.ON_THE_RIGHT, if this Point is on the right f the line from p_1 to p_2; and Side.COLLINEAR,
     * if this Point is collinear with p_1 and p_2.
     */
    override fun side_of(p_1: Point, p_2: Point): Side {
        val v1 = difference_by(p_1)
        val v2 = p_2.difference_by(p_1)
        return v1.side_of(v2)
    }

    override fun side_of(p_line: Line): Side {
        return side_of(p_line.a, p_line.b)
    }

    override fun perpendicular_projection(p_line: Line): Point {
        // this function is at the moment only implemented for lines
        // consisting of IntPoints.
        // The general implementation is still missing.
        val v = p_line.b.difference_by(p_line.a) as IntVector
        val vxvx = BigInteger.valueOf(v.x.toLong() * v.x)
        val vyvy = BigInteger.valueOf(v.y.toLong() * v.y)
        val vxvy = BigInteger.valueOf(v.x.toLong() * v.y)
        var denominator = vxvx.add(vyvy)
        val det = BigInteger.valueOf((p_line.a as IntPoint).determinant(p_line.b as IntPoint))

        var tmp1 = vxvx.multiply(x)
        var tmp2 = vxvy.multiply(y)
        tmp1 = tmp1.add(tmp2)
        tmp2 = det.multiply(BigInteger.valueOf(v.y.toLong()))
        tmp2 = tmp2.multiply(z)
        var proj_x = tmp1.add(tmp2)

        tmp1 = vxvy.multiply(x)
        tmp2 = vyvy.multiply(y)
        tmp1 = tmp1.add(tmp2)
        tmp2 = det.multiply(BigInteger.valueOf(v.x.toLong()))
        tmp2 = tmp2.multiply(z)
        var proj_y = tmp1.add(tmp2)

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
                if (proj_x.abs() <= Limits.CRIT_INT_BIG && proj_y.abs() <= Limits.CRIT_INT_BIG) {
                    return IntPoint(proj_x.toInt(), proj_y.toInt())
                }
                denominator = BigInteger.ONE
            }
        }
        return RationalPoint(proj_x, proj_y, denominator)
    }

    override fun compare_x(p_other: Point): Int {
        return -p_other.compare_x(this)
    }

    override fun compare_y(p_other: Point): Int {
        return -p_other.compare_y(this)
    }

    override fun compare_x(p_other: RationalPoint): Int {
        val tmp1 = this.x.multiply(p_other.z)
        val tmp2 = p_other.x.multiply(this.z)
        return tmp1.compareTo(tmp2)
    }

    override fun compare_y(p_other: RationalPoint): Int {
        val tmp1 = this.y.multiply(p_other.z)
        val tmp2 = p_other.y.multiply(this.z)
        return tmp1.compareTo(tmp2)
    }

    override fun compare_x(p_other: IntPoint): Int {
        val tmp1 = this.z.multiply(BigInteger.valueOf(p_other.x.toLong()))
        return this.x.compareTo(tmp1)
    }

    override fun compare_y(p_other: IntPoint): Int {
        val tmp1 = this.z.multiply(BigInteger.valueOf(p_other.y.toLong()))
        return this.y.compareTo(tmp1)
    }
}
