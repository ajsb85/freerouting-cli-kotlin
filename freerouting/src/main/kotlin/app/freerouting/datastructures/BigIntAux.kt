package app.freerouting.datastructures

import java.math.BigInteger

/**
 * Auxiliary functions with BigInteger Parameters
 */
object BigIntAux {

    /*
     * trailingZeroTable[i] is the number of trailing zero bits in the binary
     * representation of i.
     */
    @JvmField
    internal val trailingZeroTable = byteArrayOf(
        -25, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        6, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        7, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        6, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
        5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0
    )

    /**
     * calculates the determinant of the vectors (p_x_1, p_y_1) and (p_x_2, p_y_2)
     */
    @JvmStatic
    fun determinant(p_x_1: BigInteger, p_y_1: BigInteger, p_x_2: BigInteger, p_y_2: BigInteger): BigInteger {
        val tmp1 = p_x_1.multiply(p_y_2)
        val tmp2 = p_x_2.multiply(p_y_1)
        return tmp1.subtract(tmp2)
    }

    /**
     * auxiliary function to implement addition and translation in the classes RationalVector and RationalPoint
     */
    @JvmStatic
    fun add_rational_coordinates(p_first: Array<BigInteger>, p_second: Array<BigInteger>): Array<BigInteger> {
        val result = arrayOf(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO)
        if (p_first[2] == p_second[2]) {
            // both rational numbers have the same denominator
            result[2] = p_first[2]
            result[0] = p_first[0].add(p_second[0])
            result[1] = p_first[1].add(p_second[1])
        } else {
            // multiply both denominators for the new denominator
            // to be on the safe side:
            // taking the least common multiple would be optimal
            result[2] = p_first[2].multiply(p_second[2])
            var tmp_1 = p_first[0].multiply(p_second[2])
            val tmp_2 = p_second[0].multiply(p_first[2])
            result[0] = tmp_1.add(tmp_2)
            tmp_1 = p_first[1].multiply(p_second[2])
            val tmp_3 = p_second[1].multiply(p_first[2])
            result[1] = tmp_1.add(tmp_3)
        }
        return result
    }

    /**
     * Calculate GCD of a and b interpreted as unsigned integers.
     */
    @JvmStatic
    fun binaryGcd(p_a: Int, p_b: Int): Int {
        var a = p_a
        var b = p_b
        if (b == 0) {
            return a
        }
        if (a == 0) {
            return b
        }

        var x: Int
        var aZeros = 0
        while ((a and 0xff).also { x = it } == 0) {
            a = a ushr 8
            aZeros += 8
        }
        var y = trailingZeroTable[x].toInt()
        aZeros += y
        a = a ushr y

        var bZeros = 0
        while ((b and 0xff).also { x = it } == 0) {
            b = b ushr 8
            bZeros += 8
        }
        y = trailingZeroTable[x].toInt()
        bZeros += y
        b = b ushr y

        val t = Math.min(aZeros, bZeros)

        while (a != b) {
            if (a + -0x80000000 > b + -0x80000000) { // a > b as unsigned
                a -= b

                while ((a and 0xff).also { x = it } == 0) {
                    a = a ushr 8
                }
                a = a ushr trailingZeroTable[x].toInt()
            } else {
                b -= a

                while ((b and 0xff).also { x = it } == 0) {
                    b = b ushr 8
                }
                b = b ushr trailingZeroTable[x].toInt()
            }
        }
        return a shl t
    }
}
