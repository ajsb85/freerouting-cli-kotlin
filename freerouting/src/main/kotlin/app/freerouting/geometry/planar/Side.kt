package app.freerouting.geometry.planar

/**
 * Implementation of an enum class Side with the three values ON_THE_LEFT, ON_THE_RIGHT, COLLINEAR.
 */
enum class Side(private val nameString: String) {
    ON_THE_LEFT("on_the_left"),
    ON_THE_RIGHT("on_the_right"),
    COLLINEAR("collinear");

    /**
     * returns the string of this instance
     */
    fun to_string(): String {
        return nameString
    }

    /**
     * returns the opposite side of this side
     */
    fun negate(): Side {
        return when (this) {
            ON_THE_LEFT -> ON_THE_RIGHT
            ON_THE_RIGHT -> ON_THE_LEFT
            COLLINEAR -> this
        }
    }

    companion object {
        /**
         * returns ON_THE_LEFT, if p_value < 0, ON_THE_RIGHT, if p_value > 0 and COLLINEAR, if p_value == 0
         */
        @JvmStatic
        internal fun of(p_value: Double): Side {
            return when {
                p_value > 0 -> ON_THE_LEFT
                p_value < 0 -> ON_THE_RIGHT
                else -> COLLINEAR
            }
        }
    }
}
