package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.util.Arrays
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.abs

/**
 * Convex shape defined as intersection of half-planes. A half-plane is defined as the positive side of a directed line.
 */
class Simplex : TileShape, Serializable {

    @JvmField
    val arr: Array<Line>

    @Transient
    private var precalculated_corners: Array<Point?>? = null

    @Transient
    private var precalculated_float_corners: Array<FloatPoint?>? = null

    @Transient
    private var precalculated_bounding_box: IntBox? = null

    @Transient
    private var precalculated_bounding_octagon: IntOctagon? = null

    /**
     * Constructs a Simplex from the directed lines in p_line_arr. The simplex will not be normalized. To get a normalized simplex use TileShape.get_instance
     */
    constructor(p_line_arr: Array<Line>) {
        arr = p_line_arr
    }

    /**
     * Return true, if this simplex is empty
     */
    override fun is_empty(): Boolean {
        return arr.isEmpty()
    }

    /**
     * Converts the physical instance of this shape to a simpler physical instance, if possible. (For example a Simplex to an IntOctagon).
     */
    override fun simplify(): TileShape {
        var result: TileShape = this
        if (this.is_empty()) {
            result = EMPTY
        } else if (this.is_IntBox()) {
            result = this.bounding_box()
        } else if (this.is_IntOctagon()) {
            val oct = this.to_IntOctagon()
            if (oct != null) {
                result = oct
            }
        }
        return result
    }

    override fun get_id_no(): Int {
        var result = 0
        for (curr in arr) {
            result = 31 * result + curr.get_id_no()
        }
        return result
    }

    /**
     * Returns true, if the determinant of the direction of index p_no -1 and the direction of index p_no is {@literal >} 0
     */
    override fun corner_is_bounded(p_no: Int): Boolean {
        val no = when {
            p_no < 0 -> {
                FRLogger.warn("corner: p_no is < 0")
                0
            }
            p_no >= arr.size -> {
                FRLogger.warn("corner: p_index must be less than arr.length - 1")
                arr.size - 1
            }
            else -> p_no
        }
        if (arr.size == 1) {
            return false
        }
        val prev_no = if (no == 0) arr.size - 1 else no - 1
        val prev_dir = arr[prev_no].direction().get_vector() as IntVector
        val curr_dir = arr[no].direction().get_vector() as IntVector
        return prev_dir.determinant(curr_dir) > 0
    }

    /**
     * Returns true, if the shape of this simplex is contained in a sufficiently large box
     */
    override fun is_bounded(): Boolean {
        if (arr.isEmpty()) {
            return true
        }
        if (arr.size < 3) {
            return false
        }
        for (i in arr.indices) {
            if (!corner_is_bounded(i)) {
                return false
            }
        }
        return true
    }

    /**
     * Returns the number of edge lines defining this simplex
     */
    override fun border_line_count(): Int {
        return arr.size
    }

    /**
     * Returns the intersection of the p_no -1-th with the p_no-th line of this simplex. If the simplex is not bounded at this corner, the coordinates of the result will be set to Integer.MAX_VALUE.
     */
    override fun corner(p_no: Int): Point {
        val no = when {
            p_no < 0 -> {
                FRLogger.warn("Simplex.corner: p_no is < 0")
                0
            }
            p_no >= arr.size -> {
                FRLogger.warn("Simplex.corner: p_no must be less than arr.length - 1")
                arr.size - 1
            }
            else -> p_no
        }
        if (precalculated_corners == null) {
            precalculated_corners = arrayOfNulls<Point>(arr.size)
        }
        val corners = precalculated_corners!!
        if (corners[no] == null) {
            val prev = if (no == 0) arr[arr.size - 1] else arr[no - 1]
            corners[no] = arr[no].intersection(prev)
        }
        return corners[no]!!
    }

    /**
     * Returns an approximation of the intersection of the p_no -1-th with the p_no-th line of this simplex by a FloatPoint. If the simplex is not bounded at this corner, the coordinates of the result
     * will be set to Integer.MAX_VALUE.
     */
    override fun corner_approx(p_no: Int): FloatPoint? {
        if (arr.isEmpty()) {
            return null
        }
        val no = when {
            p_no < 0 -> {
                FRLogger.warn("Simplex.corner_approx: p_no is < 0")
                0
            }
            p_no >= arr.size -> {
                FRLogger.warn("Simplex.corner_approx: p_no must be less than arr.length - 1")
                arr.size - 1
            }
            else -> p_no
        }
        if (precalculated_float_corners == null) {
            precalculated_float_corners = arrayOfNulls<FloatPoint>(arr.size)
        }
        val floatCorners = precalculated_float_corners!!
        if (floatCorners[no] == null) {
            val prev = if (no == 0) arr[arr.size - 1] else arr[no - 1]
            floatCorners[no] = arr[no].intersection_approx(prev)
        }
        return floatCorners[no]
    }

    override fun corner_approx_arr(): Array<FloatPoint> {
        if (precalculated_float_corners == null) {
            precalculated_float_corners = arrayOfNulls<FloatPoint>(arr.size)
        }
        val floatCorners = precalculated_float_corners!!
        for (i in floatCorners.indices) {
            if (floatCorners[i] == null) {
                val prev = if (i == 0) arr[arr.size - 1] else arr[i - 1]
                floatCorners[i] = arr[i].intersection_approx(prev)
            }
        }
        @Suppress("UNCHECKED_CAST")
        return floatCorners as Array<FloatPoint>
    }

    /**
     * returns the p_no-th edge line of this simplex. The edge lines are sorted in ascending direction.
     */
    override fun border_line(p_no: Int): Line? {
        if (arr.isEmpty()) {
            FRLogger.warn("Simplex.edge_line : simplex is empty")
            return null
        }
        val no = when {
            p_no < 0 -> {
                FRLogger.warn("Simplex.edge_line : p_no is < 0")
                0
            }
            p_no >= arr.size -> {
                FRLogger.warn("Simplex.edge_line: p_no must be less than arr.length - 1")
                arr.size - 1
            }
            else -> p_no
        }
        return arr[no]
    }

    /**
     * Returns the dimension of this simplex. The result may be 2, 1, 0, or -1 (if the simplex is empty).
     */
    override fun dimension(): Int {
        if (arr.isEmpty()) {
            return -1
        }
        if (arr.size > 4) {
            return 2
        }
        if (arr.size == 1) {
            return 2
        }
        if (arr.size == 2) {
            if (arr[0].overlaps(arr[1])) {
                return 1
            }
            return 2
        }
        if (arr.size == 3) {
            if (arr[0].overlaps(arr[1]) || arr[0].overlaps(arr[2]) || arr[1].overlaps(arr[2])) {
                return 1
            }
            val intersection = arr[1].intersection(arr[2])
            val side_of_line0 = arr[0].side_of(intersection)
            if (side_of_line0 == Side.ON_THE_RIGHT) {
                return 2
            }
            if (side_of_line0 == Side.ON_THE_LEFT) {
                FRLogger.debug("empty Simplex not normalized")
                return -1
            }
            return 0
        }
        val collinear_0_2 = arr[0].overlaps(arr[2])
        val collinear_1_3 = arr[1].overlaps(arr[3])
        if (collinear_0_2 && collinear_1_3) {
            return 0
        }
        if (collinear_0_2 || collinear_1_3) {
            return 1
        }
        return 2
    }

    override fun max_width(): Double {
        if (!this.is_bounded()) {
            return Int.MAX_VALUE.toDouble()
        }
        var max_distance = Int.MIN_VALUE.toDouble()
        var max_distance_2 = Int.MIN_VALUE.toDouble()
        val gravity_point = this.centre_of_gravity()

        for (i in 0 until border_line_count()) {
            val curr_distance = abs(arr[i].signed_distance(gravity_point))

            if (curr_distance > max_distance) {
                max_distance_2 = max_distance
                max_distance = curr_distance
            } else if (curr_distance > max_distance_2) {
                max_distance_2 = curr_distance
            }
        }
        return max_distance + max_distance_2
    }

    override fun min_width(): Double {
        if (!this.is_bounded()) {
            return Int.MAX_VALUE.toDouble()
        }
        var min_distance = Int.MAX_VALUE.toDouble()
        var min_distance_2 = Int.MAX_VALUE.toDouble()
        val gravity_point = this.centre_of_gravity()

        for (i in 0 until border_line_count()) {
            val curr_distance = abs(arr[i].signed_distance(gravity_point))

            if (curr_distance < min_distance) {
                min_distance_2 = min_distance
                min_distance = curr_distance
            } else if (curr_distance < min_distance_2) {
                min_distance_2 = curr_distance
            }
        }
        return min_distance + min_distance_2
    }

    /**
     * checks if this simplex can be converted into an IntBox
     */
    override fun is_IntBox(): Boolean {
        for (i in arr.indices) {
            val curr_line = arr[i]
            if (curr_line.a !is IntPoint || curr_line.b !is IntPoint) {
                return false
            }
            if (!curr_line.is_orthogonal()) {
                return false
            }
            if (!corner_is_bounded(i)) {
                return false
            }
        }
        return true
    }

    /**
     * checks if this simplex can be converted into an IntOctagon
     */
    override fun is_IntOctagon(): Boolean {
        for (i in arr.indices) {
            val curr_line = arr[i]
            if (curr_line.a !is IntPoint || curr_line.b !is IntPoint) {
                return false
            }
            if (!curr_line.is_multiple_of_45_degree()) {
                return false
            }
            if (!corner_is_bounded(i)) {
                return false
            }
        }
        return true
    }

    /**
     * Converts this IntSimplex to an IntOctagon. Returns null, if that is not possible, because not all lines of this IntSimplex are 45 degree
     */
    fun to_IntOctagon(): IntOctagon? {
        if (!is_IntOctagon()) {
            return null
        }
        if (is_empty()) {
            return IntOctagon.EMPTY
        }

        var rx = Limits.CRIT_INT
        var uy = Limits.CRIT_INT
        var lrx = Limits.CRIT_INT
        var urx = Limits.CRIT_INT
        var lx = -Limits.CRIT_INT
        var ly = -Limits.CRIT_INT
        var llx = -Limits.CRIT_INT
        var ulx = -Limits.CRIT_INT
        for (i in arr.indices) {
            val curr_line = arr[i]
            val a = curr_line.a as IntPoint
            val b = curr_line.b as IntPoint
            if (a.y == b.y) {
                if (b.x >= a.x) {
                    ly = a.y
                }
                if (b.x <= a.x) {
                    uy = a.y
                }
            }
            if (a.x == b.x) {
                if (b.y >= a.y) {
                    rx = a.x
                }
                if (b.y <= a.y) {
                    lx = a.x
                }
            }
            if (a.y < b.y) {
                if (a.x < b.x) {
                    lrx = a.x - a.y
                } else if (a.x > b.x) {
                    urx = a.x + a.y
                }
            } else if (a.y > b.y) {
                if (a.x < b.x) {
                    llx = a.x + a.y
                } else if (a.x > b.x) {
                    ulx = a.x - a.y
                }
            }
        }
        val result = IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx)
        return result.normalize()
    }

    /**
     * Returns the simplex, which results from translating the lines of this simplex by p_vector
     */
    override fun translate_by(p_vector: Vector): Simplex {
        if (p_vector == Vector.ZERO) {
            return this
        }
        val new_arr = Array(arr.size) { i -> arr[i].translate_by(p_vector) }
        return Simplex(new_arr)
    }

    /**
     * Returns the smallest box with int coordinates containing all corners of this simplex. The coordinates of the result will be Integer.MAX_VALUE, if the simplex is not bounded
     */
    override fun bounding_box(): IntBox {
        if (arr.isEmpty()) {
            return IntBox.EMPTY
        }
        if (precalculated_bounding_box == null) {
            var llx = Integer.MAX_VALUE.toDouble()
            var lly = Integer.MAX_VALUE.toDouble()
            var urx = Integer.MIN_VALUE.toDouble()
            var ury = Integer.MIN_VALUE.toDouble()
            for (i in arr.indices) {
                val curr = corner_approx(i)!!
                llx = min(llx, curr.x)
                lly = min(lly, curr.y)
                urx = max(urx, curr.x)
                ury = max(ury, curr.y)
            }
            val lower_left = IntPoint(floor(llx).toInt(), floor(lly).toInt())
            val upper_right = IntPoint(ceil(urx).toInt(), ceil(ury).toInt())
            precalculated_bounding_box = IntBox(lower_left, upper_right)
        }
        return precalculated_bounding_box!!
    }

    /**
     * Calculates a bounding octagon of the Simplex. Returns null, if the Simplex is not bounded.
     */
    override fun bounding_octagon(): IntOctagon? {
        if (precalculated_bounding_octagon == null) {
            var lx = Integer.MAX_VALUE.toDouble()
            var ly = Integer.MAX_VALUE.toDouble()
            var rx = Integer.MIN_VALUE.toDouble()
            var uy = Integer.MIN_VALUE.toDouble()
            var ulx = Integer.MAX_VALUE.toDouble()
            var lrx = Integer.MIN_VALUE.toDouble()
            var llx = Integer.MAX_VALUE.toDouble()
            var urx = Integer.MIN_VALUE.toDouble()
            for (i in arr.indices) {
                val curr = corner_approx(i)!!
                lx = min(lx, curr.x)
                ly = min(ly, curr.y)
                rx = max(rx, curr.x)
                uy = max(uy, curr.y)

                val tmp = curr.x - curr.y
                ulx = min(ulx, tmp)
                lrx = max(lrx, tmp)

                val tmp2 = curr.x + curr.y
                llx = min(llx, tmp2)
                urx = max(urx, tmp2)
            }
            if (min(lx, ly) < -Limits.CRIT_INT || max(rx, uy) > Limits.CRIT_INT || min(ulx, llx) < -Limits.CRIT_INT || max(lrx, urx) > Limits.CRIT_INT) {
                return null
            }
            precalculated_bounding_octagon = IntOctagon(
                floor(lx).toInt(), floor(ly).toInt(), ceil(rx).toInt(), ceil(uy).toInt(),
                floor(ulx).toInt(), ceil(lrx).toInt(), floor(llx).toInt(), ceil(urx).toInt()
            )
        }
        return precalculated_bounding_octagon
    }

    override fun bounding_tile(): Simplex {
        return this
    }

    override fun bounding_shape(p_dirs: ShapeBoundingDirections): RegularTileShape {
        return p_dirs.bounds(this)
    }

    /**
     * Returns the simplex offseted by p_with. If p_width {@literal >} 0, the offset is to the outer, else to the inner.
     */
    override fun offset(p_width: Double): Simplex {
        if (p_width == 0.0) {
            return this
        }
        val new_arr = Array(arr.size) { i -> arr[i].translate(-p_width) }
        var offset_simplex = Simplex(new_arr)
        if (p_width < 0.0) {
            offset_simplex = offset_simplex.remove_redundant_lines()
        }
        return offset_simplex
    }

    /**
     * Returns this simplex enlarged by p_offset. The result simplex is intersected with the by p_offset enlarged bounding octagon of this simplex
     */
    override fun enlarge(p_offset: Double): Simplex {
        if (p_offset == 0.0) {
            return this
        }
        val offset_simplex = offset(p_offset)
        val bounding_oct = this.bounding_octagon() ?: return EMPTY
        val offset_oct = bounding_oct.offset(p_offset)
        return offset_simplex.intersection(offset_oct.to_Simplex())
    }

    /**
     * Returns the number of the rightmost corner seen from p_from_point No other point of this simplex may be to the right of the line from p_from_point to the result corner.
     */
    fun index_of_right_most_corner(p_from_point: Point): Int {
        val pole = p_from_point
        var right_most_corner = corner(0)
        var result = 0
        for (i in 1 until arr.size) {
            val curr_corner = corner(i)
            if (curr_corner.side_of(pole, right_most_corner) == Side.ON_THE_RIGHT) {
                right_most_corner = curr_corner
                result = i
            }
        }
        return result
    }

    /**
     * Returns the intersection of p_box with this simplex
     */
    public override fun intersection(p_box: IntBox): Simplex {
        return intersection(p_box.to_Simplex())
    }

    /**
     * Returns the intersection of this simplex and p_other
     */
    public override fun intersection(p_other: Simplex): Simplex {
        if (this.is_empty() || p_other.is_empty()) {
            return EMPTY
        }
        val new_arr = Array(arr.size + p_other.arr.size) { i ->
            if (i < arr.size) arr[i] else p_other.arr[i - arr.size]
        }
        new_arr.sort()
        val result = Simplex(new_arr)
        return result.remove_redundant_lines()
    }

    /**
     * Returns the intersection of this simplex and the shape p_other
     */
    public override fun intersection(p_other: TileShape): TileShape {
        return p_other.intersection(this)
    }

    override fun intersects(p_other: Shape): Boolean {
        return p_other.intersects(this)
    }

    override fun intersects(p_other: Simplex): Boolean {
        val isect = intersection(p_other)
        return !isect.is_empty()
    }

    /**
     * if p_line is a borderline of this simplex the number of that edge is returned, otherwise -1
     */
    override fun border_line_index(p_line: Line): Int {
        for (i in arr.indices) {
            if (p_line == arr[i]) {
                return i
            }
        }
        return -1
    }

    /**
     * Enlarges the simplex by removing the edge line with index p_no. The result simplex may get unbounded.
     */
    fun remove_border_line(p_no: Int): Simplex {
        if (p_no < 0 || p_no >= arr.size) {
            return this
        }
        val new_arr = Array(arr.size - 1) { i ->
            if (i < p_no) arr[i] else arr[i + 1]
        }
        return Simplex(new_arr)
    }

    override fun to_Simplex(): Simplex {
        return this
    }

    public override fun intersection(p_other: IntOctagon): Simplex {
        return intersection(p_other.to_Simplex())
    }

    override fun cutout(p_shape: TileShape): Array<TileShape> {
        return p_shape.cutout_from(this)
    }

    /**
     * cuts this simplex out of p_outer_simplex. Divides the resulting shape into simplices along the minimal distance lines from the vertices of the inner simplex to the outer simplex; Returns the
     * convex pieces constructed by this division.
     */
    public override fun cutout_from(p_outer_simplex: Simplex): Array<Simplex> {
        if (this.dimension() < 2) {
            FRLogger.warn("Simplex.cutout_from only implemented for 2-dim simplex")
            return emptyArray()
        }
        val inner_simplex = this.intersection(p_outer_simplex)
        if (inner_simplex.dimension() < 2) {
            // nothing to cutout from p_outer_simplex
            return arrayOf(p_outer_simplex)
        }
        val inner_corner_count = inner_simplex.arr.size
        val division_line_arr = arrayOfNulls<Array<Line>>(inner_corner_count)
        for (inner_corner_no in 0 until inner_corner_count) {
            division_line_arr[inner_corner_no] = inner_simplex.calc_division_lines(inner_corner_no, p_outer_simplex)
            if (division_line_arr[inner_corner_no] == null) {
                FRLogger.warn("Simplex.cutout_from: division line is null")
                return arrayOf(p_outer_simplex)
            }
        }
        var check_cross_first_line = false
        var prev_division_line: Line? = null
        val first_division_line = division_line_arr[0]!![0]
        val first_direction = first_division_line.direction() as IntDirection
        val result_list = LinkedList<Simplex>()

        for (inner_corner_no in 0 until inner_corner_count) {
            val next_division_line = if (inner_corner_no == inner_simplex.arr.size - 1) {
                division_line_arr[0]!![0]
            } else {
                division_line_arr[inner_corner_no + 1]!![0]
            }
            val curr_division_lines = division_line_arr[inner_corner_no]!!
            if (curr_division_lines.size == 2) {
                // 2 division lines are necessary (sharp corner).
                // Construct an unbounded simplex from
                // curr_division_lines[1] and curr_division_lines[0]
                // and intersect it with the outer simplex
                val curr_dir = curr_division_lines[0].direction() as IntDirection
                var merge_prev_division_line = false
                var merge_first_division_line = false
                if (prev_division_line != null) {
                    val prev_dir = prev_division_line.direction() as IntDirection
                    if (curr_dir.determinant(prev_dir) > 0) {
                        // the previous division line may intersect
                        //  curr_division_lines[0] inside p_divide_simplex
                        merge_prev_division_line = true
                    }
                }
                if (!check_cross_first_line) {
                    check_cross_first_line = inner_corner_no > 0 && curr_dir.determinant(first_direction) > 0
                }
                if (check_cross_first_line) {
                    val curr_dir2 = curr_division_lines[1].direction() as IntDirection
                    if (curr_dir2.determinant(first_direction) < 0) {
                        // The current piece has an intersection area with the first
                        // piece.
                        // Add a line to tmp_polyline to prevent this
                        merge_first_division_line = true
                    }
                }
                var piece_line_count = 2
                if (merge_prev_division_line) {
                    ++piece_line_count
                }
                if (merge_first_division_line) {
                    ++piece_line_count
                }
                val piece_lines = arrayOfNulls<Line>(piece_line_count)
                piece_lines[0] = Line(curr_division_lines[1].b, curr_division_lines[1].a)
                piece_lines[1] = curr_division_lines[0]
                var curr_line_no = 1
                if (merge_prev_division_line) {
                    ++curr_line_no
                    piece_lines[curr_line_no] = prev_division_line
                }
                if (merge_first_division_line) {
                    ++curr_line_no
                    piece_lines[curr_line_no] = Line(first_division_line.b, first_division_line.a)
                }
                @Suppress("UNCHECKED_CAST")
                val curr_piece = Simplex(piece_lines as Array<Line>)
                result_list.add(curr_piece.intersection(p_outer_simplex))
            }
            // construct an unbounded simplex from next_division_line,
            // inner_simplex.line [inner_corner_no] and the last current division line
            // and intersect it with the outer simplex
            val merge_next_division_line = !next_division_line.b.equals(next_division_line.a)
            val last_curr_division_line = curr_division_lines[curr_division_lines.size - 1]
            val last_curr_dir = last_curr_division_line.direction() as IntDirection
            val merge_last_curr_division_line = !last_curr_division_line.b.equals(last_curr_division_line.a)
            var merge_prev_division_line = false
            var merge_first_division_line = false
            if (prev_division_line != null) {
                val prev_dir = prev_division_line.direction() as IntDirection
                if (last_curr_dir.determinant(prev_dir) > 0) {
                    // the previous division line may intersect
                    //  the last current division line inside p_divide_simplex
                    merge_prev_division_line = true
                }
            }
            if (!check_cross_first_line) {
                check_cross_first_line = inner_corner_no > 0 && last_curr_dir.determinant(first_direction) > 0 && last_curr_dir
                    .get_vector()
                    .scalar_product(first_direction.get_vector()) < 0
                // scalar_product checked to ignore backcrossing at
                // small inner_corner_no
            }
            if (check_cross_first_line) {
                val next_dir = next_division_line.direction() as IntDirection
                if (next_dir.determinant(first_direction) < 0) {
                    // The current piece has an intersection area with the first piece.
                    // Add a line to tmp_polyline to prevent this
                    merge_first_division_line = true
                }
            }
            var piece_line_count = 1
            if (merge_next_division_line) {
                ++piece_line_count
            }
            if (merge_last_curr_division_line) {
                ++piece_line_count
            }
            if (merge_prev_division_line) {
                ++piece_line_count
            }
            if (merge_first_division_line) {
                ++piece_line_count
            }
            val piece_lines = arrayOfNulls<Line>(piece_line_count)
            val curr_line = inner_simplex.arr[inner_corner_no]
            piece_lines[0] = Line(curr_line.b, curr_line.a)
            var curr_line_no = 0
            if (merge_next_division_line) {
                ++curr_line_no
                piece_lines[curr_line_no] = Line(next_division_line.b, next_division_line.a)
            }
            if (merge_last_curr_division_line) {
                ++curr_line_no
                piece_lines[curr_line_no] = last_curr_division_line
            }
            if (merge_prev_division_line) {
                ++curr_line_no
                piece_lines[curr_line_no] = prev_division_line
            }
            if (merge_first_division_line) {
                ++curr_line_no
                piece_lines[curr_line_no] = Line(first_division_line.b, first_division_line.a)
            }
            @Suppress("UNCHECKED_CAST")
            val curr_piece = Simplex(piece_lines as Array<Line>)
            result_list.add(curr_piece.intersection(p_outer_simplex))

            @Suppress("UNUSED_VALUE")
            var unused_next_division_line: Line? = next_division_line
            unused_next_division_line = prev_division_line
        }
        val result = Array(result_list.size) { i -> result_list[i] }
        return result
    }

    public override fun cutout_from(p_oct: IntOctagon): Array<Simplex> {
        return cutout_from(p_oct.to_Simplex())
    }

    public override fun cutout_from(p_box: IntBox): Array<Simplex> {
        return cutout_from(p_box.to_Simplex())
    }

    /**
     * Removes lines, which are redundant in the definition of the shape of this simplex. Assumes that the lines of this simplex are sorted.
     */
    fun remove_redundant_lines(): Simplex {
        val line_arr = arrayOfNulls<Line>(arr.size)
        // copy the sorted lines of arr into line_arr while skipping
        // multiple lines
        var new_length = 1
        line_arr[0] = arr[0]
        var prev = line_arr[0]!!
        for (i in 1 until arr.size) {
            if (!arr[i].fast_equals(prev)) {
                line_arr[new_length] = arr[i]
                prev = line_arr[new_length]!!
                ++new_length
            }
        }

        val intersection_sides = arrayOfNulls<Side>(new_length)
        // precalculated array , on which side of this line the previous and the
        // next line do intersect

        var try_again = new_length > 2
        var index_of_last_removed_line = new_length
        while (try_again) {
            try_again = false
            var prev_ind = new_length - 1
            var next_ind: Int
            var prev_line = line_arr[prev_ind]!!
            var curr_line = line_arr[0]!!
            var next_line: Line
            var ind = 0
            while (ind < new_length) {
                next_ind = if (ind == new_length - 1) {
                    0
                } else {
                    ind + 1
                }
                next_line = line_arr[next_ind]!!

                var remove_line = false
                val prev_dir = prev_line.direction() as IntDirection
                val next_dir = next_line.direction() as IntDirection
                val det = prev_dir.determinant(next_dir)
                if (det != 0.0) { // prev_line and next_line are not parallel
                    if (intersection_sides[ind] == null) {
                        // intersection_sides [ind] not precalculated
                        intersection_sides[ind] = curr_line.side_of_intersection(prev_line, next_line)
                    }
                    if (det > 0) {
                        // direction of next_line is bigger than direction of prev_line
                        // if the intersection of prev_line and next_line
                        // is on the left of curr_line, curr_line does not
                        // contribute to the shape of the simplex
                        remove_line = intersection_sides[ind] != Side.ON_THE_LEFT
                    } else {
                        // direction of next_line is smaller than direction of prev_line
                        if (intersection_sides[ind] == Side.ON_THE_LEFT) {
                            val curr_dir = curr_line.direction() as IntDirection
                            if (prev_dir.determinant(curr_dir) > 0) {
                                // the halfplane defined by curr_line does not intersect
                                // with the simplex defined by prev_line and nex_line,
                                // hence this simplex must be empty
                                new_length = 0
                                try_again = false
                                break
                            }
                        }
                    }
                } else { // prev_line and next_line are parallel
                    if (prev_line.side_of(next_line.a) == Side.ON_THE_LEFT) {
                        // prev_line is to the left of next_line,
                        // the halfplanes defined by prev_line and next_line
                        // do not intersect
                        new_length = 0
                        try_again = false
                        break
                    }
                }
                if (remove_line) {
                    try_again = true
                    --new_length
                    for (i in ind until new_length) {
                        line_arr[i] = line_arr[i + 1]
                        intersection_sides[i] = intersection_sides[i + 1]
                    }

                    if (new_length < 3) {
                        try_again = false
                        break
                    }
                    // reset 3 precalculated intersection_sides
                    if (ind == 0) {
                        prev_ind = new_length - 1
                    }
                    intersection_sides[prev_ind] = null
                    next_ind = if (ind >= new_length) {
                        0
                    } else {
                        ind
                    }
                    intersection_sides[next_ind] = null
                    --ind
                    index_of_last_removed_line = ind
                } else {
                    prev_line = curr_line
                    prev_ind = ind
                }
                curr_line = next_line
                if (!try_again && ind >= index_of_last_removed_line) {
                    // tried all lines without removing one
                    break
                }
                ind++
            }
        }

        if (new_length == 2) {
            val line_0 = line_arr[0]!!
            val line_1 = line_arr[1]!!
            if (line_0.is_parallel(line_1)) {
                if (line_0.direction() == line_1.direction()) {
                    // one of the two remaining lines is redundant
                    if (line_1.side_of(line_0.a) == Side.ON_THE_LEFT) {
                        line_arr[0] = line_1
                    }
                    --new_length
                } else {
                    // the two remaining lines have opposite direction
                    // the simplex may be empty
                    if (line_1.side_of(line_0.a) == Side.ON_THE_LEFT) {
                        new_length = 0
                    }
                }
            }
        }
        if (new_length == arr.size) {
            return this // nothing removed
        }
        if (new_length == 0) {
            return EMPTY
        }
        val result = Array(new_length) { i -> line_arr[i]!! }
        return Simplex(result)
    }

    override fun intersects(p_box: IntBox): Boolean {
        return intersects(p_box.to_Simplex())
    }

    override fun intersects(p_octagon: IntOctagon): Boolean {
        return intersects(p_octagon.to_Simplex())
    }

    override fun intersects(p_circle: Circle): Boolean {
        return p_circle.intersects(this)
    }

    /**
     * For each corner of this inner simplex 1 or 2 perpendicular projections onto lines of the outer simplex are constructed, so that the resulting pieces after cutting out the inner simplex are
     * convex. 2 projections may be necessary at sharp angle corners. Used in the method cutout_from with parametertype Simplex.
     */
    private fun calc_division_lines(p_inner_corner_no: Int, p_outer_simplex: Simplex): Array<Line>? {
        val curr_inner_line = this.arr[p_inner_corner_no]
        val prev_inner_line = if (p_inner_corner_no != 0) {
            this.arr[p_inner_corner_no - 1]
        } else {
            this.arr[arr.size - 1]
        }
        val intersection = curr_inner_line.intersection_approx(prev_inner_line)
        if (intersection.x >= Integer.MAX_VALUE) {
            FRLogger.warn("Simplex.calc_division_lines: intersection expected")
            return null
        }
        val inner_corner = intersection.round()
        val c_tolerance = 0.0001
        val is_exact = abs(inner_corner.x - intersection.x) < c_tolerance && abs(inner_corner.y - intersection.y) < c_tolerance

        if (!is_exact) {
            // it is assumed, that the corners of the original inner simplex are
            // exact and the not exact corners come from the intersection of
            // the inner simplex with the outer simplex.
            // Because these corners lie on the border of the outer simplex,
            // no division is necessary
            val result = arrayOf(prev_inner_line)
            return result
        }
        var first_projection_dir: IntDirection = Direction.NULL
        var second_projection_dir: IntDirection = Direction.NULL
        val prev_inner_dir = prev_inner_line.direction().opposite() as IntDirection
        val next_inner_dir = curr_inner_line.direction() as IntDirection
        var outer_line_no = 0

        // search the first outer line, so that
        // the perpendicular projection of the inner corner onto this
        // line is visible from inner_corner to the left of prev_inner_line.

        var min_distance = Integer.MAX_VALUE.toDouble()

        for (ind in 0 until p_outer_simplex.arr.size) {
            val outer_line = p_outer_simplex.arr[outer_line_no]
            val curr_projection_dir = inner_corner.perpendicular_direction(outer_line) as IntDirection
            if (curr_projection_dir == Direction.NULL) {
                val result = arrayOf(Line(inner_corner, inner_corner))
                return result
            }
            val projection_visible = prev_inner_dir.determinant(curr_projection_dir) >= 0
            if (projection_visible) {
                var curr_distance = abs(outer_line.signed_distance(inner_corner.to_float()))
                val second_division_necessary = curr_projection_dir.determinant(next_inner_dir) < 0
                // may occur at a sharp angle
                var curr_second_projection_dir = curr_projection_dir

                if (second_division_necessary) {
                    // search the first projection_dir between curr_projection_dir
                    // and next_inner_dir, that is visible from next_inner_line
                    var second_projection_visible = false
                    var tmp_outer_line_no = outer_line_no
                    while (!second_projection_visible) {
                        if (tmp_outer_line_no == p_outer_simplex.arr.size - 1) {
                            tmp_outer_line_no = 0
                        } else {
                            ++tmp_outer_line_no
                        }
                        curr_second_projection_dir = inner_corner.perpendicular_direction(p_outer_simplex.arr[tmp_outer_line_no]) as IntDirection

                        if (curr_second_projection_dir == Direction.NULL) {
                            // inner corner is on outer_line
                            val result = arrayOf(Line(inner_corner, inner_corner))
                            return result
                        }
                        if (curr_projection_dir.determinant(curr_second_projection_dir) < 0) {
                            // curr_second_projection_dir not found;
                            // the angle between curr_projection_dir and
                            // curr_second_projection_dir would be already bigger
                            // than 180 degree
                            curr_distance = Integer.MAX_VALUE.toDouble()
                            break
                        }

                        second_projection_visible = curr_second_projection_dir.determinant(next_inner_dir) >= 0
                    }
                    if (curr_distance < Integer.MAX_VALUE.toDouble()) {
                        curr_distance += abs(p_outer_simplex.arr[tmp_outer_line_no].signed_distance(inner_corner.to_float()))
                    }
                }
                if (curr_distance < min_distance) {
                    min_distance = curr_distance
                    first_projection_dir = curr_projection_dir
                    second_projection_dir = curr_second_projection_dir
                }
            }
            if (outer_line_no == p_outer_simplex.arr.size - 1) {
                outer_line_no = 0
            } else {
                ++outer_line_no
            }
        }
        if (min_distance == Integer.MAX_VALUE.toDouble()) {
            FRLogger.warn("Simplex.calc_division_lines: division not found")
            return null
        }
        val result: Array<Line>
        if (first_projection_dir == second_projection_dir) {
            result = arrayOf(Line(inner_corner, first_projection_dir))
        } else {
            result = arrayOf(
                Line(inner_corner, first_projection_dir),
                Line(inner_corner, second_projection_dir)
            )
        }
        return result
    }

    companion object {
        /**
         * Standard implementation for an empty Simplex.
         */
        @JvmField
        val EMPTY = Simplex(emptyArray())

        /**
         * creates a Simplex as intersection of the halfplanes defined by an array of directed lines
         */
        @JvmStatic
        fun get_instance(p_line_arr: Array<Line>): Simplex {
            if (p_line_arr.isEmpty()) {
                return EMPTY
            }
            val curr_arr = p_line_arr.copyOf()
            // sort the lines in ascending direction
            curr_arr.sort()
            val curr_simplex = Simplex(curr_arr)
            return curr_simplex.remove_redundant_lines()
        }
    }
}
