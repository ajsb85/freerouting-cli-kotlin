package app.freerouting.core

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.util.Arrays
import java.util.Vector

/**
 * The logical parts contain information for gate swap and pin swap.
 */
class LogicalParts : Serializable {

    /**
     * The array of logical parts
     */
    private val part_arr = Vector<LogicalPart>()

    /**
     * Adds a logical part to the database.
     */
    fun add(p_name: String, p_part_pin_arr: Array<LogicalPart.PartPin>): LogicalPart {
        Arrays.sort(p_part_pin_arr)
        val new_part = LogicalPart(p_name, part_arr.size + 1, p_part_pin_arr)
        part_arr.add(new_part)
        return new_part
    }

    /**
     * Returns the logical part with the input name or null, if no such package exists.
     */
    fun get(p_name: String): LogicalPart? {
        for (curr_part in this.part_arr) {
            if (curr_part != null && curr_part.name.equals(p_name, ignoreCase = true)) {
                return curr_part
            }
        }
        return null
    }

    /**
     * Returns the logical part with index p_part_no. Part numbers are from 1 to part count.
     */
    fun get(p_part_no: Int): LogicalPart? {
        val result = part_arr.elementAt(p_part_no - 1)
        if (result != null && result.no != p_part_no) {
            FRLogger.warn("LogicalParts.get: inconsistent part number")
        }
        return result
    }

    /**
     * Returns the count of logical parts.
     */
    fun count(): Int {
        return part_arr.size
    }
}
