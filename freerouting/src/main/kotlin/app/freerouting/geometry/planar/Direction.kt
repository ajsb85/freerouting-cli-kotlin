package app.freerouting.geometry.planar

import app.freerouting.datastructures.Signum
import java.io.Serializable

/**
 * Abstract class defining functionality of directions in the plane. A Direction is an equivalence class of vectors. Two vectors define the same object of class Direction, if they point into the same
 * direction. We prefer using directions instead of angles, because with angles the arithmetic calculations are in general not exact.
 */
abstract class Direction : Comparable<Direction>, Serializable {

    abstract fun get_vector(): Vector

    abstract fun is_orthogonal(): Boolean

    abstract fun is_diagonal(): Boolean

    fun is_multiple_of_45_degree(): Boolean {
        return is_orthogonal() || is_diagonal()
    }

    abstract fun turn_45_degree(p_factor: Int): Direction

    abstract fun opposite(): Direction

    fun equals(p_other: Direction?): Boolean {
        if (this === p_other) {
            return true
        }
        if (p_other == null) {
            return false
        }

        if (this.side_of(p_other) != Side.COLLINEAR) {
            return false
        }
        // check, that dir and other_dir do not point into opposite directions
        val thisVector = get_vector()
        val otherVector = p_other.get_vector()
        return thisVector.projection(otherVector) == Signum.POSITIVE
    }

    override fun equals(other: Any?): Boolean {
        if (other is Direction) {
            return equals(other)
        }
        return false
    }

    override fun hashCode(): Int {
        return get_vector().hashCode()
    }

    fun side_of(p_other: Direction): Side {
        return this.get_vector().side_of(p_other.get_vector())
    }

    fun projection(p_other: Direction): Signum {
        return this.get_vector().projection(p_other.get_vector())
    }

    fun middle_approx(p_other: Direction): Direction {
        val v1 = get_vector().to_float()
        val v2 = p_other.get_vector().to_float()
        val length1 = v1.size()
        val length2 = v2.size()
        val x = v1.x / length1 + v2.x / length2
        val y = v1.y / length1 + v2.y / length2
        val scaleFactor = 1000.0
        val vm = IntVector(Math.round(x * scaleFactor).toInt(), Math.round(y * scaleFactor).toInt())
        return get_instance(vm)
    }

    fun compare_from(p_1: Direction, p_2: Direction): Int {
        val result: Int
        if (p_1 >= this) {
            if (p_2 >= this) {
                result = p_1.compareTo(p_2)
            } else {
                result = -1
            }
        } else {
            if (p_2 >= this) {
                result = 1
            } else {
                result = p_1.compareTo(p_2)
            }
        }
        return result
    }

    fun angle_approx(): Double {
        return this.get_vector().angle_approx()
    }

    abstract fun compareTo(p_other: IntDirection): Int

    abstract fun compareTo(p_other: BigIntDirection): Int

    override fun toString(): String {
        if (this.compareTo(RIGHT) == 0) {
            return "RIGHT"
        } else if (this.compareTo(RIGHT45) == 0) {
            return "UP-RIGHT"
        } else if (this.compareTo(UP) == 0) {
            return "UP"
        } else if (this.compareTo(UP45) == 0) {
            return "UP-LEFT"
        } else if (this.compareTo(LEFT) == 0) {
            return "LEFT"
        } else if (this.compareTo(LEFT45) == 0) {
            return "DOWN-LEFT"
        } else if (this.compareTo(DOWN) == 0) {
            return "DOWN"
        } else if (this.compareTo(DOWN45) == 0) {
            return "DOWN-RIGHT"
        } else if (this.compareTo(NULL) == 0) {
            return "NULL"
        } else {
            return "UNKNOWN"
        }
    }

    companion object {
        @JvmField
        val NULL = IntDirection(0, 0)
        @JvmField
        val RIGHT = IntDirection(1, 0)
        @JvmField
        val RIGHT45 = IntDirection(1, 1)
        @JvmField
        val UP = IntDirection(0, 1)
        @JvmField
        val UP45 = IntDirection(-1, 1)
        @JvmField
        val LEFT = IntDirection(-1, 0)
        @JvmField
        val LEFT45 = IntDirection(-1, -1)
        @JvmField
        val DOWN = IntDirection(0, -1)
        @JvmField
        val DOWN45 = IntDirection(1, -1)

        @JvmStatic
        fun get_instance(p_vector: Vector): Direction {
            return p_vector.to_normalized_direction()
        }

        @JvmStatic
        fun get_instance(p_from: Point, p_to: Point): Direction? {
            if (p_from == p_to) {
                return null
            }
            return get_instance(p_to.difference_by(p_from))
        }

        @JvmStatic
        fun get_instance_approx(p_angle: Double): Direction {
            val scaleFactor = 10000.0
            val x = Math.round(Math.cos(p_angle) * scaleFactor).toInt()
            val y = Math.round(Math.sin(p_angle) * scaleFactor).toInt()
            return get_instance(IntVector(x, y))
        }
    }
}
