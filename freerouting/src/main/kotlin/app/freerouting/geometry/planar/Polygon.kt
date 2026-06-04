package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.util.LinkedList

/**
 * A Polygon is a list of points in the plane, where no 2 consecutive points may be equal and no 3 consecutive points collinear.
 */
class Polygon(p_point_arr: Array<Point>) : Serializable {

    private val corners: MutableCollection<Point> = LinkedList()

    init {
        if (p_point_arr.isNotEmpty()) {
            corners.addAll(p_point_arr)

            var corner_removed = true
            while (corner_removed) {
                corner_removed = false
                // remove multiple points

                if (corners.isEmpty()) {
                    break
                }
                var i = corners.iterator()
                var curr_ob = i.next()
                while (i.hasNext()) {
                    val next_ob = i.next()
                    if (next_ob == curr_ob) {
                        i.remove()
                        corner_removed = true
                    } else {
                        curr_ob = next_ob
                    }
                }

                // remove points which are collinear with the previous and next point.
                i = corners.iterator()
                if (i.hasNext()) {
                    var prev = i.next()
                    val prev_i = corners.iterator()
                    if (i.hasNext()) {
                        var curr = i.next()
                        prev_i.next()
                        while (i.hasNext()) {
                            val next = i.next()
                            prev_i.next()

                            if (curr.side_of(prev, next) == Side.COLLINEAR) {
                                prev_i.remove()
                                corner_removed = true
                                break
                            }
                            prev = curr
                            curr = next
                        }
                    }
                }
            }
        }
    }

    /**
     * returns the array of corners of this polygon
     */
    fun corner_array(): Array<Point> {
        val corner_count = corners.size
        val result = Array<Point?>(corner_count) { null }
        val it = corners.iterator()
        for (i in 0 until corner_count) {
            result[i] = it.next()
        }
        @Suppress("UNCHECKED_CAST")
        return result as Array<Point>
    }

    /**
     * Reverts the order of the corners of this polygon.
     */
    fun revert_corners(): Polygon {
        val corner_arr = corner_array()
        val reverse_corner_arr = Array<Point?>(corner_arr.size) { null }
        for (i in corner_arr.indices) {
            reverse_corner_arr[i] = corner_arr[corner_arr.size - i - 1]
        }
        @Suppress("UNCHECKED_CAST")
        return Polygon(reverse_corner_arr as Array<Point>)
    }

    /**
     * Returns the winding number of this polygon, treated as closed.
     * It will be > 0, if the corners are in counterclock sense, and < 0, if the corners are in clockwise sense.
     */
    fun winding_number_after_closing(): Int {
        val corner_arr = corner_array()
        if (corner_arr.size < 2) {
            return 0
        }
        val first_side_vector = corner_arr[1].difference_by(corner_arr[0])
        var prev_side_vector = first_side_vector
        var corner_count = corner_arr.size
        // Skip the last corner, if it is equal to the first corner.
        if (corner_arr[0] == corner_arr[corner_count - 1]) {
            --corner_count
        }
        var angle_sum = 0.0
        for (i in 1..corner_count) {
            val next_side_vector = when {
                i == corner_count - 1 -> corner_arr[0].difference_by(corner_arr[i])
                i == corner_count -> first_side_vector
                else -> corner_arr[i + 1].difference_by(corner_arr[i])
            }
            angle_sum += prev_side_vector.angle_approx(next_side_vector)
            prev_side_vector = next_side_vector
        }
        angle_sum /= 2.0 * Math.PI
        if (Math.abs(angle_sum) < 0.5) {
            FRLogger.warn("Polygon.winding_number_after_closing: winding number != 0 expected")
        }
        return Math.round(angle_sum).toInt()
    }
}
