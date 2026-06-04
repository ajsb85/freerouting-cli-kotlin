package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.math.BigInteger

/**
 * Implements the abstract class Direction as a tuple of infinite precision integers.
 */
class BigIntDirection : Direction, Serializable {

    @JvmField
    val x: BigInteger
    @JvmField
    val y: BigInteger

    constructor(p_x: BigInteger, p_y: BigInteger) {
        x = p_x
        y = p_y
    }

    constructor(p_dir: IntDirection) {
        x = BigInteger.valueOf(p_dir.x.toLong())
        y = BigInteger.valueOf(p_dir.y.toLong())
    }

    override fun is_orthogonal(): Boolean {
        return x.signum() == 0 || y.signum() == 0
    }

    override fun is_diagonal(): Boolean {
        return x.abs() == y.abs()
    }

    override fun get_vector(): Vector {
        return RationalVector(x, y, BigInteger.ONE)
    }

    override fun turn_45_degree(p_factor: Int): Direction {
        FRLogger.warn("BigIntDirection: turn_45_degree not yet implemented")
        return this
    }

    override fun opposite(): Direction {
        return BigIntDirection(x.negate(), y.negate())
    }

    override fun compareTo(other: Direction): Int {
        return -other.compareTo(this)
    }

    override fun compareTo(p_other: IntDirection): Int {
        val other = BigIntDirection(p_other)
        return compareTo(other)
    }

    override fun compareTo(p_other: BigIntDirection): Int {
        val x1 = x.signum()
        val y1 = y.signum()
        val x2 = p_other.x.signum()
        val y2 = p_other.y.signum()
        if (y1 > 0) {
            if (y2 < 0) {
                return -1
            }
            if (y2 == 0) {
                if (x2 > 0) {
                    return 1
                }
                return -1
            }
        } else if (y1 < 0) {
            if (y2 >= 0) {
                return 1
            }
        } else { // y1 == 0
            if (x1 > 0) {
                if (y2 != 0 || x2 < 0) {
                    return -1
                }
                return 0
            }
            // x1 < 0
            if (y2 > 0 || (y2 == 0 && x2 > 0)) {
                return 1
            }
            if (y2 < 0) {
                return -1
            }
            return 0
        }

        // now this direction and p_other are located in the same
        // open horizontal half plane

        val tmp_1 = y.multiply(p_other.x)
        val tmp_2 = x.multiply(p_other.y)
        val determinant = tmp_1.subtract(tmp_2)
        return determinant.signum()
    }
}
