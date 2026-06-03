package app.freerouting.datastructures

/**
 * Interface for creating unique identification number.
 */
interface IdentificationNumberGenerator {

    /**
     * Create a new unique identification number.
     */
    fun new_no(): Int

    /**
     * Return the maximum generated id number so far.
     */
    fun max_generated_no(): Int
}
