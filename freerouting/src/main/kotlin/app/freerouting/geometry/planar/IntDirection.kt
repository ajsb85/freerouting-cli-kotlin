package app.freerouting.geometry.planar

import app.freerouting.datastructures.Signum
import java.io.Serializable

/**
 * Implements an abstract class Direction as an equivalence class of IntVector's.
 */
class IntDirection : Direction, Serializable {

    @JvmField
    val x: Int
    @JvmField
    val y: Int

    constructor(p_x: Int, p_y: Int) {
        x = p_x
        y = p_y
    }

    constructor(p_vector: IntVector) {
        x = p_vector.x
        y = p_vector.y
    }

    override fun is_orthogonal(): Boolean {
        return x == 0 || y == 0
    }

    override fun is_diagonal(): Boolean {
        return Math.abs(x) == Math.abs(y)
    }

    override fun get_vector(): Vector {
        return IntVector(x, y)
    }

    override fun compareTo(other: Direction): Int {
        return -other.compareTo(this)
    }

    override fun compareTo(p_other: IntDirection): Int {
        if (y > 0) {
            if (p_other.y < 0) {
                return -1
            }
            if (p_other.y == 0) {
                if (p_other.x > 0) {
                    return 1
                }
                return -1
            }
        } else if (y < 0) {
            if (p_other.y >= 0) {
                return 1
            }
        } else { // y == 0
            if (x > 0) {
                if (p_other.y != 0 || p_other.x < 0) {
                    return -1
                }
                return 0
            }
            // x < 0
            if (p_other.y > 0 || (p_other.y == 0 && p_other.x > 0)) {
                return 1
            }
            if (p_other.y < 0) {
                return -1
            }
            return 0
        }

        // now this direction and p_other are located in the same
        // open horizontal half plane

        val determinant = p_other.x.toDouble() * y - p_other.y.toDouble() * x
        return Signum.as_int(determinant)
    }

    override fun compareTo(p_other: BigIntDirection): Int {
        return -p_other.compareTo(this)
    }

    override fun opposite(): Direction {
        return IntDirection(-x, -y)
    }

    override fun turn_45_degree(p_factor: Int): Direction {
        var n = p_factor % 8
        if (n < 0) {
            n += 8
        }
        val new_x: Int
        val new_y: Int
        when (n) {
            0 -> { // 0 degree
                new_x = x
                new_y = y
            }
            1 -> { // 45 degree
                new_x = x - y
                new_y = x + y
            }
            2 -> { // 90 degree
                new_x = -y
                new_y = x
            }
            3 -> { // 135 degree
                new_x = -x - y
                new_y = x - y
            }
            4 -> { // 180 degree
                new_x = -x
                new_y = -y
            }
            5 -> { // 225 degree
                new_x = y - x
                new_y = -x - y
            }
            6 -> { // 270 degree
                new_x = y
                new_y = -x
            }
            7 -> { // 315 degree
                new_x = x + y
                new_y = y - x
            }
            else -> {
                new_x = 0
                new_y = 0
            }
        }
        return IntDirection(new_x, new_y)
    }

    fun determinant(p_other: IntDirection): Double {
        return x.toDouble() * p_other.y - y.toDouble() * p_other.x
    }
}
