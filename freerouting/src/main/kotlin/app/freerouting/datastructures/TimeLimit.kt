package app.freerouting.datastructures

import java.util.Date

/**
 * Class used to cancel a performance critical algorithm after a time limit is exceeded.
 */
class TimeLimit(p_milli_seconds: Int) {

    private val time_stamp: Long = Date().time
    private var time_limit: Int = p_milli_seconds

    /**
     * Returns true, if the time limit provided in the constructor of this class is exceeded.
     */
    fun limit_exceeded(): Boolean {
        val curr_time = Date().time
        return curr_time - this.time_stamp > this.time_limit
    }

    /**
     * Multiplies this TimeLimit by p_factor.
     */
    fun multiply(p_factor: Double) {
        if (p_factor <= 0) {
            return
        }
        var new_limit = p_factor * this.time_limit
        new_limit = Math.min(new_limit, Int.MAX_VALUE.toDouble())
        this.time_limit = new_limit.toInt()
    }
}
