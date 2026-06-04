package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ceil

/**
 * Implements functionality of orthogonal rectangles in the plane with integer coordinates.
 */
class IntBox : RegularTileShape, Serializable {

    /**
     * coordinates of the lower left corner
     */
    @JvmField
    val ll: IntPoint

    /**
     * coordinates of the upper right corner
     */
    @JvmField
    val ur: IntPoint

    /**
     * Creates an IntBox from its lower left and upper right corners.
     */
    constructor(p_ll: IntPoint, p_ur: IntPoint) {
        ll = p_ll
        ur = p_ur
    }

    /**
     * creates an IntBox from the coordinates of its lower left and upper right corners.
     */
    constructor(p_ll_x: Int, p_ll_y: Int, p_ur_x: Int, p_ur_y: Int) {
        ll = IntPoint(p_ll_x, p_ll_y)
        ur = IntPoint(p_ur_x, p_ur_y)
    }

    override fun is_IntOctagon(): Boolean {
        return true
    }

    /**
     * Returns true, if the box is empty
     */
    override fun is_empty(): Boolean {
        return ll.x > ur.x || ll.y > ur.y
    }

    override fun border_line_count(): Int {
        return 4
    }

    /**
     * returns the horizontal extension of the box.
     */
    fun width(): Int {
        return ur.x - ll.x
    }

    /**
     * Returns the vertical extension of the box.
     */
    fun height(): Int {
        return ur.y - ll.y
    }

    override fun max_width(): Double {
        return max(ur.x - ll.x, ur.y - ll.y).toDouble()
    }

    override fun min_width(): Double {
        return min(ur.x - ll.x, ur.y - ll.y).toDouble()
    }

    override fun area(): Double {
        return (ur.x - ll.x).toDouble() * (ur.y - ll.y).toDouble()
    }

    override fun circumference(): Double {
        return 2.0 * ((ur.x - ll.x) + (ur.y - ll.y))
    }

    override fun corner(p_no: Int): IntPoint {
        if (p_no == 0) {
            return ll
        }
        if (p_no == 1) {
            return IntPoint(ur.x, ll.y)
        }
        if (p_no == 2) {
            return ur
        }
        if (p_no == 3) {
            return IntPoint(ll.x, ur.y)
        }
        throw IllegalArgumentException("IntBox.corner: p_no out of range")
    }

    override fun dimension(): Int {
        if (is_empty()) {
            return -1
        }
        if (ll == ur) {
            return 0
        }
        if (ur.x == ll.x || ll.y == ur.y) {
            return 1
        }
        return 2
    }

    /**
     * Checks, if p_point is located in the interior of this box.
     */
    fun contains_inside(p_point: IntPoint): Boolean {
        return p_point.x > this.ll.x && p_point.x < this.ur.x && p_point.y > this.ll.y && p_point.y < this.ur.y
    }

    override fun is_IntBox(): Boolean {
        return true
    }

    override fun simplify(): TileShape {
        return this
    }

    /**
     * Calculates the nearest point of this box to p_from_point.
     */
    fun nearest_point(p_from_point: FloatPoint): FloatPoint {
        val x: Double = if (p_from_point.x <= ll.x) {
            ll.x.toDouble()
        } else if (p_from_point.x >= ur.x) {
            ur.x.toDouble()
        } else {
            p_from_point.x
        }

        val y: Double = if (p_from_point.y <= ll.y) {
            ll.y.toDouble()
        } else if (p_from_point.y >= ur.y) {
            ur.y.toDouble()
        } else {
            p_from_point.y
        }

        return FloatPoint(x, y)
    }

    /**
     * Calculates the sorted p_max_result_points nearest points on the border of this box. p_point is assumed to be located in the interior of this nox. The function is only implemented for
     * p_max_result_points {@literal <=} 2;
     */
    fun nearest_border_projections(p_point: IntPoint, p_max_result_points: Int): Array<IntPoint> {
        var max_result_points = p_max_result_points
        if (max_result_points <= 0) {
            return emptyArray()
        }
        max_result_points = min(max_result_points, 2)
        val result = Array(max_result_points) { IntPoint(0, 0) }

        val lower_x_diff = p_point.x - ll.x
        val upper_x_diff = ur.x - p_point.x
        val lower_y_diff = p_point.y - ll.y
        val upper_y_diff = ur.y - p_point.y

        var min_diff: Int
        var second_min_diff: Int

        var nearest_projection_x = p_point.x
        var nearest_projection_y = p_point.y
        var second_nearest_projection_x = p_point.x
        var second_nearest_projection_y = p_point.y
        if (lower_x_diff <= upper_x_diff) {
            min_diff = lower_x_diff
            second_min_diff = upper_x_diff
            nearest_projection_x = ll.x
            second_nearest_projection_x = ur.x
        } else {
            min_diff = upper_x_diff
            second_min_diff = lower_x_diff
            nearest_projection_x = ur.x
            second_nearest_projection_x = ll.x
        }
        if (lower_y_diff < min_diff) {
            second_min_diff = min_diff
            min_diff = lower_y_diff
            second_nearest_projection_x = nearest_projection_x
            second_nearest_projection_y = nearest_projection_y
            nearest_projection_x = p_point.x
            nearest_projection_y = ll.y
        } else if (lower_y_diff < second_min_diff) {
            second_min_diff = lower_y_diff
            second_nearest_projection_x = p_point.x
            second_nearest_projection_y = ll.y
        }
        if (upper_y_diff < min_diff) {
            second_min_diff = min_diff
            min_diff = upper_y_diff
            second_nearest_projection_x = nearest_projection_x
            second_nearest_projection_y = nearest_projection_y
            nearest_projection_x = p_point.x
            nearest_projection_y = ur.y
        } else if (upper_y_diff < second_min_diff) {
            second_min_diff = upper_y_diff
            second_nearest_projection_x = p_point.x
            second_nearest_projection_y = ur.y
        }
        result[0] = IntPoint(nearest_projection_x, nearest_projection_y)
        if (result.size > 1) {
            result[1] = IntPoint(second_nearest_projection_x, second_nearest_projection_y)
        }

        return result
    }

    /**
     * Calculates distance of this box to p_from_point.
     */
    override fun distance(p_from_point: FloatPoint): Double {
        return p_from_point.distance(nearest_point(p_from_point))
    }

    /**
     * Computes the weighted distance to the box p_other.
     */
    fun weighted_distance(p_other: IntBox, p_horizontal_weight: Double, p_vertical_weight: Double): Double {
        val result: Double

        val max_ll_x = max(this.ll.x, p_other.ll.x).toDouble()
        val max_ll_y = max(this.ll.y, p_other.ll.y).toDouble()
        val min_ur_x = min(this.ur.x, p_other.ur.x).toDouble()
        val min_ur_y = min(this.ur.y, p_other.ur.y).toDouble()

        if (min_ur_x >= max_ll_x) {
            result = max(p_vertical_weight * (max_ll_y - min_ur_y), 0.0)
        } else if (min_ur_y >= max_ll_y) {
            result = max(p_horizontal_weight * (max_ll_x - min_ur_x), 0.0)
        } else {
            var delta_x = max_ll_x - min_ur_x
            var delta_y = max_ll_y - min_ur_y
            delta_x *= p_horizontal_weight
            delta_y *= p_vertical_weight
            result = Math.sqrt(delta_x * delta_x + delta_y * delta_y)
        }
        return result
    }

    override fun bounding_box(): IntBox {
        return this
    }

    override fun get_id_no(): Int {
        return 31 * ll.get_id_no() + ur.get_id_no()
    }

    override fun bounding_octagon(): IntOctagon {
        return to_IntOctagon()
    }

    override fun is_bounded(): Boolean {
        return true
    }

    override fun bounding_tile(): IntBox {
        return this
    }

    override fun corner_is_bounded(p_no: Int): Boolean {
        return true
    }

    override fun union(p_other: RegularTileShape): RegularTileShape {
        return p_other.union(this)
    }

    public override fun union(p_other: IntBox): IntBox {
        val llx = min(ll.x, p_other.ll.x)
        val lly = min(ll.y, p_other.ll.y)
        val urx = max(ur.x, p_other.ur.x)
        val ury = max(ur.y, p_other.ur.y)
        return IntBox(llx, lly, urx, ury)
    }

    /**
     * Returns the intersection of this box with an IntBox.
     */
    public override fun intersection(p_other: IntBox): IntBox {
        if (p_other.ll.x > ur.x) {
            return EMPTY
        }
        if (p_other.ll.y > ur.y) {
            return EMPTY
        }
        if (ll.x > p_other.ur.x) {
            return EMPTY
        }
        if (ll.y > p_other.ur.y) {
            return EMPTY
        }
        val llx = max(ll.x, p_other.ll.x)
        val urx = min(ur.x, p_other.ur.x)
        val lly = max(ll.y, p_other.ll.y)
        val ury = min(ur.y, p_other.ur.y)
        return IntBox(llx, lly, urx, ury)
    }

    /**
     * returns the intersection of this box with a ConvexShape
     */
    override fun intersection(p_other: TileShape): TileShape {
        return p_other.intersection(this)
    }

    public override fun intersection(p_other: IntOctagon): IntOctagon {
        return p_other.intersection(this.to_IntOctagon())
    }

    public override fun intersection(p_other: Simplex): Simplex {
        return p_other.intersection(this.to_Simplex())
    }

    override fun intersects(p_other: Shape): Boolean {
        return p_other.intersects(this)
    }

    override fun intersects(p_other: IntBox): Boolean {
        if (p_other.ll.x > this.ur.x) {
            return false
        }
        if (p_other.ll.y > this.ur.y) {
            return false
        }
        if (this.ll.x > p_other.ur.x) {
            return false
        }
        return this.ll.y <= p_other.ur.y
    }

    /**
     * Returns true, if this box intersects with p_other and the intersection is 2-dimensional.
     */
    fun overlaps(p_other: IntBox): Boolean {
        if (p_other.ll.x >= this.ur.x) {
            return false
        }
        if (p_other.ll.y >= this.ur.y) {
            return false
        }
        if (this.ll.x >= p_other.ur.x) {
            return false
        }
        return this.ll.y < p_other.ur.y
    }

    override fun contains(p_other: RegularTileShape): Boolean {
        return p_other.is_contained_in(this)
    }

    override fun bounding_shape(p_dirs: ShapeBoundingDirections): RegularTileShape {
        return p_dirs.bounds(this)
    }

    /**
     * Enlarges the box by p_offset. Contrary to the offset() method the result is an IntOctagon, not an IntBox.
     */
    override fun enlarge(p_offset: Double): IntOctagon {
        return bounding_octagon().offset(p_offset)
    }

    override fun translate_by(p_rel_coor: Vector): IntBox {
        // This function is at the moment only implemented for Vectors
        // with integer coordinates.
        // The general implementation is still missing.

        if (p_rel_coor.equals(Vector.ZERO)) {
            return this
        }
        val new_ll = ll.translate_by(p_rel_coor) as IntPoint
        val new_ur = ur.translate_by(p_rel_coor) as IntPoint
        return IntBox(new_ll, new_ur)
    }

    override fun turn_90_degree(p_factor: Int, p_pole: IntPoint): IntBox {
        val p1 = ll.turn_90_degree(p_factor, p_pole) as IntPoint
        val p2 = ur.turn_90_degree(p_factor, p_pole) as IntPoint

        val llx = min(p1.x, p2.x)
        val lly = min(p1.y, p2.y)
        val urx = max(p1.x, p2.x)
        val ury = max(p1.y, p2.y)
        return IntBox(llx, lly, urx, ury)
    }

    override fun border_line(p_no: Int): Line {
        val a_x: Int
        val a_y: Int
        val b_x: Int
        val b_y: Int
        when (p_no) {
            0 -> {
                // lower boundary line
                a_x = 0
                a_y = ll.y
                b_x = 1
                b_y = ll.y
            }
            1 -> {
                // right boundary line
                a_x = ur.x
                a_y = 0
                b_x = ur.x
                b_y = 1
            }
            2 -> {
                // upper boundary line
                a_x = 0
                a_y = ur.y
                b_x = -1
                b_y = ur.y
            }
            3 -> {
                // left boundary line
                a_x = ll.x
                a_y = 0
                b_x = ll.x
                b_y = -1
            }
            else -> throw IllegalArgumentException("IntBox.edge_line: p_no out of range")
        }
        return Line(a_x, a_y, b_x, b_y)
    }

    override fun border_line_index(p_line: Line): Int {
        FRLogger.warn("edge_index_of_line not yet implemented for IntBoxes")
        return -1
    }

    /**
     * Returns the box offseted by p_dist. If p_dist {@literal >} 0, the offset is to the outside, else to the inside.
     */
    override fun offset(p_dist: Double): IntBox {
        if (p_dist == 0.0 || is_empty()) {
            return this
        }
        val dist = Math.round(p_dist).toInt()
        val lower_left = IntPoint(ll.x - dist, ll.y - dist)
        val upper_right = IntPoint(ur.x + dist, ur.y + dist)
        return IntBox(lower_left, upper_right)
    }

    /**
     * Returns the box, where the horizontal boundary is offseted by p_dist. If p_dist {@literal >} 0, the offset is to the outside, else to the inside.
     */
    fun horizontal_offset(p_dist: Double): IntBox {
        if (p_dist == 0.0 || is_empty()) {
            return this
        }
        val dist = Math.round(p_dist).toInt()
        val lower_left = IntPoint(ll.x - dist, ll.y)
        val upper_right = IntPoint(ur.x + dist, ur.y)
        return IntBox(lower_left, upper_right)
    }

    /**
     * Returns the box, where the vertical boundary is offseted by p_dist. If p_dist {@literal >} 0, the offset is to the outside, else to the inside.
     */
    fun vertical_offset(p_dist: Double): IntBox {
        if (p_dist == 0.0 || is_empty()) {
            return this
        }
        val dist = Math.round(p_dist).toInt()
        val lower_left = IntPoint(ll.x, ll.y - dist)
        val upper_right = IntPoint(ur.x, ur.y + dist)
        return IntBox(lower_left, upper_right)
    }

    /**
     * Shrinks the width and height of the box by the input width. The box will not vanish completely.
     */
    fun shrink(p_width: Int): IntBox {
        val ll_x: Int
        val ur_x: Int
        if (2 * p_width <= this.ur.x - this.ll.x) {
            ll_x = this.ll.x + p_width
            ur_x = this.ur.x - p_width
        } else {
            ll_x = (this.ll.x + this.ur.x) / 2
            ur_x = ll_x
        }
        val ll_y: Int
        val ur_y: Int
        if (2 * p_width <= this.ur.y - this.ll.y) {
            ll_y = this.ll.y + p_width
            ur_y = this.ur.y - p_width
        } else {
            ll_y = (this.ll.y + this.ur.y) / 2
            ur_y = ll_y
        }
        return IntBox(ll_x, ll_y, ur_x, ur_y)
    }

    override fun compare(p_other: RegularTileShape, p_edge_no: Int): Side {
        val result = p_other.compare(this, p_edge_no)
        return result.negate()
    }

    public override fun compare(p_other: IntBox, p_edge_no: Int): Side {
        val result: Side
        when (p_edge_no) {
            0 -> {
                // compare the lower edge line
                if (ll.y > p_other.ll.y) {
                    result = Side.ON_THE_LEFT
                } else if (ll.y < p_other.ll.y) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            1 -> {
                // compare the right edge line
                if (ur.x < p_other.ur.x) {
                    result = Side.ON_THE_LEFT
                } else if (ur.x > p_other.ur.x) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            2 -> {
                // compare the upper edge line
                if (ur.y < p_other.ur.y) {
                    result = Side.ON_THE_LEFT
                } else if (ur.y > p_other.ur.y) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            3 -> {
                // compare the left edge line
                if (ll.x > p_other.ll.x) {
                    result = Side.ON_THE_LEFT
                } else if (ll.x < p_other.ll.x) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            else -> throw IllegalArgumentException("IntBox.compare: p_edge_no out of range")
        }
        return result
    }

    /**
     * Returns an object of class IntOctagon defining the same shape
     */
    fun to_IntOctagon(): IntOctagon {
        return IntOctagon(ll.x, ll.y, ur.x, ur.y, ll.x - ur.y, ur.x - ll.y, ll.x + ll.y, ur.x + ur.y)
    }

    /**
     * Returns an object of class Simplex defining the same shape
     */
    override fun to_Simplex(): Simplex {
        val line_arr: Array<Line>
        if (is_empty()) {
            line_arr = emptyArray()
        } else {
            line_arr = arrayOf(
                Line.get_instance(ll, Direction.RIGHT),
                Line.get_instance(ur, Direction.UP),
                Line.get_instance(ur, Direction.LEFT),
                Line.get_instance(ll, Direction.DOWN)
            )
        }
        return Simplex(line_arr)
    }

    override fun is_contained_in(p_other: IntBox): Boolean {
        if (is_empty() || this === p_other) {
            return true
        }
        return ll.x >= p_other.ll.x && ll.y >= p_other.ll.y && ur.x <= p_other.ur.x && ur.y <= p_other.ur.y
    }

    /**
     * Return true, if p_other is contained in the interior of this box.
     */
    fun contains_in_interior(p_other: IntBox): Boolean {
        if (p_other.is_empty()) {
            return true
        }
        return p_other.ll.x > ll.x && p_other.ll.y > ll.y && p_other.ur.x < ur.x && p_other.ur.y < ur.y
    }

    /**
     * Calculates the part of p_from_box, which has minimal distance to this box.
     */
    fun nearest_part(p_from_box: IntBox): IntBox {
        val ll_x: Int = if (p_from_box.ll.x >= this.ll.x) {
            p_from_box.ll.x
        } else {
            min(p_from_box.ur.x, this.ll.x)
        }

        val ur_x: Int = if (p_from_box.ur.x <= this.ur.x) {
            p_from_box.ur.x
        } else {
            max(p_from_box.ll.x, this.ur.x)
        }

        val ll_y: Int = if (p_from_box.ll.y >= this.ll.y) {
            p_from_box.ll.y
        } else {
            min(p_from_box.ur.y, this.ll.y)
        }

        val ur_y: Int = if (p_from_box.ur.y <= this.ur.y) {
            p_from_box.ur.y
        } else {
            max(p_from_box.ll.y, this.ur.y)
        }
        return IntBox(ll_x, ll_y, ur_x, ur_y)
    }

    public override fun is_contained_in(p_other: IntOctagon): Boolean {
        return p_other.contains(to_IntOctagon())
    }

    override fun intersects(p_other: IntOctagon): Boolean {
        return p_other.intersects(to_IntOctagon())
    }

    override fun intersects(p_other: Simplex): Boolean {
        return p_other.intersects(to_Simplex())
    }

    override fun intersects(p_other: Circle): Boolean {
        return p_other.intersects(this)
    }

    public override fun union(p_other: IntOctagon): IntOctagon {
        return p_other.union(to_IntOctagon())
    }

    public override fun compare(p_other: IntOctagon, p_edge_no: Int): Side {
        return to_IntOctagon().compare(p_other, p_edge_no)
    }

    /**
     * Divides this box into sections with width and height at most p_max_section_width of about equal size.
     */
    override fun divide_into_sections(p_max_section_width: Double): Array<IntBox> {
        if (p_max_section_width <= 0) {
            return emptyArray()
        }
        val length = (this.ur.x - this.ll.x).toDouble()
        val height = (this.ur.y - this.ll.y).toDouble()
        val x_count = ceil(length / p_max_section_width).toInt()
        val y_count = ceil(height / p_max_section_width).toInt()
        val section_length_x = ceil(length / x_count).toInt()
        val section_length_y = ceil(height / y_count).toInt()
        val result = Array(x_count * y_count) { EMPTY }
        var curr_index = 0
        for (j in 0 until y_count) {
            val curr_lly = this.ll.y + j * section_length_y
            val curr_ury: Int = if (j == y_count - 1) {
                this.ur.y
            } else {
                curr_lly + section_length_y
            }
            for (i in 0 until x_count) {
                val curr_llx = this.ll.x + i * section_length_x
                val curr_urx: Int = if (i == x_count - 1) {
                    this.ur.x
                } else {
                    curr_llx + section_length_x
                }
                result[curr_index] = IntBox(curr_llx, curr_lly, curr_urx, curr_ury)
                ++curr_index
            }
        }
        return result
    }

    override fun cutout(p_shape: TileShape): Array<TileShape> {
        val tmp_result = p_shape.cutout_from(this)
        val result = Array(tmp_result.size) { tmp_result[it].simplify() }
        return result
    }

    public override fun cutout_from(p_d: IntBox): Array<IntBox> {
        val c = this.intersection(p_d)
        if (this.is_empty() || c.dimension() < this.dimension()) {
            // there is only an overlap at the border
            val result = Array(1) { p_d }
            return result
        }

        val result = Array(4) { EMPTY }

        result[0] = IntBox(p_d.ll.x, p_d.ll.y, c.ur.x, c.ll.y)

        result[1] = IntBox(p_d.ll.x, c.ll.y, c.ll.x, p_d.ur.y)

        result[2] = IntBox(c.ur.x, p_d.ll.y, p_d.ur.x, c.ur.y)

        result[3] = IntBox(c.ll.x, c.ur.y, p_d.ur.x, p_d.ur.y)

        // now the division will be optimised, so that the cumulative
        // circumference will be minimal.

        var b: IntBox

        if (c.ll.x - p_d.ll.x > c.ll.y - p_d.ll.y) {
            // switch left dividing line to lower
            b = result[0]
            result[0] = IntBox(c.ll.x, b.ll.y, b.ur.x, b.ur.y)
            b = result[1]
            result[1] = IntBox(b.ll.x, p_d.ll.y, b.ur.x, b.ur.y)
        }
        if (p_d.ur.y - c.ur.y > c.ll.x - p_d.ll.x) {
            // switch upper dividing line to the left
            b = result[1]
            result[1] = IntBox(b.ll.x, b.ll.y, b.ur.x, c.ur.y)
            b = result[3]
            result[3] = IntBox(p_d.ll.x, b.ll.y, b.ur.x, b.ur.y)
        }
        if (p_d.ur.x - c.ur.x > p_d.ur.y - c.ur.y) {
            // switch right dividing line to upper
            b = result[2]
            result[2] = IntBox(b.ll.x, b.ll.y, b.ur.x, p_d.ur.y)
            b = result[3]
            result[3] = IntBox(b.ll.x, b.ll.y, c.ur.x, b.ur.y)
        }
        if (c.ll.y - p_d.ll.y > p_d.ur.x - c.ur.x) {
            // switch lower dividing line to the left
            b = result[0]
            result[0] = IntBox(b.ll.x, b.ll.y, p_d.ur.x, b.ur.y)
            b = result[2]
            result[2] = IntBox(b.ll.x, c.ll.y, b.ur.x, b.ur.y)
        }
        return result
    }

    public override fun cutout_from(p_simplex: Simplex): Array<Simplex> {
        return this.to_Simplex().cutout_from(p_simplex)
    }

    public override fun cutout_from(p_oct: IntOctagon): Array<IntOctagon> {
        return this.to_IntOctagon().cutout_from(p_oct)
    }

    companion object {
        /**
         * Standard implementation of an empty box.
         */
        @JvmField
        val EMPTY = IntBox(Limits.CRIT_INT, Limits.CRIT_INT, -Limits.CRIT_INT, -Limits.CRIT_INT)
    }
}
