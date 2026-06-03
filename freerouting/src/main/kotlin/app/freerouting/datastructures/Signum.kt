package app.freerouting.datastructures

/**
 * Implements the mathematical signum function.
 */
class Signum private constructor(private val name: String) {

    /**
     * Returns the string of this instance
     */
    fun to_string(): String {
        return name
    }

    /**
     * Returns the opposite Signum of this Signum
     */
    fun negate(): Signum {
        return when (this) {
            POSITIVE -> NEGATIVE
            NEGATIVE -> POSITIVE
            else -> this
        }
    }

    companion object {
        @JvmField val POSITIVE: Signum = Signum("positive")
        @JvmField val NEGATIVE: Signum = Signum("negative")
        @JvmField val ZERO: Signum = Signum("zero")

        /**
         * Returns the signum of p_value. Values are Signum.POSITIVE, Signum.NEGATIVE and Signum.ZERO
         */
        @JvmStatic
        fun of(p_value: Double): Signum {
            return if (p_value > 0) {
                POSITIVE
            } else if (p_value < 0) {
                NEGATIVE
            } else {
                ZERO
            }
        }

        /**
         * Returns the signum of p_value as an int. Values are +1, 0 and -1
         */
        @JvmStatic
        fun as_int(p_value: Double): Int {
            return if (p_value > 0) {
                1
            } else if (p_value < 0) {
                -1
            } else {
                0
            }
        }
    }
}
