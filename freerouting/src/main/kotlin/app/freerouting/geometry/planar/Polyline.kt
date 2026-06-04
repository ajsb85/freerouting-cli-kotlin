package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.util.LinkedList

/**
 * A Polyline is a sequence of lines, where no 2 consecutive lines may be
 * parallel. A Polyline of n lines defines a Polygon of n-1 intersection points
 * of consecutive lines. The lines of the objects of
 * class Polyline are normally defined by points with integer coordinates,
 * whereas the intersections of Lines can be represented in general only by
 * infinite precision rational points. We use polylines
 * with integer coordinates instead of polygons with infinite precision rational
 * coordinates because of its better performance in geometric calculations.
 */
class Polyline : Serializable {

    @JvmField
    val arr: Array<Line>

    @Transient
    private var precalculated_float_corners: Array<FloatPoint?>? = null

    @Transient
    private var precalculated_corners: Array<Point?>? = null

    @Transient
    private var precalculated_bounding_box: IntBox? = null

    /**
     * creates a polyline of length p_polygon.corner_count + 1 from p_polygon, so
     * that the i-th corner of p_polygon will be the intersection of the i-th and
     * the i+1-th lines of the new created
     * p_polyline for 0 <= i < p_point_arr.length. p_polygon must have at least 2 corners
     */
    constructor(p_polygon: Polygon) {
        val point_arr = p_polygon.corner_array()
        if (point_arr.size < 2) {
            FRLogger.warn("Polyline: must contain at least 2 different points")
            this.arr = emptyArray()
            return
        }
        val temp_arr = Array(point_arr.size + 1) { Line(point_arr[0], point_arr[1]) }
        for (i in 1 until point_arr.size) {
            temp_arr[i] = Line(point_arr[i - 1], point_arr[i])
        }
        // construct perpendicular lines at the start and at the end to represent
        // the first and the last point of point_arr as intersection of lines.

        var dir = Direction.get_instance(point_arr[0], point_arr[1])
        temp_arr[0] = Line.get_instance(point_arr[0], dir!!.turn_45_degree(2))

        dir = Direction.get_instance(point_arr[point_arr.size - 1], point_arr[point_arr.size - 2])
        temp_arr[point_arr.size] = Line.get_instance(point_arr[point_arr.size - 1], dir!!.turn_45_degree(2))
        this.arr = temp_arr
    }

    constructor(p_points: Array<Point>) : this(Polygon(p_points))

    /**
     * creates a polyline consisting of 3 lines
     */
    constructor(p_from_corner: Point, p_to_corner: Point) {
        if (p_from_corner == p_to_corner) {
            this.arr = emptyArray()
            return
        }
        val temp_arr = Array(3) { Line(p_from_corner, p_to_corner) }
        val dir = Direction.get_instance(p_from_corner, p_to_corner)
        temp_arr[0] = Line.get_instance(p_from_corner, dir!!.turn_45_degree(2))
        temp_arr[1] = Line(p_from_corner, p_to_corner)
        temp_arr[2] = Line.get_instance(p_to_corner, dir.turn_45_degree(2))
        this.arr = temp_arr
    }

    /**
     * Creates a polyline from an array of lines. Lines, which are parallel to the
     * previous line are skipped. The directed lines are normalized, so that they
     * intersect the previous line before the next line
     */
    constructor(p_line_arr: Array<Line>) {
        var lines = remove_consecutive_parallel_lines(p_line_arr)
        lines = remove_overlaps(lines)
        if (lines.size < 3) {
            this.arr = emptyArray()
            return
        }
        val floatCorners = Array<FloatPoint?>(lines.size - 1) { null }

        // turn evtl the direction of the lines that they point always
        // from the previous corner to the next corner
        for (i in 1 until lines.size - 1) {
            floatCorners[i] = lines[i].intersection_approx(lines[i + 1])
            val side_of_line = lines[i - 1].side_of(floatCorners[i]!!)
            if (side_of_line != Side.COLLINEAR) {
                val d0 = lines[i - 1].direction()
                val d1 = lines[i].direction()
                val side1 = d0.side_of(d1)
                if (side1 != side_of_line) {
                    lines[i] = lines[i].opposite()
                }
            }
        }
        this.precalculated_float_corners = floatCorners
        this.arr = lines
    }

    /**
     * Returns the number of lines minus 1
     */
    fun corner_count(): Int {
        return arr.size - 1
    }

    fun is_empty(): Boolean {
        return arr.size < 3
    }

    /**
     * Checks, if this polyline is empty or if all corner points are equal.
     */
    fun is_point(): Boolean {
        if (arr.size < 3) {
            return true
        }
        val first_corner = this.corner(0) ?: return true
        for (i in 1 until arr.size - 1) {
            if (this.corner(i) != first_corner) {
                return false
            }
        }
        return true
    }

    /**
     * checks, if all lines of this polyline are orthogonal
     */
    fun is_orthogonal(): Boolean {
        for (i in arr.indices) {
            if (!arr[i].is_orthogonal()) {
                return false
            }
        }
        return true
    }

    /**
     * checks, if all lines of this polyline are multiples of 45 degree
     */
    fun is_multiple_of_45_degree(): Boolean {
        for (i in arr.indices) {
            if (!arr[i].is_multiple_of_45_degree()) {
                return false
            }
        }
        return true
    }

    /**
     * returns the intersection of the first line with the second line
     */
    fun first_corner(): Point? {
        return corner(0)
    }

    /**
     * returns the intersection of the last line with the line before the last line
     */
    fun last_corner(): Point? {
        return corner(arr.size - 2)
    }

    /**
     * returns the array of the intersection of two consecutive lines approximated
     * by FloatPoint's.
     */
    fun corner_arr(): Array<Point> {
        if (arr.size < 2) {
            return emptyArray()
        }
        var corners = precalculated_corners
        if (corners == null) {
            corners = Array(arr.size - 1) { null }
            precalculated_corners = corners
        }
        for (i in corners.indices) {
            if (corners[i] == null) {
                corners[i] = arr[i].intersection(arr[i + 1])
            }
        }
        @Suppress("UNCHECKED_CAST")
        return corners as Array<Point>
    }

    /**
     * returns the array of the intersection of two consecutive lines approximated
     * by FloatPoint's.
     */
    fun corner_approx_arr(): Array<FloatPoint> {
        if (arr.size < 2) {
            return emptyArray()
        }
        var floatCorners = precalculated_float_corners
        if (floatCorners == null) {
            floatCorners = Array(arr.size - 1) { null }
            precalculated_float_corners = floatCorners
        }
        for (i in floatCorners.indices) {
            if (floatCorners[i] == null) {
                floatCorners[i] = arr[i].intersection_approx(arr[i + 1])
            }
        }
        @Suppress("UNCHECKED_CAST")
        return floatCorners as Array<FloatPoint>
    }

    /**
     * Returns an approximation of the intersection of the p_no-th with the (p_no -
     * 1)-th line by a FloatPoint.
     */
    fun corner_approx(p_no: Int): FloatPoint {
        val no = when {
            p_no < 0 -> {
                FRLogger.warn("Polyline.corner_approx: p_no is < 0")
                0
            }
            p_no >= arr.size - 1 -> {
                FRLogger.warn("Polyline.corner_approx: p_no must be less than arr.length - 1")
                arr.size - 2
            }
            else -> p_no
        }
        var floatCorners = precalculated_float_corners
        if (floatCorners == null) {
            floatCorners = Array(arr.size - 1) { null }
            precalculated_float_corners = floatCorners
        }
        if (floatCorners[no] == null) {
            floatCorners[no] = arr[no].intersection_approx(arr[no + 1])
        }
        return floatCorners[no]!!
    }

    /**
     * Returns the intersection of the p_no-th with the (p_no - 1)-th edge line.
     */
    fun corner(p_no: Int): Point? {
        if (arr.size < 2) {
            FRLogger.trace("Polyline.corner: arr.length is < 2")
            return null
        }
        val no = when {
            p_no < 0 -> {
                FRLogger.warn("Polyline.corner: p_no is < 0")
                0
            }
            p_no >= arr.size - 1 -> {
                FRLogger.warn("Polyline.corner: p_no must be less than arr.length - 1")
                arr.size - 2
            }
            else -> p_no
        }
        var corners = precalculated_corners
        if (corners == null) {
            corners = Array(arr.size - 1) { null }
            precalculated_corners = corners
        }
        if (corners[no] == null) {
            corners[no] = arr[no].intersection(arr[no + 1])
        }
        return corners[no]
    }

    /**
     * return the polyline with the reversed order of lines
     */
    fun reverse(): Polyline {
        val reversed_lines = Array(arr.size) { i ->
            arr[arr.size - i - 1].opposite()
        }
        return Polyline(reversed_lines)
    }

    /**
     * Calculates the length of this polyline from p_from_corner to p_to_corner.
     */
    fun length_approx(p_from_corner: Int, p_to_corner: Int): Double {
        val from_corner = Math.max(p_from_corner, 0)
        val to_corner = Math.min(p_to_corner, arr.size - 2)
        var result = 0.0
        for (i in from_corner until to_corner) {
            result += this.corner_approx(i + 1).distance(this.corner_approx(i))
        }
        return result
    }

    /**
     * Calculates the cumulative distance between consecutive corners of this
     * polyline.
     */
    fun length_approx(): Double {
        return length_approx(0, arr.size - 2)
    }

    /**
     * calculates for each line a shape around this line where the right and left
     * edge lines have the distance p_half_width from the center line Returns an
     * array of convex shapes of length line_count - 2
     */
    fun offset_shapes(p_half_width: Int): Array<TileShape> {
        return offset_shapes(p_half_width, 0, arr.size - 1)
    }

    /**
     * calculates for each line between p_from_no and p_to_no a shape around this
     * line, where the right and left edge lines have the distance p_half_width from
     * the center line
     */
    fun offset_shapes(p_half_width: Int, p_from_no: Int, p_to_no: Int): Array<TileShape> {
        val from_no = Math.max(p_from_no, 0)
        val to_no = Math.min(p_to_no, arr.size - 1)
        val shape_count = Math.max(to_no - from_no - 1, 0)
        if (shape_count == 0) {
            return emptyArray()
        }
        val shape_arr = Array<TileShape>(shape_count) { TileShape.EMPTY }
        var prev_dir = arr[from_no].direction().get_vector()
        var curr_dir = arr[from_no + 1].direction().get_vector()
        for (i in from_no + 1 until to_no) {
            val next_dir = arr[i + 1].direction().get_vector()

            val lines = Array(4) { Line.ZERO }

            lines[0] = arr[i].translate(-p_half_width.toDouble())
            // current center line translated to the right

            // create the front line of the offset shape
            val next_dir_from_curr_dir = next_dir.side_of(curr_dir)
            // left turn from curr_line to next_line
            if (next_dir_from_curr_dir == Side.ON_THE_LEFT) {
                lines[1] = arr[i + 1].translate(-p_half_width.toDouble())
                // next right line
            } else {
                lines[1] = arr[i + 1].opposite().translate(-p_half_width.toDouble())
                // next left line in opposite direction
            }

            lines[2] = arr[i].opposite().translate(-p_half_width.toDouble())
            // current left line in opposite direction

            // create the back line of the offset shape
            val curr_dir_from_prev_dir = curr_dir.side_of(prev_dir)
            // left turn from prev_line to curr_line
            if (curr_dir_from_prev_dir == Side.ON_THE_LEFT) {
                lines[3] = arr[i - 1].translate(-p_half_width.toDouble())
                // previous line translated to the right
            } else {
                lines[3] = arr[i - 1].opposite().translate(-p_half_width.toDouble())
                // previous left line in opposite direction
            }
            // cut off outstanding corners with following shapes
            var corner_to_check: FloatPoint? = null
            var curr_line = lines[1]
            var check_line: Line
            if (next_dir_from_curr_dir == Side.ON_THE_LEFT) {
                check_line = lines[2]
            } else {
                check_line = lines[0]
            }
            var check_distance_corner = corner_approx(i)
            val check_dist_square = 2.0 * p_half_width * p_half_width
            val cut_dog_ear_lines = LinkedList<Line>()
            var tmp_curr_dir = next_dir
            var direction_changed = false
            for (j in i + 2 until arr.size - 1) {
                if (corner_approx(j - 1).distance_square(check_distance_corner) > check_dist_square) {
                    break
                }
                if (!direction_changed) {
                    corner_to_check = curr_line.intersection_approx(check_line)
                }
                val tmp_next_dir = arr[j].direction().get_vector()
                val next_border_line: Line
                val tmp_next_dir_from_tmp_curr_dir = tmp_next_dir.side_of(tmp_curr_dir)
                direction_changed = tmp_next_dir_from_tmp_curr_dir != next_dir_from_curr_dir
                if (!direction_changed) {
                    if (tmp_next_dir_from_tmp_curr_dir == Side.ON_THE_LEFT) {
                        next_border_line = arr[j].translate(-p_half_width.toDouble())
                    } else {
                        next_border_line = arr[j].opposite().translate(-p_half_width.toDouble())
                    }

                    if (next_border_line.side_of(corner_to_check!!) == Side.ON_THE_LEFT
                        && next_border_line.side_of(this.corner(i)!!) == Side.ON_THE_RIGHT
                        && next_border_line.side_of(this.corner(i - 1)!!) == Side.ON_THE_RIGHT
                    ) {
                        cut_dog_ear_lines.add(next_border_line)
                    }
                    tmp_curr_dir = tmp_next_dir
                    curr_line = next_border_line
                }
            }
            // cut off outstanding corners with previous shapes
            check_distance_corner = corner_approx(i - 1)
            if (curr_dir_from_prev_dir == Side.ON_THE_LEFT) {
                check_line = lines[2]
            } else {
                check_line = lines[0]
            }
            curr_line = lines[3]
            tmp_curr_dir = prev_dir
            direction_changed = false
            for (j in i - 2 downTo 1) {
                if (corner_approx(j).distance_square(check_distance_corner) > check_dist_square) {
                    break
                }
                if (!direction_changed) {
                    corner_to_check = curr_line.intersection_approx(check_line)
                }
                val tmp_prev_dir = arr[j].direction().get_vector()
                val prev_border_line: Line
                val tmp_curr_dir_from_tmp_prev_dir = tmp_curr_dir.side_of(tmp_prev_dir)
                direction_changed = tmp_curr_dir_from_tmp_prev_dir != curr_dir_from_prev_dir
                if (!direction_changed) {
                    if (tmp_curr_dir.side_of(tmp_prev_dir) == Side.ON_THE_LEFT) {
                        prev_border_line = arr[j].translate(-p_half_width.toDouble())
                    } else {
                        prev_border_line = arr[j].opposite().translate(-p_half_width.toDouble())
                    }
                    if (prev_border_line.side_of(corner_to_check!!) == Side.ON_THE_LEFT
                        && prev_border_line.side_of(this.corner(i)!!) == Side.ON_THE_RIGHT
                        && prev_border_line.side_of(this.corner(i - 1)!!) == Side.ON_THE_RIGHT
                    ) {
                        cut_dog_ear_lines.add(prev_border_line)
                    }
                    tmp_curr_dir = tmp_prev_dir
                    curr_line = prev_border_line
                }
            }
            var s1 = TileShape.get_instance(lines)
            val cut_line_count = cut_dog_ear_lines.size
            if (cut_line_count > 0) {
                val cut_lines = Array(cut_line_count) { Line.ZERO }
                val it = cut_dog_ear_lines.iterator()
                for (j in 0 until cut_line_count) {
                    cut_lines[j] = it.next()
                }
                s1 = s1.intersection(TileShape.get_instance(cut_lines))
            }
            val curr_shape_no = i - from_no - 1
            val bounding_shape: TileShape
            if (USE_BOUNDING_OCTAGON_FOR_OFFSET_SHAPES) {
                val surr_oct = bounding_octagon(i - 1, i)
                bounding_shape = surr_oct.offset(p_half_width.toDouble())
            } else {
                val surr_box = bounding_box(i - 1, i)
                val offset_box = surr_box.offset(p_half_width.toDouble())
                bounding_shape = offset_box.to_Simplex()
            }
            shape_arr[curr_shape_no] = bounding_shape.intersection_with_simplify(s1)
            if (shape_arr[curr_shape_no].is_empty()) {
                FRLogger.warn("offset_shapes: shape is empty")
            }

            prev_dir = curr_dir
            curr_dir = next_dir
        }
        return shape_arr
    }

    /**
     * Calculates for the p_no-th line segment a shape around this line where the
     * right and left edge lines have the distance p_half_width from the center
     * line. 0 <= p_no <= arr.length - 3
     */
    fun offset_shape(p_half_width: Int, p_no: Int): TileShape? {
        if (p_no < 0 || p_no > arr.size - 3) {
            FRLogger.warn("Polyline.offset_shape: p_no out of range")
            return null
        }
        val result = offset_shapes(p_half_width, p_no, p_no + 2)
        return result[0]
    }

    /**
     * Calculates for the p_no-th line segment a box shape around this line where
     * the border lines have the distance p_half_width from the center line. 0
     * <= p_no <= arr.length - 3
     */
    fun offset_box(p_half_width: Int, p_no: Int): IntBox {
        val curr_line_segment = LineSegment(this, p_no + 1)
        return curr_line_segment.bounding_box().offset(p_half_width.toDouble())
    }

    /**
     * Returns the by p_vector translated polyline
     */
    fun translate_by(p_vector: Vector): Polyline {
        if (p_vector.equals(Vector.ZERO)) {
            return this
        }
        val new_arr = Array(arr.size) { i ->
            arr[i].translate_by(p_vector)
        }
        return Polyline(new_arr)
    }

    /**
     * Returns the polyline turned by p_factor times 90 degree around p_pole.
     */
    fun turn_90_degree(p_factor: Int, p_pole: IntPoint): Polyline {
        val new_arr = Array(arr.size) { i ->
            arr[i].turn_90_degree(p_factor, p_pole)
        }
        return Polyline(new_arr)
    }

    fun rotate_approx(p_angle: Double, p_pole: FloatPoint): Polyline {
        if (p_angle == 0.0) {
            return this
        }
        val new_corners = Array<Point>(this.corner_count()) { i ->
            this.corner_approx(i).rotate(p_angle, p_pole).round()
        }
        return Polyline(new_corners)
    }

    /**
     * Mirrors this polyline at the vertical line through p_pole
     */
    fun mirror_vertical(p_pole: IntPoint): Polyline {
        val new_arr = Array(arr.size) { i ->
            arr[i].mirror_vertical(p_pole)
        }
        return Polyline(new_arr)
    }

    /**
     * Mirrors this polyline at the horizontal line through p_pole
     */
    fun mirror_horizontal(p_pole: IntPoint): Polyline {
        val new_arr = Array(arr.size) { i ->
            arr[i].mirror_horizontal(p_pole)
        }
        return Polyline(new_arr)
    }

    /**
     * Returns the smallest box containing the intersection points from index
     * p_from_corner_no to index p_to_corner_no of the lines of this polyline
     */
    fun bounding_box(p_from_corner_no: Int, p_to_corner_no: Int): IntBox {
        val from_corner_no = Math.max(p_from_corner_no, 0)
        val to_corner_no = Math.min(p_to_corner_no, arr.size - 2)
        var llx = Double.MAX_VALUE
        var lly = llx
        var urx = -Double.MAX_VALUE
        var ury = urx
        for (i in from_corner_no..to_corner_no) {
            val curr_corner = corner_approx(i)
            llx = Math.min(llx, curr_corner.x)
            lly = Math.min(lly, curr_corner.y)
            urx = Math.max(urx, curr_corner.x)
            ury = Math.max(ury, curr_corner.y)
        }
        val lower_left = IntPoint(Math.floor(llx).toInt(), Math.floor(lly).toInt())
        val upper_right = IntPoint(Math.ceil(urx).toInt(), Math.ceil(ury).toInt())
        return IntBox(lower_left, upper_right)
    }

    /**
     * Returns the smallest box containing the intersection points of the lines of
     * this polyline
     */
    fun bounding_box(): IntBox {
        var precalculated = precalculated_bounding_box
        if (precalculated == null) {
            precalculated = bounding_box(0, corner_count() - 1)
            precalculated_bounding_box = precalculated
        }
        return precalculated
    }

    /**
     * Returns the smallest octagon containing the intersection points from index
     * p_from_corner_no to index p_to_corner_no of the lines of this polyline
     */
    fun bounding_octagon(p_from_corner_no: Int, p_to_corner_no: Int): IntOctagon {
        val from_corner_no = Math.max(p_from_corner_no, 0)
        val to_corner_no = Math.min(p_to_corner_no, arr.size - 2)
        var lx = Double.MAX_VALUE
        var ly = Double.MAX_VALUE
        var rx = -Double.MAX_VALUE
        var uy = -Double.MAX_VALUE
        var ulx = Double.MAX_VALUE
        var lrx = -Double.MAX_VALUE
        var llx = Double.MAX_VALUE
        var urx = -Double.MAX_VALUE
        for (i in from_corner_no..to_corner_no) {
            val curr = corner_approx(i)
            lx = Math.min(lx, curr.x)
            ly = Math.min(ly, curr.y)
            rx = Math.max(rx, curr.x)
            uy = Math.max(uy, curr.y)
            var tmp = curr.x - curr.y
            ulx = Math.min(ulx, tmp)
            lrx = Math.max(lrx, tmp)
            tmp = curr.x + curr.y
            llx = Math.min(llx, tmp)
            urx = Math.max(urx, tmp)
        }
        return IntOctagon(
            Math.floor(lx).toInt(), Math.floor(ly).toInt(), Math.ceil(rx).toInt(),
            Math.ceil(uy).toInt(), Math.floor(ulx).toInt(), Math.ceil(lrx).toInt(),
            Math.floor(llx).toInt(), Math.ceil(urx).toInt()
        )
    }

    /**
     * Calculates an approximation of the nearest point on this polyline to
     * p_from_point.
     */
    fun nearest_point_approx(p_from_point: FloatPoint): FloatPoint {
        var min_distance = Double.MAX_VALUE
        var nearest_point = FloatPoint(0.0, 0.0)
        // calculate the nearest corner point
        val corners = corner_approx_arr()
        for (i in corners.indices) {
            val curr_distance = corners[i].distance(p_from_point)
            if (curr_distance < min_distance) {
                min_distance = curr_distance
                nearest_point = corners[i]
            }
        }
        val c_tolerance = 1.0
        for (i in 1 until arr.size - 1) {
            val projection = p_from_point.projection_approx(arr[i])
            val curr_distance = projection.distance(p_from_point)
            if (curr_distance < min_distance) {
                // look, if the projection is inside the segment
                val segment_length = corners[i].distance(corners[i - 1])
                if (projection.distance(corners[i]) + projection.distance(corners[i - 1]) < segment_length + c_tolerance) {
                    min_distance = curr_distance
                    nearest_point = projection
                }
            }
        }
        return nearest_point
    }

    /**
     * Calculates the distance of p_from_point to the nearest point on this polyline
     */
    fun distance(p_from_point: FloatPoint): Double {
        return p_from_point.distance(nearest_point_approx(p_from_point))
    }

    /**
     * Combines the two polylines, if they have a common end corner. The order of
     * lines in this polyline will be preserved. Returns the combined polyline or
     * this polyline, if this polyline and p_other
     * have no common end corner. If there is something to combine at the start of
     * this polyline, p_other is inserted in front of this polyline. If there is
     * something to combine at the end of this
     * polyline, this polyline is inserted in front of p_other.
     */
    fun combine(p_other: Polyline?): Polyline {
        if (p_other == null || arr.size < 3 || p_other.arr.size < 3) {
            return this
        }
        val combine_at_start: Boolean
        val combine_other_at_start: Boolean
        if (first_corner() == p_other.first_corner()) {
            combine_at_start = true
            combine_other_at_start = true
        } else if (first_corner() == p_other.last_corner()) {
            combine_at_start = true
            combine_other_at_start = false
        } else if (last_corner() == p_other.first_corner()) {
            combine_at_start = false
            combine_other_at_start = true
        } else if (last_corner() == p_other.last_corner()) {
            combine_at_start = false
            combine_other_at_start = false
        } else {
            return this // no common endpoint
        }
        val line_arr = Array(arr.size + p_other.arr.size - 2) { Line.ZERO }
        if (combine_at_start) {
            // insert the lines of p_other in front
            if (combine_other_at_start) {
                // insert in reverse order, skip the first line of p_other
                for (i in 0 until p_other.arr.size - 1) {
                    line_arr[i] = p_other.arr[p_other.arr.size - i - 1].opposite()
                }
            } else {
                // skip the last line of p_other
                System.arraycopy(p_other.arr, 0, line_arr, 0, p_other.arr.size - 1)
            }
            // append the lines of this polyline, skip the first line
            System.arraycopy(arr, 1, line_arr, p_other.arr.size - 1, arr.size - 1)
        } else {
            // insert the lines of this polyline in front, skip the last line
            System.arraycopy(arr, 0, line_arr, 0, arr.size - 1)
            if (combine_other_at_start) {
                // skip the first line of p_other
                System.arraycopy(p_other.arr, 1, line_arr, arr.size - 1, p_other.arr.size - 1)
            } else {
                // insert in reverse order, skip the last line of p_other
                for (i in 1 until p_other.arr.size) {
                    line_arr[arr.size + i - 2] = p_other.arr[p_other.arr.size - i - 1].opposite()
                }
            }
        }
        return Polyline(line_arr)
    }

    /**
     * Splits this polyline at the line with number p_line_no into two by inserting
     * p_endline as concluding line of the first split piece and as the start line
     * of the second split piece. p_endline and
     * the line with number p_line_no must not be parallel. The order of the lines
     * ins the two result pieces is preserved. p_line_no must be bigger than 0 and
     * less than arr.length - 1. Returns null, if
     * nothing was split.
     */
    fun split(p_line_no: Int, p_end_line: Line): Array<Polyline>? {
        if (p_line_no < 1 || p_line_no > arr.size - 2) {
            FRLogger.warn("Polyline.split: p_line_no out of range")
            return null
        }
        if (this.arr[p_line_no].is_parallel(p_end_line)) {
            return null
        }
        val new_end_corner = this.arr[p_line_no].intersection(p_end_line)
        FRLogger.trace(
            "Polyline.split",
            "compare_trace_split_called",
            "p_line_no="
                + p_line_no
                + ", arr.length="
                + arr.size
                + ", arr.length-2="
                + (arr.size - 2)
                + ", new_end_corner="
                + debug_point(new_end_corner)
                + " (type="
                + new_end_corner.javaClass.simpleName
                + ")"
                + ", last_corner="
                + debug_point(this.last_corner())
                + " (type="
                + (this.last_corner()?.javaClass?.simpleName ?: "null")
                + ")"
                + ", equals="
                + (new_end_corner == this.last_corner()),
            "Polyline split p_line_no=" + p_line_no,
            arrayOf(this.first_corner()!!, new_end_corner, this.last_corner()!!)
        )
        val sb = StringBuilder("    CORNERS:")
        for (i in 0 until this.corner_count()) {
            sb.append(" ").append(this.corner_approx(i))
        }
        FRLogger.trace(
            "Polyline.split",
            "compare_trace_split_corners",
            sb.toString(),
            "Polyline split p_line_no=" + p_line_no,
            arrayOf(this.first_corner()!!, new_end_corner, this.last_corner()!!)
        )
        if (p_line_no == 1 && new_end_corner == this.first_corner()
            || p_line_no >= arr.size - 2 && new_end_corner == this.last_corner()
        ) {
            // No split, if p_end_line does not intersect, but touches
            // only this Polyline at an end point.
            return null
        }
        val first_piece: Array<Line>
        if (this.corner(p_line_no - 1) == new_end_corner) {
            // skip line segment of length 0 at the end of the first piece
            first_piece = Array(p_line_no + 1) { Line.ZERO }
            System.arraycopy(arr, 0, first_piece, 0, first_piece.size)

        } else {
            first_piece = Array(p_line_no + 2) { Line.ZERO }
            System.arraycopy(arr, 0, first_piece, 0, p_line_no + 1)
            first_piece[p_line_no + 1] = p_end_line
        }
        val second_piece: Array<Line>
        if (this.corner(p_line_no) == new_end_corner) {
            // skip line segment of length 0 at the beginning of the second piece
            second_piece = Array(arr.size - p_line_no) { Line.ZERO }
            System.arraycopy(this.arr, p_line_no, second_piece, 0, second_piece.size)

        } else {
            second_piece = Array(arr.size - p_line_no + 1) { Line.ZERO }
            second_piece[0] = p_end_line
            System.arraycopy(this.arr, p_line_no, second_piece, 1, second_piece.size - 1)
        }
        val result = Array(2) { Polyline(emptyArray<Line>()) }
        result[0] = Polyline(first_piece)
        result[1] = Polyline(second_piece)
        if (result[0].is_point() || result[1].is_point()) {
            return null
        }
        return result
    }

    /**
     * create a new Polyline by skipping the lines of this Polyline from p_from_no
     * to p_to_no
     */
    fun skip_lines(p_from_no: Int, p_to_no: Int): Polyline {
        if (p_from_no < 0 || p_to_no > arr.size - 1 || p_from_no > p_to_no) {
            return this
        }
        val new_lines = Array(arr.size - (p_to_no - p_from_no + 1)) { Line.ZERO }
        System.arraycopy(arr, 0, new_lines, 0, p_from_no)
        System.arraycopy(arr, p_to_no + 1, new_lines, p_from_no, new_lines.size - p_from_no)
        return Polyline(new_lines)
    }

    fun contains(p_point: Point): Boolean {
        for (i in 1 until arr.size - 1) {
            val curr_segment = LineSegment(this, i)
            if (curr_segment.contains(p_point)) {
                return true
            }
        }
        return false
    }

    /**
     * Creates a perpendicular line segment from p_from_point onto the nearest line
     * segment of this polyline to p_from_side. Returns null, if the perpendicular
     * line does not intersect the nearest line
     * segment inside its segment bounds or if p_from_point is contained in this
     * polyline.
     */
    fun projection_line(p_from_point: Point?): LineSegment? {
        if (p_from_point == null) {
            FRLogger.warn("Polyline.projection_line: p_from_point is null; returning null. This indicates a degenerate routing connection was attempted with an uninitialized endpoint.")
            return null
        }
        val from_point = p_from_point.to_float()
        var min_distance = Double.MAX_VALUE
        var result_line: Line? = null
        var nearest_line: Line? = null
        for (i in 1 until arr.size - 1) {
            val projection = from_point.projection_approx(arr[i])
            val curr_distance = projection.distance(from_point)
            if (curr_distance < min_distance) {
                val direction_towards_line = this.arr[i].perpendicular_direction(p_from_point) ?: continue
                val curr_result_line = Line(p_from_point, direction_towards_line)
                val prev_corner = this.corner(i - 1) ?: continue
                val next_corner = this.corner(i) ?: continue
                val prev_corner_side = curr_result_line.side_of(prev_corner)
                val next_corner_side = curr_result_line.side_of(next_corner)
                if (prev_corner_side == next_corner_side && prev_corner_side != Side.COLLINEAR) {
                    // the projection point is outside the line segment
                    continue
                }
                nearest_line = this.arr[i]
                min_distance = curr_distance
                result_line = curr_result_line
            }
        }
        if (nearest_line == null || result_line == null) {
            return null
        }
        val start_line = Line(p_from_point, nearest_line.direction())
        return LineSegment(start_line, result_line, nearest_line)
    }

    /**
     * Shortens this polyline to p_new_line_count lines. Additionally, the last line
     * segment will be approximately shortened to p_new_length. The last corner of
     * the new polyline will be an IntPoint.
     */
    fun shorten(p_new_line_count: Int, p_last_segment_length: Double): Polyline {
        val last_corner = this.corner_approx(p_new_line_count - 2)
        val prev_last_corner = this.corner_approx(p_new_line_count - 3)
        val new_last_corner = prev_last_corner.change_length(last_corner, p_last_segment_length).round()
        if (new_last_corner == this.corner(this.corner_count() - 2)) {
            // skip the last line
            return skip_lines(p_new_line_count - 1, p_new_line_count - 1)
        }
        val new_lines = Array(p_new_line_count) { Line.ZERO }
        System.arraycopy(arr, 0, new_lines, 0, p_new_line_count - 2)
        // create the last 2 lines of the new polyline
        var first_line_point = arr[p_new_line_count - 2].a
        if (first_line_point == new_last_corner) {
            first_line_point = arr[p_new_line_count - 2].b
        }
        val new_prev_last_line = Line(first_line_point, new_last_corner)
        new_lines[p_new_line_count - 2] = new_prev_last_line
        new_lines[p_new_line_count - 1] = Line.get_instance(
            new_last_corner,
            new_prev_last_line.direction().turn_45_degree(6)
        )
        return Polyline(new_lines)
    }

    companion object {
        private const val USE_BOUNDING_OCTAGON_FOR_OFFSET_SHAPES = true

        @JvmStatic
        private fun remove_consecutive_parallel_lines(p_line_arr: Array<Line>): Array<Line> {
            if (p_line_arr.size < 3) {
                // polyline must have at least 3 lines
                return p_line_arr
            }
            val tmp_arr = Array(p_line_arr.size) { p_line_arr[0] }
            var new_length = 0
            tmp_arr[0] = p_line_arr[0]
            for (i in 1 until p_line_arr.size) {
                // skip multiple lines
                if (!tmp_arr[new_length].is_parallel(p_line_arr[i])) {
                    ++new_length
                    tmp_arr[new_length] = p_line_arr[i]
                }
            }
            ++new_length
            if (new_length == p_line_arr.size) {
                // nothing skipped
                return p_line_arr
            }
            // at least 1 line is skipped, adjust the array
            if (new_length < 3) {
                return emptyArray()
            }
            val result = Array(new_length) { p_line_arr[0] }
            System.arraycopy(tmp_arr, 0, result, 0, new_length)
            return result
        }

        /**
         * checks if previous and next line are equal or opposite and removes the
         * resulting overlap
         */
        @JvmStatic
        private fun remove_overlaps(p_line_arr: Array<Line>): Array<Line> {
            if (p_line_arr.size < 4) {
                return p_line_arr
            }
            var new_length = 0
            val tmp_arr = Array(p_line_arr.size) { p_line_arr[0] }
            tmp_arr[0] = p_line_arr[0]
            if (!p_line_arr[0].is_equal_or_opposite(p_line_arr[2])) {
                ++new_length
            }
            // else skip the first line
            tmp_arr[new_length] = p_line_arr[1]
            ++new_length
            for (i in 2 until p_line_arr.size - 2) {
                if (tmp_arr[new_length - 1].is_equal_or_opposite(p_line_arr[i + 1])) {
                    // skip 2 lines
                    --new_length
                } else {
                    tmp_arr[new_length] = p_line_arr[i]
                    ++new_length
                }
            }
            tmp_arr[new_length] = p_line_arr[p_line_arr.size - 2]
            ++new_length
            // Guard: new_length must be >= 2 before accessing tmp_arr[new_length - 2].
            // If the loop decremented new_length all the way to 0 the index would be -1.
            if (new_length >= 2 && !p_line_arr[p_line_arr.size - 1].is_equal_or_opposite(tmp_arr[new_length - 2])) {
                tmp_arr[new_length] = p_line_arr[p_line_arr.size - 1]
                ++new_length
            }
            // else skip the last line
            if (new_length == p_line_arr.size) {
                // nothing skipped
                return p_line_arr
            }
            // at least 1 line is skipped, adjust the array
            if (new_length < 3) {
                return emptyArray()
            }
            val result = Array(new_length) { p_line_arr[0] }
            System.arraycopy(tmp_arr, 0, result, 0, new_length)
            return result
        }

        @JvmStatic
        private fun debug_point(p_point: Point?): String {
            if (p_point == null) return "null"
            if (p_point is IntPoint) {
                return "(${p_point.x},${p_point.y})"
            }
            return p_point.toString()
        }
    }
}
