package app.freerouting.geometry.planar

import app.freerouting.datastructures.Signum
import app.freerouting.logger.FRLogger
import java.io.Serializable

/**
 * Implements functionality for line segments. The difference between a LineSegment and a Line is, that a Line is infinite and a LineSegment has a start and an endpoint.
 */
class LineSegment : Serializable {

    @JvmField val start: Line?
    @JvmField val middle: Line?
    @JvmField val end: Line?

    @Transient
    private var precalculated_start_point: Point? = null

    @Transient
    private var precalculated_end_point: Point? = null

    /**
     * Creates a line segment from the 3 input lines. It starts at the intersection of p_start_line and p_middle_line and ends at the intersection of p_middle_line and p_end_line. p_start_line and
     * p_end_line must not be parallel to p_middle_line.
     */
    constructor(p_start_line: Line?, p_middle_line: Line?, p_end_line: Line?) {
        start = p_start_line
        middle = p_middle_line
        end = p_end_line
    }

    /**
     * creates the p_no-th line segment of p_polyline for p_no between 1 and p_polyline.line_count - 2.
     */
    constructor(p_polyline: Polyline, p_no: Int) {
        if (p_no <= 0 || p_no >= p_polyline.arr.size - 1) {
            FRLogger.warn("LineSegment from Polyline: p_no out of range")
            start = null
            middle = null
            end = null
            return
        }
        start = p_polyline.arr[p_no - 1]
        middle = p_polyline.arr[p_no]
        end = p_polyline.arr[p_no + 1]
    }

    /**
     * Creates the p_no-th line segment of p_shape for p_no between 0 and p_shape.line_count - 1.
     */
    constructor(p_shape: PolylineShape, p_no: Int) {
        val line_count = p_shape.border_line_count()
        if (p_no < 0 || p_no >= line_count) {
            FRLogger.warn("LineSegment from TileShape: p_no out of range")
            start = null
            middle = null
            end = null
            return
        }
        start = if (p_no == 0) {
            p_shape.border_line(line_count - 1)
        } else {
            p_shape.border_line(p_no - 1)
        }
        middle = p_shape.border_line(p_no)
        end = if (p_no == line_count - 1) {
            p_shape.border_line(0)
        } else {
            p_shape.border_line(p_no + 1)
        }
    }

    /**
     * Returns the intersection of the first 2 lines of this segment
     */
    fun start_point(): Point {
        if (precalculated_start_point == null) {
            precalculated_start_point = middle!!.intersection(start!!)
        }
        return precalculated_start_point!!
    }

    /**
     * Returns the intersection of the last 2 lines of this segment
     */
    fun end_point(): Point {
        if (precalculated_end_point == null) {
            precalculated_end_point = middle!!.intersection(end!!)
        }
        return precalculated_end_point!!
    }

    /**
     * Returns an approximation of the intersection of the first 2 lines of this segment
     */
    fun start_point_approx(): FloatPoint {
        val result: FloatPoint
        val startPt = precalculated_start_point
        if (startPt != null) {
            result = startPt.to_float()
        } else {
            result = this.start!!.intersection_approx(this.middle!!)
        }
        return result
    }

    /**
     * Returns an approximation of the intersection of the last 2 lines of this segment
     */
    fun end_point_approx(): FloatPoint {
        val result: FloatPoint
        val endPt = precalculated_end_point
        if (endPt != null) {
            result = endPt.to_float()
        } else {
            result = this.end!!.intersection_approx(this.middle!!)
        }
        return result
    }

    /**
     * Returns the (infinite) line of this segment.
     */
    fun get_line(): Line? {
        return middle
    }

    /**
     * Returns the start closing line of this segment.
     */
    fun get_start_closing_line(): Line? {
        return start
    }

    /**
     * Returns the end closing line of this segment.
     */
    fun get_end_closing_line(): Line? {
        return end
    }

    /**
     * Returns the line segment with the opposite direction.
     */
    fun opposite(): LineSegment {
        return LineSegment(end!!.opposite(), middle!!.opposite(), start!!.opposite())
    }

    /**
     * Transforms this LineSegment into a polyline of length 3.
     */
    fun to_polyline(): Polyline {
        val lines = arrayOf(start, middle, end)
        return Polyline(lines)
    }

    /**
     * Creates a 1 dimensional simplex rom this line segment, which has the same shape as the line segment.
     */
    fun to_simplex(): Simplex {
        val lineArr = arrayOf(
            if (this.end_point().side_of(this.start!!) == Side.ON_THE_RIGHT) this.start.opposite() else this.start,
            this.middle!!,
            this.middle.opposite(),
            if (this.start_point().side_of(this.end!!) == Side.ON_THE_RIGHT) this.end.opposite() else this.end
        )
        return Simplex.get_instance(lineArr)
    }

    /**
     * Checks if p_point is contained in this line segment
     */
    fun contains(p_point: Point): Boolean {
        if (p_point !is IntPoint) {
            FRLogger.warn("LineSegments.contains currently only implemented for IntPoints")
            return false
        }
        if (middle!!.side_of(p_point) != Side.COLLINEAR) {
            return false
        }
        // create a perpendicular line at p_point and check, that the two
        // endpoints of this segment are on different sides of that line.
        val perpendicular_direction = middle.direction().turn_45_degree(2)
        val perpendicular_line = Line(p_point, perpendicular_direction)
        val start_point_side = perpendicular_line.side_of(this.start_point())
        val end_point_side = perpendicular_line.side_of(this.end_point())
        return start_point_side != end_point_side || start_point_side == Side.COLLINEAR
    }

    /**
     * calculates the smallest surrounding box of this line segment
     */
    fun bounding_box(): IntBox {
        val start_corner = middle!!.intersection_approx(start!!)
        val end_corner = middle.intersection_approx(end!!)
        val llx = Math.min(start_corner.x, end_corner.x)
        val lly = Math.min(start_corner.y, end_corner.y)
        val urx = Math.max(start_corner.x, end_corner.x)
        val ury = Math.max(start_corner.y, end_corner.y)
        val lower_left = IntPoint(Math.floor(llx).toInt(), Math.floor(lly).toInt())
        val upper_right = IntPoint(Math.ceil(urx).toInt(), Math.ceil(ury).toInt())
        return IntBox(lower_left, upper_right)
    }

    /**
     * calculates the smallest surrounding octagon of this line segment
     */
    fun bounding_octagon(): IntOctagon {
        val start_corner = middle!!.intersection_approx(start!!)
        val end_corner = middle.intersection_approx(end!!)
        val lx = Math.floor(Math.min(start_corner.x, end_corner.x))
        val ly = Math.floor(Math.min(start_corner.y, end_corner.y))
        val rx = Math.ceil(Math.max(start_corner.x, end_corner.x))
        val uy = Math.ceil(Math.max(start_corner.y, end_corner.y))
        val start_x_minus_y = start_corner.x - start_corner.y
        val end_x_minus_y = end_corner.x - end_corner.y
        val ulx = Math.floor(Math.min(start_x_minus_y, end_x_minus_y))
        val lrx = Math.ceil(Math.max(start_x_minus_y, end_x_minus_y))
        val start_x_plus_y = start_corner.x + start_corner.y
        val end_x_plus_y = end_corner.x + end_corner.y
        val llx2 = Math.floor(Math.min(start_x_plus_y, end_x_plus_y))
        val urx2 = Math.ceil(Math.max(start_x_plus_y, end_x_plus_y))
        val result = IntOctagon(lx.toInt(), ly.toInt(), rx.toInt(), uy.toInt(), ulx.toInt(), lrx.toInt(), llx2.toInt(), urx2.toInt())
        return result.normalize()
    }

    /**
     * Creates a new line segment with the same start and middle line and an end line, so that the length of the new line segment is about p_new_length.
     */
    fun change_length_approx(p_new_length: Double): LineSegment {
        val new_end_point = start_point_approx().change_length(end_point_approx(), p_new_length)
        val perpendicular_direction = this.middle!!.direction().turn_45_degree(2)
        val new_end_line = Line(new_end_point.round(), perpendicular_direction)
        return LineSegment(this.start, this.middle, new_end_line)
    }

    /**
     * Looks up the intersections of this line segment with p_other. The result array may have length 0, 1 or 2. If the segments do not intersect the result array will have length 0. The result lines
     * are so that the intersections of the result lines with this line segment will deliver the intersection points. If the segments overlap, the result array has length 2 and the intersection points
     * are the first and the last overlap point. Otherwise, the result array has length 1 and the intersection point is the unique intersection or touching point. The result is not symmetric in this and
     * p_other, because intersecting lines and not the intersection points are returned.
     */
    fun intersection(p_other: LineSegment): Array<Line> {
        if (!this.bounding_box().intersects(p_other.bounding_box())) {
            return emptyArray()
        }
        val start_point_side = start_point().side_of(p_other.middle!!)
        val end_point_side = end_point().side_of(p_other.middle)
        if (start_point_side == Side.COLLINEAR && end_point_side == Side.COLLINEAR) {
            // there may be an overlap
            val this_sorted = this.sort_endpoints_in_x_y()
            val other_sorted = p_other.sort_endpoints_in_x_y()
            val left_line: LineSegment
            val right_line: LineSegment
            if (this_sorted.start_point().compare_x_y(other_sorted.start_point()) <= 0) {
                left_line = this_sorted
                right_line = other_sorted
            } else {
                left_line = other_sorted
                right_line = this_sorted
            }
            val cmp = left_line.end_point().compare_x_y(right_line.start_point())
            if (cmp < 0) {
                // end point of the left line is to the left of the start point of the right line
                return emptyArray()
            }
            if (cmp == 0) {
                // end point of the left line is equal to the start point of the right line
                return arrayOf(left_line.end!!)
            }
            // now there is a real overlap
            val res0 = right_line.start!!
            val res1 = if (right_line.end_point().compare_x_y(left_line.end_point()) >= 0) {
                left_line.end!!
            } else {
                right_line.end!!
            }
            return arrayOf(res0, res1)
        }
        if (start_point_side == end_point_side || p_other.start_point().side_of(this.middle!!) == p_other.end_point().side_of(this.middle)) {
            return emptyArray() // no intersection possible
        }
        // now both start points and both end points are on different sides of the middle
        // line of the other segment.
        return arrayOf(p_other.middle)
    }

    /**
     * Checks if this LineSegment and p_other contain a common point
     */
    fun intersects(p_other: LineSegment): Boolean {
        val intersections = this.intersection(p_other)
        return intersections.isNotEmpty()
    }

    /**
     * Checks if this LineSegment and p_other contain a common LineSegment, which is not reduced to a point.
     */
    fun overlaps(p_other: LineSegment): Boolean {
        val intersections = this.intersection(p_other)
        return intersections.size > 1
    }

    /**
     * Constructs an approximation of this line segment by orthogonal stairs with integer coordinates. The length of the stairs will be at most p_stair_width. If p_to_the_right, the stairs will be to
     * the right of this line segment, else to the left.
     */
    fun stair_approximation(p_width: Double, p_to_the_right: Boolean): Array<IntPoint> {
        val start_point = this.start_point().to_float().round()
        val end_point = this.end_point().to_float().round()
        if (start_point == end_point) {
            return emptyArray()
        }

        if (start_point.x == end_point.x || start_point.y == end_point.y) {
            return arrayOf(start_point, end_point)
        }

        val dx = end_point.x - start_point.x
        val dy = end_point.y - start_point.y
        val abs_dx = Math.abs(dx)
        val abs_dy = Math.abs(dy)
        val function_of_x = abs_dx >= abs_dy

        var stair_width: Int
        val stair_count: Int

        if (function_of_x) {
            stair_width = Math.round((p_width * abs_dx) / abs_dy).toInt()
            stair_count = (abs_dx - 1) / stair_width + 1
            if (end_point.x < start_point.x) {
                stair_width = -stair_width
            }
        } else {
            stair_width = Math.round((p_width * abs_dy) / abs_dx).toInt()
            stair_count = (abs_dy - 1) / stair_width + 1
            if (end_point.y < start_point.y) {
                stair_width = -stair_width
            }
        }
        val result = arrayOfNulls<IntPoint>(2 * stair_count + 1)

        result[0] = start_point
        val det = dx.toDouble() * dy.toDouble()
        val change_x_first = p_to_the_right && det > 0 || !p_to_the_right && det < 0
        var curr_index = 0

        var prev_line_point_x = start_point.x
        var prev_line_point_y = start_point.y
        for (i in 1 until stair_count) {
            val curr_line_point_x: Int
            val curr_line_point_y: Int
            if (function_of_x) {
                curr_line_point_x = start_point.x + i * stair_width
                curr_line_point_y = Math.round(this.get_line()!!.function_value_approx(curr_line_point_x.toDouble())).toInt()
            } else {
                curr_line_point_y = start_point.y + i * stair_width
                curr_line_point_x = Math.round(this.get_line()!!.function_in_y_value_approx(curr_line_point_y.toDouble())).toInt()
            }
            ++curr_index
            if (change_x_first) {
                result[curr_index] = IntPoint(curr_line_point_x, prev_line_point_y)
            } else {
                result[curr_index] = IntPoint(prev_line_point_x, curr_line_point_y)
            }
            ++curr_index
            result[curr_index] = IntPoint(curr_line_point_x, curr_line_point_y)
            prev_line_point_x = curr_line_point_x
            prev_line_point_y = curr_line_point_y
        }
        ++curr_index
        if (change_x_first) {
            result[curr_index] = IntPoint(end_point.x, prev_line_point_y)
        } else {
            result[curr_index] = IntPoint(prev_line_point_x, end_point.y)
        }
        ++curr_index
        result[curr_index] = end_point
        return result.filterNotNull().toTypedArray()
    }

    /**
     * Constructs an approximation of this line segment by 45 degree stairs with integer coordinates. The length of the stairs will be at most p_stair_width. If p_to_the_right, the stairs will be to the
     * right of this line segment, else to the left.
     */
    fun stair_approximation_45(p_width: Double, p_to_the_right: Boolean): Array<IntPoint> {
        val start_point = this.start_point().to_float().round()
        val end_point = this.end_point().to_float().round()
        if (start_point == end_point) {
            return emptyArray()
        }
        val delta = end_point.difference_by(start_point)
        if (delta.is_multiple_of_45_degree()) {
            return arrayOf(start_point, end_point)
        }
        val abs_delta = IntVector(Math.abs(delta.x), Math.abs(delta.y))
        val function_of_x = abs_delta.x >= abs_delta.y
        val det = delta.x.toDouble() * delta.y.toDouble()
        var stair_width: Int
        val stair_count: Int
        if (function_of_x) {
            stair_width = Math.round((p_width * abs_delta.x) / abs_delta.y).toInt()
            stair_count = (abs_delta.x - 1) / stair_width + 1
            if (end_point.x < start_point.x) {
                stair_width = -stair_width
            }
        } else {
            stair_width = Math.round((p_width * abs_delta.y) / abs_delta.x).toInt()
            stair_count = (abs_delta.y - 1) / stair_width + 1
            if (end_point.y < start_point.y) {
                stair_width = -stair_width
            }
        }
        val result = arrayOfNulls<IntPoint>(2 * stair_count + 1)
        result[0] = start_point
        var prev_line_point = start_point
        var curr_index = 0
        for (i in 1..stair_count) {
            val curr_line_point: IntPoint
            var curr_x: Int
            var curr_y: Int
            if (i == stair_count) {
                curr_line_point = end_point
            } else {
                if (function_of_x) {
                    curr_x = start_point.x + i * stair_width
                    curr_y = Math.round(this.get_line()!!.function_value_approx(curr_x.toDouble())).toInt()
                } else {
                    curr_y = start_point.y + i * stair_width
                    curr_x = Math.round(this.get_line()!!.function_value_approx(curr_y.toDouble())).toInt()
                }
                curr_line_point = IntPoint(curr_x, curr_y)
            }
            if (function_of_x) {
                val diagonal_first = p_to_the_right && det < 0 || !p_to_the_right && det > 0

                if (diagonal_first) {
                    curr_x = prev_line_point.x + Signum.as_int(stair_width.toDouble()) * Math.abs(curr_line_point.y - prev_line_point.y)
                    curr_y = curr_line_point.y
                } else { // horizontal first
                    curr_x = curr_line_point.x - Signum.as_int(stair_width.toDouble()) * Math.abs(curr_line_point.y - prev_line_point.y)
                    curr_y = prev_line_point.y
                }
            } else { // function of y
                val diagonal_first = p_to_the_right && det > 0 || !p_to_the_right && det < 0

                if (diagonal_first) {
                    curr_x = curr_line_point.x
                    curr_y = prev_line_point.y + Signum.as_int(stair_width.toDouble()) * Math.abs(curr_line_point.x - prev_line_point.x)
                } else {
                    curr_x = prev_line_point.x
                    curr_y = curr_line_point.y - Signum.as_int(stair_width.toDouble()) * Math.abs(curr_line_point.x - prev_line_point.x)
                }
            }
            ++curr_index
            result[curr_index] = IntPoint(curr_x, curr_y)
            ++curr_index
            result[curr_index] = curr_line_point
            prev_line_point = curr_line_point
        }
        return result.filterNotNull().toTypedArray()
    }

    /**
     * Returns an array with the borderline numbers of p_shape, which are intersected by this line segment. Intersections at an endpoint of this line segment are only counted, if the line segment
     * intersects with the interior of p_shape. The result array may have length 0, 1 or 2. With 2 intersections the intersection which is nearest to the start point of the line segment comes first.
     */
    fun border_intersections(p_shape: TileShape): IntArray {
        val empty_result = IntArray(0)
        if (!this.bounding_box().intersects(p_shape.bounding_box())) {
            return empty_result
        }

        val edge_count = p_shape.border_line_count()
        var prev_line = p_shape.border_line(edge_count - 1)
        var curr_line = p_shape.border_line(0)
        val result = IntArray(2)
        val intersection = arrayOfNulls<Point>(2)
        var intersection_count = 0
        val line_start = this.start_point()
        val line_end = this.end_point()

        for (edge_line_no in 0 until edge_count) {
            val next_line = if (edge_line_no == edge_count - 1) {
                p_shape.border_line(0)
            } else {
                p_shape.border_line(edge_line_no + 1)
            }

            val start_point_side = curr_line.side_of(line_start)
            val end_point_side = curr_line.side_of(line_end)
            if (start_point_side == Side.ON_THE_LEFT && end_point_side == Side.ON_THE_LEFT) {
                // both endpoints are outside the border_line,
                // no intersection possible
                return empty_result
            }

            if (start_point_side == Side.COLLINEAR) {
                // the start is on curr_line, check that the end point is inside
                // the halfplane, because touches count only, if the interior
                // is entered
                if (end_point_side != Side.ON_THE_RIGHT) {
                    return empty_result
                }
            }

            if (end_point_side == Side.COLLINEAR) {
                // the end is on curr_line, check that the start point is inside
                // the halfplane, because touches count only, if the interior
                // is entered
                if (start_point_side != Side.ON_THE_RIGHT) {
                    return empty_result
                }
            }

            if (start_point_side != Side.ON_THE_RIGHT || end_point_side != Side.ON_THE_RIGHT) {
                // not both points are inside the halplane defined by curr_line
                val intersectPt = this.middle!!.intersection(curr_line)
                val prev_line_side_of_is = prev_line.side_of(intersectPt)
                val next_line_side_of_is = next_line.side_of(intersectPt)
                if (prev_line_side_of_is != Side.ON_THE_LEFT && next_line_side_of_is != Side.ON_THE_LEFT) {
                    // this line segment intersects curr_line between the
                    // previous and the next corner of p_simplex

                    if (prev_line_side_of_is == Side.COLLINEAR) {
                        // this line segment goes through the previous
                        // corner of p_simplex. Check, that the intersection
                        // isn't merely a touch.
                        val prev_prev_corner = if (edge_line_no == 0) {
                            p_shape.corner(edge_count - 1)
                        } else {
                            p_shape.corner(edge_line_no - 1)
                        }

                        val next_corner = if (edge_line_no == edge_count - 1) {
                            p_shape.corner(0)
                        } else {
                            p_shape.corner(edge_line_no + 1)
                        }
                        // check, that prev_prev_corner and next_corner
                        // are on different sides of this line segment.
                        val prev_prev_corner_side = this.middle.side_of(prev_prev_corner)
                        val next_corner_side = this.middle.side_of(next_corner)
                        if (prev_prev_corner_side == Side.COLLINEAR || next_corner_side == Side.COLLINEAR || prev_prev_corner_side == next_corner_side) {
                            return empty_result
                        }
                    }
                    if (next_line_side_of_is == Side.COLLINEAR) {
                        // this line segment goes through the next
                        // corner of p_simplex. Check, that the intersection
                        // isn't merely a touch.
                        val prev_corner = p_shape.corner(edge_line_no)
                        val next_next_corner = if (edge_line_no == edge_count - 2) {
                            p_shape.corner(0)
                        } else if (edge_line_no == edge_count - 1) {
                            p_shape.corner(1)
                        } else {
                            p_shape.corner(edge_line_no + 2)
                        }
                        // check, that prev_corner and next_next_corner
                        // are on different sides of this line segment.
                        val prev_corner_side = this.middle.side_of(prev_corner)
                        val next_next_corner_side = this.middle.side_of(next_next_corner)
                        if (prev_corner_side == Side.COLLINEAR || next_next_corner_side == Side.COLLINEAR || prev_corner_side == next_next_corner_side) {
                            return empty_result
                        }
                    }
                    var intersection_already_handled = false
                    for (i in 0 until intersection_count) {
                        if (intersectPt == intersection[i]) {
                            intersection_already_handled = true
                            break
                        }
                    }
                    if (!intersection_already_handled) {
                        if (intersection_count < result.size) {
                            // a new intersection is found
                            result[intersection_count] = edge_line_no
                            intersection[intersection_count] = intersectPt
                            ++intersection_count
                        } else {
                            FRLogger.warn("border_intersections: intersection_count ($intersection_count) is too big!")
                        }
                    }
                }
            }

            prev_line = curr_line
            curr_line = next_line
        }

        if (intersection_count == 0) {
            return empty_result
        }

        if (intersection_count == 2) {
            // assure the correct order
            val is0 = intersection[0]!!.to_float()
            val is1 = intersection[1]!!.to_float()
            val curr_start = line_start.to_float()
            if (curr_start.distance_square(is1) < curr_start.distance_square(is0)) {
                // swap the result points
                val tmp = result[0]
                result[0] = result[1]
                result[1] = tmp
            }

            return result
        }

        if (intersection_count != 1) {
            FRLogger.warn("LineSegment.border_intersections: intersection_count 1 expected")
        }

        val normalised_result = IntArray(1)
        normalised_result[0] = result[0]
        return normalised_result
    }

    /**
     * Inverts the direction of this.middle, if start_point() has a bigger x coordinate than end_point(), or an equal x coordinate and a bigger y coordinate.
     */
    fun sort_endpoints_in_x_y(): LineSegment {
        val swap_endlines = start_point().compare_x_y(end_point()) > 0
        val result: LineSegment

        if (swap_endlines) {
            result = LineSegment(this.end, this.middle, this.start)
            result.precalculated_start_point = this.precalculated_end_point
            result.precalculated_end_point = this.precalculated_start_point
        } else {
            result = this
        }

        return result
    }
}
