package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.abs

/**
 * IntOctagon is a specialized geometric shape implementation representing an octagon with integer coordinates and 45-degree angle constraints. The class extends RegularTileShape and provides
 * efficient representations for PCB (Printed Circuit Board) routing spaces.
 */
class IntOctagon : RegularTileShape, Serializable {

    /* Vertical boundaries (east/west) */

    // X-coordinate of the left vertical border
    @JvmField
    val leftX: Int
    // X-coordinate of the right vertical border
    @JvmField
    val rightX: Int

    /* Horizontal boundaries (north/south) */

    // Y-coordinate of the bottom horizontal border
    @JvmField
    val bottomY: Int
    // Y-coordinate of the top horizontal border
    @JvmField
    val topY: Int

    /* Diagonal boundaries at +45° angle */

    // X-axis intersection of lower-left diagonal border
    @JvmField
    val lowerLeftDiagonalX: Int
    // X-axis intersection of upper-right diagonal border
    @JvmField
    val upperRightDiagonalX: Int

    /* Diagonal boundaries at -45° angle */

    // X-axis intersection of upper-left diagonal border
    @JvmField
    val upperLeftDiagonalX: Int
    // X-axis intersection of lower-right diagonal border
    @JvmField
    val lowerRightDiagonalX: Int

    /**
     * Result of to_simplex() memorized for performance reasons.
     */
    @Transient
    private var precalculated_to_simplex: Simplex? = null

    /**
     * Creates an IntOctagon from 8 integer values. p_lx is the smallest x value of the shape. p_ly is the smallest y value of the shape. p_rx is the biggest x value af the shape. p_uy is the biggest y
     * value of the shape. p_ulx is the intersection of the upper left diagonal boundary line with the x axis. p_lrx is the intersection of the lower right diagonal boundary line with the x axis. p_llx
     * is the intersection of the lower left diagonal boundary line with the x axis. p_urx is the intersection of the upper right diagonal boundary line with the x axis.
     */
    constructor(p_lx: Int, p_ly: Int, p_rx: Int, p_uy: Int, p_ulx: Int, p_lrx: Int, p_llx: Int, p_urx: Int) {
        leftX = p_lx
        bottomY = p_ly
        rightX = p_rx
        topY = p_uy
        upperLeftDiagonalX = p_ulx
        lowerRightDiagonalX = p_lrx
        lowerLeftDiagonalX = p_llx
        upperRightDiagonalX = p_urx
    }

    override fun is_empty(): Boolean {
        return this === EMPTY
    }

    override fun is_IntOctagon(): Boolean {
        return true
    }

    override fun is_bounded(): Boolean {
        return true
    }

    override fun corner_is_bounded(p_no: Int): Boolean {
        return true
    }

    override fun bounding_box(): IntBox {
        return IntBox(leftX, bottomY, rightX, topY)
    }

    override fun bounding_octagon(): IntOctagon {
        return this
    }

    override fun bounding_tile(): IntOctagon {
        return this
    }

    override fun dimension(): Int {
        if (this === EMPTY) {
            return -1
        }
        val result: Int

        if (rightX > leftX && topY > bottomY && lowerRightDiagonalX > upperLeftDiagonalX && upperRightDiagonalX > lowerLeftDiagonalX) {
            result = 2
        } else if (rightX == leftX && topY == bottomY) {
            result = 0
        } else {
            result = 1
        }
        return result
    }

    override fun corner(p_no: Int): IntPoint {
        val x: Int
        val y: Int
        when (p_no) {
            0 -> {
                x = lowerLeftDiagonalX - bottomY
                y = bottomY
            }
            1 -> {
                x = lowerRightDiagonalX + bottomY
                y = bottomY
            }
            2 -> {
                x = rightX
                y = rightX - lowerRightDiagonalX
            }
            3 -> {
                x = rightX
                y = upperRightDiagonalX - rightX
            }
            4 -> {
                x = upperRightDiagonalX - topY
                y = topY
            }
            5 -> {
                x = upperLeftDiagonalX + topY
                y = topY
            }
            6 -> {
                x = leftX
                y = leftX - upperLeftDiagonalX
            }
            7 -> {
                x = leftX
                y = lowerLeftDiagonalX - leftX
            }
            else -> throw IllegalArgumentException("IntOctagon.corner: p_no out of range")
        }
        return IntPoint(x, y)
    }

    override fun get_id_no(): Int {
        var result = leftX
        result = 31 * result + rightX
        result = 31 * result + bottomY
        result = 31 * result + topY
        result = 31 * result + lowerLeftDiagonalX
        result = 31 * result + upperRightDiagonalX
        result = 31 * result + upperLeftDiagonalX
        result = 31 * result + lowerRightDiagonalX
        return result
    }

    /**
     * Additional to the function corner() for performance reasons to avoid allocation of an IntPoint.
     */
    fun corner_y(p_no: Int): Int {
        return when (p_no) {
            0, 1 -> bottomY
            2 -> rightX - lowerRightDiagonalX
            3 -> upperRightDiagonalX - rightX
            4, 5 -> topY
            6 -> leftX - upperLeftDiagonalX
            7 -> lowerLeftDiagonalX - leftX
            else -> throw IllegalArgumentException("IntOctagon.corner: p_no out of range")
        }
    }

    /**
     * Additional to the function corner() for performance reasons to avoid allocation of an IntPoint.
     */
    fun corner_x(p_no: Int): Int {
        return when (p_no) {
            0 -> lowerLeftDiagonalX - bottomY
            1 -> lowerRightDiagonalX + bottomY
            2, 3 -> rightX
            4 -> upperRightDiagonalX - topY
            5 -> upperLeftDiagonalX + topY
            6, 7 -> leftX
            else -> throw IllegalArgumentException("IntOctagon.corner: p_no out of range")
        }
    }

    override fun area(): Double {
        // calculate half of the absolute value of
        // x0 (y1 - y7) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ x7( y0 - y6)
        // where xi, yi are the coordinates of the i-th corner of this Octagon.

        // Overwrites the same implementation in TileShape for performance
        // reasons to avoid Point allocation.

        var result = (lowerLeftDiagonalX - bottomY).toDouble() * (bottomY - lowerLeftDiagonalX + leftX).toDouble()
        result += (lowerRightDiagonalX + bottomY).toDouble() * (rightX - lowerRightDiagonalX - bottomY).toDouble()
        result += rightX.toDouble() * (upperRightDiagonalX - 2 * rightX - bottomY + topY + lowerRightDiagonalX).toDouble()
        result += (upperRightDiagonalX - topY).toDouble() * (topY - upperRightDiagonalX + rightX).toDouble()
        result += (upperLeftDiagonalX + topY).toDouble() * (leftX - upperLeftDiagonalX - topY).toDouble()
        result += leftX.toDouble() * (lowerLeftDiagonalX - 2 * leftX - topY + bottomY + upperLeftDiagonalX).toDouble()

        return 0.5 * abs(result)
    }

    override fun border_line_count(): Int {
        return 8
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
                a_y = bottomY
                b_x = 1
                b_y = bottomY
            }
            1 -> {
                // lower right boundary line
                a_x = lowerRightDiagonalX
                a_y = 0
                b_x = lowerRightDiagonalX + 1
                b_y = 1
            }
            2 -> {
                // right boundary line
                a_x = rightX
                a_y = 0
                b_x = rightX
                b_y = 1
            }
            3 -> {
                // upper right boundary line
                a_x = upperRightDiagonalX
                a_y = 0
                b_x = upperRightDiagonalX - 1
                b_y = 1
            }
            4 -> {
                // upper boundary line
                a_x = 0
                a_y = topY
                b_x = -1
                b_y = topY
            }
            5 -> {
                // upper left boundary line
                a_x = upperLeftDiagonalX
                a_y = 0
                b_x = upperLeftDiagonalX - 1
                b_y = -1
            }
            6 -> {
                // left boundary line
                a_x = leftX
                a_y = 0
                b_x = leftX
                b_y = -1
            }
            7 -> {
                // lower left boundary line
                a_x = lowerLeftDiagonalX
                a_y = 0
                b_x = lowerLeftDiagonalX + 1
                b_y = -1
            }
            else -> throw IllegalArgumentException("IntOctagon.edge_line: p_no out of range")
        }
        return Line(a_x, a_y, b_x, b_y)
    }

    public override fun translate_by(p_rel_coor: Vector): IntOctagon {
        // This function is at the moment only implemented for Vectors
        // with integer coordinates.
        // The general implementation is still missing.

        if (p_rel_coor.equals(Vector.ZERO)) {
            return this
        }
        val rel_coor = p_rel_coor as IntVector
        return IntOctagon(
            leftX + rel_coor.x,
            bottomY + rel_coor.y,
            rightX + rel_coor.x,
            topY + rel_coor.y,
            upperLeftDiagonalX + rel_coor.x - rel_coor.y,
            lowerRightDiagonalX + rel_coor.x - rel_coor.y,
            lowerLeftDiagonalX + rel_coor.x + rel_coor.y,
            upperRightDiagonalX + rel_coor.x + rel_coor.y
        )
    }

    override fun max_width(): Double {
        val width_1 = max(rightX - leftX, topY - bottomY).toDouble()
        val width2 = max(upperRightDiagonalX - lowerLeftDiagonalX, lowerRightDiagonalX - upperLeftDiagonalX).toDouble()
        return max(width_1, width2 / Limits.sqrt2)
    }

    override fun min_width(): Double {
        val width_1 = min(rightX - leftX, topY - bottomY).toDouble()
        val width2 = min(upperRightDiagonalX - lowerLeftDiagonalX, lowerRightDiagonalX - upperLeftDiagonalX).toDouble()
        return min(width_1, width2 / Limits.sqrt2)
    }

    public override fun offset(p_distance: Double): IntOctagon {
        val width = Math.round(p_distance).toInt()
        if (width == 0) {
            return this
        }
        val dia_width = Math.round(Limits.sqrt2 * p_distance).toInt()
        val result = IntOctagon(
            leftX - width,
            bottomY - width,
            rightX + width,
            topY + width,
            upperLeftDiagonalX - dia_width,
            lowerRightDiagonalX + dia_width,
            lowerLeftDiagonalX - dia_width,
            upperRightDiagonalX + dia_width
        )
        return result.normalize()
    }

    override fun enlarge(p_offset: Double): IntOctagon {
        return offset(p_offset)
    }

    override fun contains(p_other: RegularTileShape): Boolean {
        return p_other.is_contained_in(this)
    }

    override fun union(p_other: RegularTileShape): RegularTileShape {
        return p_other.union(this)
    }

    override fun intersection(p_other: TileShape): TileShape {
        return p_other.intersection(this)
    }

    fun normalize(): IntOctagon {
        if (leftX > rightX || bottomY > topY || lowerLeftDiagonalX > upperRightDiagonalX || upperLeftDiagonalX > lowerRightDiagonalX) {
            return EMPTY
        }
        var new_lx = leftX
        var new_rx = rightX
        var new_ly = bottomY
        var new_uy = topY
        var new_llx = lowerLeftDiagonalX
        var new_ulx = upperLeftDiagonalX
        var new_lrx = lowerRightDiagonalX
        var new_urx = upperRightDiagonalX

        if (new_lx < new_llx - new_uy) {
            // the point new_lx, new_uy is the lower left border line of
            // this octagon
            // change new_lx , that the lower left border line runs through
            // this point
            new_lx = new_llx - new_uy
        }

        if (new_lx < new_ulx + new_ly) {
            // the point new_lx, new_ly is above the upper left border line of
            // this octagon
            // change new_lx , that the upper left border line runs through
            // this point
            new_lx = new_ulx + new_ly
        }

        if (new_rx > new_urx - new_ly) {
            // the point new_rx, new_ly is above the upper right border line of
            // this octagon
            // change new_rx , that the upper right border line runs through
            // this point
            new_rx = new_urx - new_ly
        }

        if (new_rx > new_lrx + new_uy) {
            // the point new_rx, new_uy is below the lower right border line of
            // this octagon
            // change rx , that the lower right border line runs through
            // this point
            new_rx = new_lrx + new_uy
        }

        if (new_ly < new_lx - new_lrx) {
            // the point lx, ly is below the lower right border line of this
            // octagon
            // change ly, so that the lower right border line runs through
            // this point
            new_ly = new_lx - new_lrx
        }

        if (new_ly < new_llx - new_rx) {
            // the point rx, ly is below the lower left border line of
            // this octagon.
            // change ly, so that the lower left border line runs through
            // this point
            new_ly = new_llx - new_rx
        }

        if (new_uy > new_urx - new_lx) {
            // the point lx, uy is above the upper right border line of
            // this octagon.
            // Change the uy, so that the upper right border line runs through
            // this point.
            new_uy = new_urx - new_lx
        }

        if (new_uy > new_rx - new_ulx) {
            // the point rx, uy is above the upper left border line of
            // this octagon.
            // Change the uy, so that the upper left border line runs through
            // this point.
            new_uy = new_rx - new_ulx
        }

        if (new_llx - new_lx < new_ly) {
            // The point lx, ly is above the lower left border line of
            // this octagon.
            // Change the lower left line, so that it runs through this point.
            new_llx = new_lx + new_ly
        }

        if (new_rx - new_lrx < new_ly) {
            // the point rx, ly is above the lower right border line of
            // this octagon.
            // Change the lower right line, so that it runs through this point.
            new_lrx = new_rx - new_ly
        }

        if (new_urx - new_rx > new_uy) {
            // the point rx, uy is below the upper right border line of p_oct.
            // Change the upper right line, so that it runs through this point.
            new_urx = new_uy + new_rx
        }

        if (new_lx - new_ulx > new_uy) {
            // the point lx, uy is below the upper left border line of
            // this octagon.
            // Change the upper left line, so that it runs through this point.
            new_ulx = new_lx - new_uy
        }

        val diag_upper_y = ceil((new_urx - new_ulx) / 2.0).toInt()

        if (new_uy > diag_upper_y) {
            // the intersection of the upper right and the upper left border
            // line is below new_uy.  Adjust new_uy to diag_upper_y.
            new_uy = diag_upper_y
        }

        val diag_lower_y = floor((new_llx - new_lrx) / 2.0).toInt()

        if (new_ly < diag_lower_y) {
            // the intersection of the lower right and the lower left border
            // line is above new_ly.  Adjust new_ly to diag_lower_y.
            new_ly = diag_lower_y
        }

        val diag_right_x = ceil((new_urx + new_lrx) / 2.0).toInt()

        if (new_rx > diag_right_x) {
            // the intersection of the upper right and the lower right border
            // line is to the left of  right x.  Adjust new_rx to diag_right_x.
            new_rx = diag_right_x
        }

        val diag_left_x = floor((new_llx + new_ulx) / 2.0).toInt()

        if (new_lx < diag_left_x) {
            // the intersection of the lower left and the upper left border
            // line is to the right of left x.  Ajust new_lx to diag_left_x.
            new_lx = diag_left_x
        }
        if (new_lx > new_rx || new_ly > new_uy || new_llx > new_urx || new_ulx > new_lrx) {
            return EMPTY
        }
        return IntOctagon(new_lx, new_ly, new_rx, new_uy, new_ulx, new_lrx, new_llx, new_urx)
    }

    /**
     * Checks, if this octagon is normalized.
     */
    fun is_normalized(): Boolean {
        val on = this.normalize()
        return leftX == on.leftX && bottomY == on.bottomY && rightX == on.rightX && topY == on.topY && lowerLeftDiagonalX == on.lowerLeftDiagonalX && lowerRightDiagonalX == on.lowerRightDiagonalX
                && upperLeftDiagonalX == on.upperLeftDiagonalX && upperRightDiagonalX == on.upperRightDiagonalX
    }

    override fun to_Simplex(): Simplex {
        if (is_empty()) {
            return Simplex.EMPTY
        }
        if (precalculated_to_simplex == null) {
            val line_arr = Array(8) { border_line(it) }
            val curr_simplex = Simplex(line_arr)
            precalculated_to_simplex = curr_simplex.remove_redundant_lines()
        }
        return precalculated_to_simplex!!
    }

    override fun bounding_shape(p_dirs: ShapeBoundingDirections): RegularTileShape {
        return p_dirs.bounds(this)
    }

    override fun intersects(p_other: Shape): Boolean {
        return p_other.intersects(this)
    }

    /**
     * Returns true, if p_point is contained in this octagon. Because of the parameter type FloatPoint, the function may not be exact close to the border.
     */
    override fun contains(p_point: FloatPoint): Boolean {
        if (leftX > p_point.x || bottomY > p_point.y || rightX < p_point.x || topY < p_point.y) {
            return false
        }
        val tmp_1 = p_point.x - p_point.y
        val tmp_2 = p_point.x + p_point.y
        return !(upperLeftDiagonalX > tmp_1) && !(lowerRightDiagonalX < tmp_1) && !(lowerLeftDiagonalX > tmp_2) && !(upperRightDiagonalX < tmp_2)
    }

    /**
     * Calculates the side of the point (p_x, p_y) of the border line with index p_border_line_no. The border lines are located in counterclock sense around this octagon.
     */
    fun side_of_border_line(p_x: Int, p_y: Int, p_border_line_no: Int): Side {
        val tmp = when (p_border_line_no) {
            0 -> this.bottomY - p_y
            2 -> p_x - this.rightX
            4 -> p_y - this.topY
            6 -> this.leftX - p_x
            1 -> p_x - p_y - this.lowerRightDiagonalX
            3 -> p_x + p_y - this.upperRightDiagonalX
            5 -> this.upperLeftDiagonalX + p_y - p_x
            7 -> this.lowerLeftDiagonalX - p_x - p_y
            else -> {
                FRLogger.warn("IntOctagon.side_of_border_line: p_border_line_no out of range")
                0
            }
        }
        val result: Side
        if (tmp < 0) {
            result = Side.ON_THE_LEFT
        } else if (tmp > 0) {
            result = Side.ON_THE_RIGHT
        } else {
            result = Side.COLLINEAR
        }
        return result
    }

    override fun intersection(p_other: Simplex): Simplex {
        return p_other.intersection(this)
    }

    public override fun intersection(p_other: IntOctagon): IntOctagon {
        val result = IntOctagon(
            max(leftX, p_other.leftX),
            max(bottomY, p_other.bottomY),
            min(rightX, p_other.rightX),
            min(topY, p_other.topY),
            max(upperLeftDiagonalX, p_other.upperLeftDiagonalX),
            min(lowerRightDiagonalX, p_other.lowerRightDiagonalX),
            max(lowerLeftDiagonalX, p_other.lowerLeftDiagonalX),
            min(upperRightDiagonalX, p_other.upperRightDiagonalX)
        )
        return result.normalize()
    }

    public override fun intersection(p_other: IntBox): IntOctagon {
        return intersection(p_other.to_IntOctagon())
    }

    /**
     * checks if this (normalized) octagon is contained in p_box
     */
    public override fun is_contained_in(p_box: IntBox): Boolean {
        return leftX >= p_box.ll.x && bottomY >= p_box.ll.y && rightX <= p_box.ur.x && topY <= p_box.ur.y
    }

    public override fun is_contained_in(p_other: IntOctagon): Boolean {
        return leftX >= p_other.leftX && bottomY >= p_other.bottomY && rightX <= p_other.rightX && topY <= p_other.topY && lowerLeftDiagonalX >= p_other.lowerLeftDiagonalX
                && upperLeftDiagonalX >= p_other.upperLeftDiagonalX && lowerRightDiagonalX <= p_other.lowerRightDiagonalX && upperRightDiagonalX <= p_other.upperRightDiagonalX
    }

    public override fun union(p_other: IntOctagon): IntOctagon {
        return IntOctagon(
            min(leftX, p_other.leftX),
            min(bottomY, p_other.bottomY),
            max(rightX, p_other.rightX),
            max(topY, p_other.topY),
            min(upperLeftDiagonalX, p_other.upperLeftDiagonalX),
            max(lowerRightDiagonalX, p_other.lowerRightDiagonalX),
            min(lowerLeftDiagonalX, p_other.lowerLeftDiagonalX),
            max(upperRightDiagonalX, p_other.upperRightDiagonalX)
        )
    }

    public override fun intersects(p_other: IntBox): Boolean {
        return intersects(p_other.to_IntOctagon())
    }

    /**
     * checks, if two normalized Octagons intersect.
     */
    public override fun intersects(p_other: IntOctagon): Boolean {
        val is_lx = max(p_other.leftX, this.leftX)
        val is_rx = min(p_other.rightX, this.rightX)
        if (is_lx > is_rx) {
            return false
        }

        val is_ly = max(p_other.bottomY, this.bottomY)
        val is_uy = min(p_other.topY, this.topY)
        if (is_ly > is_uy) {
            return false
        }

        val is_llx = max(p_other.lowerLeftDiagonalX, this.lowerLeftDiagonalX)
        val is_urx = min(p_other.upperRightDiagonalX, this.upperRightDiagonalX)
        if (is_llx > is_urx) {
            return false
        }

        val is_ulx = max(p_other.upperLeftDiagonalX, this.upperLeftDiagonalX)
        val is_lrx = min(p_other.lowerRightDiagonalX, this.lowerRightDiagonalX)
        return is_ulx <= is_lrx
    }

    /**
     * Returns true, if this octagon intersects with p_other and the intersection is 2-dimensional.
     */
    fun overlaps(p_other: IntOctagon): Boolean {
        val is_lx = max(p_other.leftX, this.leftX)
        val is_rx = min(p_other.rightX, this.rightX)
        if (is_lx >= is_rx) {
            return false
        }

        val is_ly = max(p_other.bottomY, this.bottomY)
        val is_uy = min(p_other.topY, this.topY)
        if (is_ly >= is_uy) {
            return false
        }

        val is_llx = max(p_other.lowerLeftDiagonalX, this.lowerLeftDiagonalX)
        val is_urx = min(p_other.upperRightDiagonalX, this.upperRightDiagonalX)
        if (is_llx >= is_urx) {
            return false
        }

        val is_ulx = max(p_other.upperLeftDiagonalX, this.upperLeftDiagonalX)
        val is_lrx = min(p_other.lowerRightDiagonalX, this.lowerRightDiagonalX)
        return is_ulx < is_lrx
    }

    override fun intersects(p_other: Simplex): Boolean {
        return p_other.intersects(this)
    }

    override fun intersects(p_other: Circle): Boolean {
        return p_other.intersects(this)
    }

    public override fun union(p_other: IntBox): IntOctagon {
        return union(p_other.to_IntOctagon())
    }

    /**
     * computes the x value of the left boundary of this Octagon at p_y
     */
    fun left_x_value(p_y: Int): Int {
        val result = max(leftX, upperLeftDiagonalX + p_y)
        return max(result, lowerLeftDiagonalX - p_y)
    }

    /**
     * computes the x value of the right boundary of this Octagon at p_y
     */
    fun right_x_value(p_y: Int): Int {
        val result = min(rightX, upperRightDiagonalX - p_y)
        return min(result, lowerRightDiagonalX + p_y)
    }

    /**
     * computes the y value of the lower boundary of this Octagon at p_x
     */
    fun lower_y_value(p_x: Int): Int {
        val result = max(bottomY, lowerLeftDiagonalX - p_x)
        return max(result, p_x - lowerRightDiagonalX)
    }

    /**
     * computes the y value of the upper boundary of this Octagon at p_x
     */
    fun upper_y_value(p_x: Int): Int {
        val result = min(topY, p_x - upperLeftDiagonalX)
        return min(result, upperRightDiagonalX - p_x)
    }

    override fun compare(p_other: RegularTileShape, p_edge_no: Int): Side {
        val result = p_other.compare(this, p_edge_no)
        return result.negate()
    }

    public override fun compare(p_other: IntOctagon, p_edge_no: Int): Side {
        val result: Side
        when (p_edge_no) {
            0 -> {
                // compare the lower edge line
                if (bottomY > p_other.bottomY) {
                    result = Side.ON_THE_LEFT
                } else if (bottomY < p_other.bottomY) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            1 -> {
                // compare the lower right edge line
                if (lowerRightDiagonalX < p_other.lowerRightDiagonalX) {
                    result = Side.ON_THE_LEFT
                } else if (lowerRightDiagonalX > p_other.lowerRightDiagonalX) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            2 -> {
                // compare the right edge line
                if (rightX < p_other.rightX) {
                    result = Side.ON_THE_LEFT
                } else if (rightX > p_other.rightX) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            3 -> {
                // compare the upper right edge line
                if (upperRightDiagonalX < p_other.upperRightDiagonalX) {
                    result = Side.ON_THE_LEFT
                } else if (upperRightDiagonalX > p_other.upperRightDiagonalX) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            4 -> {
                // compare the upper edge line
                if (topY < p_other.topY) {
                    result = Side.ON_THE_LEFT
                } else if (topY > p_other.topY) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            5 -> {
                // compare the upper left edge line
                if (upperLeftDiagonalX > p_other.upperLeftDiagonalX) {
                    result = Side.ON_THE_LEFT
                } else if (upperLeftDiagonalX < p_other.upperLeftDiagonalX) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            6 -> {
                // compare the left edge line
                if (leftX > p_other.leftX) {
                    result = Side.ON_THE_LEFT
                } else if (leftX < p_other.leftX) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            7 -> {
                // compare the lower left edge line
                if (lowerLeftDiagonalX > p_other.lowerLeftDiagonalX) {
                    result = Side.ON_THE_LEFT
                } else if (lowerLeftDiagonalX < p_other.lowerLeftDiagonalX) {
                    result = Side.ON_THE_RIGHT
                } else {
                    result = Side.COLLINEAR
                }
            }
            else -> throw IllegalArgumentException("IntBox.compare: p_edge_no out of range")
        }
        return result
    }

    public override fun compare(p_other: IntBox, p_edge_no: Int): Side {
        return compare(p_other.to_IntOctagon(), p_edge_no)
    }

    override fun border_line_index(p_line: Line): Int {
        FRLogger.warn("edge_index_of_line not yet implemented for octagons")
        return -1
    }

    /**
     * Calculates the border point of this octagon from p_point into the 45 degree direction p_dir. If this border point is not an IntPoint, the nearest outside IntPoint of the octagon is returned.
     */
    fun border_point(p_point: IntPoint, p_dir: FortyfiveDegreeDirection): IntPoint {
        var result_x: Int
        var result_y: Int
        when (p_dir) {
            FortyfiveDegreeDirection.RIGHT -> {
                result_x = min(rightX, upperRightDiagonalX - p_point.y)
                result_x = min(result_x, lowerRightDiagonalX + p_point.y)
                result_y = p_point.y
            }
            FortyfiveDegreeDirection.LEFT -> {
                result_x = max(leftX, upperLeftDiagonalX + p_point.y)
                result_x = max(result_x, lowerLeftDiagonalX - p_point.y)
                result_y = p_point.y
            }
            FortyfiveDegreeDirection.UP -> {
                result_x = p_point.x
                result_y = min(topY, p_point.x - upperLeftDiagonalX)
                result_y = min(result_y, upperRightDiagonalX - p_point.x)
            }
            FortyfiveDegreeDirection.DOWN -> {
                result_x = p_point.x
                result_y = max(bottomY, lowerLeftDiagonalX - p_point.x)
                result_y = max(result_y, p_point.x - lowerRightDiagonalX)
            }
            FortyfiveDegreeDirection.RIGHT45 -> {
                result_x = ceil(0.5 * (p_point.x - p_point.y + upperRightDiagonalX)).toInt()
                result_x = min(result_x, rightX)
                result_x = min(result_x, p_point.x - p_point.y + topY)
                result_y = p_point.y - p_point.x + result_x
            }
            FortyfiveDegreeDirection.UP45 -> {
                result_x = floor(0.5 * (p_point.x + p_point.y + upperLeftDiagonalX)).toInt()
                result_x = max(result_x, leftX)
                result_x = max(result_x, p_point.x + p_point.y - topY)
                result_y = p_point.y + p_point.x - result_x
            }
            FortyfiveDegreeDirection.LEFT45 -> {
                result_x = floor(0.5 * (p_point.x - p_point.y + lowerLeftDiagonalX)).toInt()
                result_x = max(result_x, leftX)
                result_x = max(result_x, p_point.x - p_point.y + bottomY)
                result_y = p_point.y - p_point.x + result_x
            }
            FortyfiveDegreeDirection.DOWN45 -> {
                result_x = ceil(0.5 * (p_point.x + p_point.y + lowerRightDiagonalX)).toInt()
                result_x = min(result_x, rightX)
                result_x = min(result_x, p_point.x + p_point.y - bottomY)
                result_y = p_point.y + p_point.x - result_x
            }
        }
        return IntPoint(result_x, result_y)
    }

    /**
     * Calculates the sorted p_max_result_points nearest points on the border of this octagon in the 45-degree directions. p_point is assumed to be located in the interior of this octagon.
     */
    fun nearest_border_projections(p_point: IntPoint, p_max_result_points: Int): Array<IntPoint> {
        var max_result_points = p_max_result_points
        if (!this.contains(p_point.to_float()) || max_result_points <= 0) {
            return emptyArray()
        }
        max_result_points = min(max_result_points, 8)
        val result = Array(max_result_points) { IntPoint(0, 0) }
        val min_dist = DoubleArray(max_result_points) { Double.MAX_VALUE }
        val inside_point = p_point.to_float()
        for (curr_dir in FortyfiveDegreeDirection.values()) {
            val curr_border_point = border_point(p_point, curr_dir)
            val curr_dist = inside_point.distance_square(curr_border_point.to_float())
            for (i in 0 until max_result_points) {
                if (curr_dist < min_dist[i]) {
                    for (k in max_result_points - 1 downTo i + 1) {
                        min_dist[k] = min_dist[k - 1]
                        result[k] = result[k - 1]
                    }
                    min_dist[i] = curr_dist
                    result[i] = curr_border_point
                    break
                }
            }
        }
        return result
    }

    fun border_line_side_of(p_point: FloatPoint, p_line_no: Int, p_tolerance: Double): Side {
        return when (p_line_no) {
            0 -> {
                if (p_point.y > this.bottomY + p_tolerance) {
                    Side.ON_THE_RIGHT
                } else if (p_point.y < this.bottomY - p_tolerance) {
                    Side.ON_THE_LEFT
                } else {
                    Side.COLLINEAR
                }
            }
            2 -> {
                if (p_point.x < this.rightX - p_tolerance) {
                    Side.ON_THE_RIGHT
                } else if (p_point.x > this.rightX + p_tolerance) {
                    Side.ON_THE_LEFT
                } else {
                    Side.COLLINEAR
                }
            }
            4 -> {
                if (p_point.y < this.topY - p_tolerance) {
                    Side.ON_THE_RIGHT
                } else if (p_point.y > this.topY + p_tolerance) {
                    Side.ON_THE_LEFT
                } else {
                    Side.COLLINEAR
                }
            }
            6 -> {
                if (p_point.x > this.leftX + p_tolerance) {
                    Side.ON_THE_RIGHT
                } else if (p_point.x < this.leftX - p_tolerance) {
                    Side.ON_THE_LEFT
                } else {
                    Side.COLLINEAR
                }
            }
            1 -> {
                val tmp = p_point.y - p_point.x + lowerRightDiagonalX
                if (tmp > p_tolerance) {
                    // the p_point is above the lower right border line of this octagon
                    Side.ON_THE_RIGHT
                } else if (tmp < -p_tolerance) {
                    // the p_point is below the lower right border line of this octagon
                    Side.ON_THE_LEFT
                } else {
                    Side.COLLINEAR
                }
            }
            3 -> {
                val tmp = p_point.x + p_point.y - upperRightDiagonalX
                if (tmp < -p_tolerance) {
                    // the p_point is below the upper right border line of this octagon
                    Side.ON_THE_RIGHT
                } else if (tmp > p_tolerance) {
                    // the p_point is above the upper right border line of this octagon
                    Side.ON_THE_LEFT
                } else {
                    Side.COLLINEAR
                }
            }
            5 -> {
                val tmp = p_point.y - p_point.x + upperLeftDiagonalX
                if (tmp < -p_tolerance) {
                    // the p_point is below the upper left border line of this octagon
                    Side.ON_THE_RIGHT
                } else if (tmp > p_tolerance) {
                    // the p_point is above the upper left border line of this octagon
                    Side.ON_THE_LEFT
                } else {
                    Side.COLLINEAR
                }
            }
            7 -> {
                val tmp = p_point.x + p_point.y - lowerLeftDiagonalX
                if (tmp > p_tolerance) {
                    // the p_point is above the lower left border line of this octagon
                    Side.ON_THE_RIGHT
                } else if (tmp < -p_tolerance) {
                    // the p_point is below the lower left border line of this octagon
                    Side.ON_THE_LEFT
                } else {
                    Side.COLLINEAR
                }
            }
            else -> {
                FRLogger.warn("IntOctagon.border_line_side_of: p_line_no out of range")
                Side.COLLINEAR
            }
        }
    }

    /**
     * Checks, if this octagon can be converted to an IntBox.
     */
    override fun is_IntBox(): Boolean {
        if (lowerLeftDiagonalX != leftX + bottomY) {
            return false
        }
        if (lowerRightDiagonalX != rightX - bottomY) {
            return false
        }
        if (upperRightDiagonalX != rightX + topY) {
            return false
        }
        return upperLeftDiagonalX == leftX - topY
    }

    override fun simplify(): TileShape {
        if (this.is_IntBox()) {
            return this.bounding_box()
        }
        return this
    }

    override fun cutout(p_shape: TileShape): Array<TileShape> {
        return p_shape.cutout_from(this)
    }

    /**
     * Divide p_d minus this octagon into 8 convex pieces, from which 4 have cut off a corner.
     */
    public override fun cutout_from(p_d: IntBox): Array<IntOctagon> {
        val c = this.intersection(p_d)

        if (this.is_empty() || c.dimension() < this.dimension()) {
            // there is only an overlap at the border
            val result = Array(1) { p_d.to_IntOctagon() }
            return result
        }

        val boxes = Array<IntBox?>(4) { null }

        // construct left box

        boxes[0] = IntBox(p_d.ll.x, c.lowerLeftDiagonalX - c.leftX, c.leftX, c.leftX - c.upperLeftDiagonalX)

        // construct right box

        boxes[1] = IntBox(c.rightX, c.rightX - c.lowerRightDiagonalX, p_d.ur.x, c.upperRightDiagonalX - c.rightX)

        // construct lower box

        boxes[2] = IntBox(c.lowerLeftDiagonalX - c.bottomY, p_d.ll.y, c.lowerRightDiagonalX + c.bottomY, c.bottomY)

        // construct upper box

        boxes[3] = IntBox(c.upperLeftDiagonalX + c.topY, c.topY, c.upperRightDiagonalX - c.topY, p_d.ur.y)

        val octagons = Array(4) { EMPTY }

        // construct upper left octagon

        var curr_oct = IntOctagon(p_d.ll.x, boxes[0]!!.ur.y, boxes[3]!!.ll.x, p_d.ur.y, -Limits.CRIT_INT, c.upperLeftDiagonalX, -Limits.CRIT_INT, Limits.CRIT_INT)
        octagons[0] = curr_oct.normalize()

        // construct lower left octagon

        curr_oct = IntOctagon(p_d.ll.x, p_d.ll.y, boxes[2]!!.ll.x, boxes[0]!!.ll.y, -Limits.CRIT_INT, Limits.CRIT_INT, -Limits.CRIT_INT, c.lowerLeftDiagonalX)
        octagons[1] = curr_oct.normalize()

        // construct lower right octagon

        curr_oct = IntOctagon(boxes[2]!!.ur.x, p_d.ll.y, p_d.ur.x, boxes[1]!!.ll.y, c.lowerRightDiagonalX, Limits.CRIT_INT, -Limits.CRIT_INT, Limits.CRIT_INT)
        octagons[2] = curr_oct.normalize()

        // construct upper right octagon

        curr_oct = IntOctagon(boxes[3]!!.ur.x, boxes[1]!!.ur.y, p_d.ur.x, p_d.ur.y, -Limits.CRIT_INT, Limits.CRIT_INT, c.upperRightDiagonalX, Limits.CRIT_INT)
        octagons[3] = curr_oct.normalize()

        // optimise the result to minimum cumulative circumference

        var b = boxes[0]!!
        var o = octagons[0]
        if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
            // switch the horizontal upper left divide line to vertical

            boxes[0] = IntBox(b.ll.x, b.ll.y, b.ur.x, o.topY)
            curr_oct = IntOctagon(b.ur.x, o.bottomY, o.rightX, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX)
            octagons[0] = curr_oct.normalize()
        }

        b = boxes[3]!!
        o = octagons[0]
        if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
            // switch the vertical upper left divide line to horizontal

            boxes[3] = IntBox(o.leftX, b.ll.y, b.ur.x, b.ur.y)
            curr_oct = IntOctagon(o.leftX, o.bottomY, o.rightX, b.ll.y, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX)
            octagons[0] = curr_oct.normalize()
        }
        b = boxes[3]!!
        o = octagons[3]
        if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
            // switch the vertical upper right divide line to horizontal

            boxes[3] = IntBox(b.ll.x, b.ll.y, o.rightX, b.ur.y)
            curr_oct = IntOctagon(o.leftX, o.bottomY, o.rightX, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX)
            octagons[3] = curr_oct.normalize()
        }
        b = boxes[1]!!
        o = octagons[3]
        if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
            // switch the horizontal upper right divide line to vertical

            boxes[1] = IntBox(b.ll.x, b.ll.y, b.ur.x, o.topY)
            curr_oct = IntOctagon(o.leftX, o.bottomY, b.ll.x, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX)
            octagons[3] = curr_oct.normalize()
        }
        b = boxes[1]!!
        o = octagons[2]
        if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
            // switch the horizontal lower right divide line to vertical

            boxes[1] = IntBox(b.ll.x, o.bottomY, b.ur.x, b.ur.y)
            curr_oct = IntOctagon(o.leftX, o.bottomY, b.ll.x, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX)
            octagons[2] = curr_oct.normalize()
        }
        b = boxes[2]!!
        o = octagons[2]
        if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
            // switch the vertical lower right divide line to horizontal

            boxes[2] = IntBox(b.ll.x, b.ll.y, o.rightX, b.ur.y)
            curr_oct = IntOctagon(o.leftX, b.ur.y, o.rightX, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX)
            octagons[2] = curr_oct.normalize()
        }
        b = boxes[2]!!
        o = octagons[1]
        if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
            // switch the vertical lower  left divide line to horizontal

            boxes[2] = IntBox(o.leftX, b.ll.y, b.ur.x, b.ur.y)
            curr_oct = IntOctagon(o.leftX, b.ur.y, o.rightX, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX)
            octagons[1] = curr_oct.normalize()
        }
        b = boxes[0]!!
        o = octagons[1]
        if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
            // switch the horizontal lower left divide line to vertical
            boxes[0] = IntBox(b.ll.x, o.bottomY, b.ur.x, b.ur.y)
            curr_oct = IntOctagon(b.ur.x, o.bottomY, o.rightX, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX)
            octagons[1] = curr_oct.normalize()
        }

        val result = Array(8) { EMPTY }

        // add the 4 boxes to the result
        for (i in 0 until 4) {
            result[i] = boxes[i]!!.to_IntOctagon()
        }

        // add the 4 octagons to the result
        System.arraycopy(octagons, 0, result, 4, 4)
        return result
    }

    /**
     * Divide p_divide_octagon minus cut_octagon into 8 convex pieces without sharp angles.
     */
    public override fun cutout_from(p_d: IntOctagon): Array<IntOctagon> {
        val c = this.intersection(p_d)

        if (this.is_empty() || c.dimension() < this.dimension()) {
            // there is only an overlap at the border
            val result = Array(1) { p_d }
            return result
        }

        val result = Array(8) { EMPTY }

        var tmp = c.lowerLeftDiagonalX - c.leftX

        result[0] = IntOctagon(p_d.leftX, tmp, c.leftX, c.leftX - c.upperLeftDiagonalX, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX)

        var tmp2 = c.lowerLeftDiagonalX - c.bottomY

        result[1] = IntOctagon(p_d.leftX, p_d.bottomY, tmp2, tmp, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, c.lowerLeftDiagonalX)

        tmp = c.lowerRightDiagonalX + c.bottomY

        result[2] = IntOctagon(tmp2, p_d.bottomY, tmp, c.bottomY, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX)

        tmp2 = c.rightX - c.lowerRightDiagonalX

        result[3] = IntOctagon(tmp, p_d.bottomY, p_d.rightX, tmp2, c.lowerRightDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX)

        tmp = c.upperRightDiagonalX - c.rightX

        result[4] = IntOctagon(c.rightX, tmp2, p_d.rightX, tmp, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX)

        tmp2 = c.upperRightDiagonalX - c.topY

        result[5] = IntOctagon(tmp2, tmp, p_d.rightX, p_d.topY, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, c.upperRightDiagonalX, p_d.upperRightDiagonalX)

        tmp = c.upperLeftDiagonalX + c.topY

        result[6] = IntOctagon(tmp, c.topY, tmp2, p_d.topY, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX)

        tmp2 = c.leftX - c.upperLeftDiagonalX

        result[7] = IntOctagon(p_d.leftX, tmp2, tmp, p_d.topY, p_d.upperLeftDiagonalX, c.upperLeftDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX)

        for (i in 0 until 8) {
            result[i] = result[i].normalize()
        }

        var curr_1 = result[0]
        var curr_2 = result[7]

        if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_1.rightX - curr_1.left_x_value(curr_1.topY) > curr_2.upper_y_value(curr_1.rightX) - curr_2.bottomY) {
            // switch the horizontal upper left divide line to vertical
            curr_1 = IntOctagon(
                min(curr_1.leftX, curr_2.leftX),
                curr_1.bottomY,
                curr_1.rightX,
                curr_2.topY,
                curr_2.upperLeftDiagonalX,
                curr_1.lowerRightDiagonalX,
                curr_1.lowerLeftDiagonalX,
                curr_2.upperRightDiagonalX
            )

            curr_2 = IntOctagon(curr_1.rightX, curr_2.bottomY, curr_2.rightX, curr_2.topY, curr_2.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_2.upperRightDiagonalX)

            result[0] = curr_1.normalize()
            result[7] = curr_2.normalize()
        }
        curr_1 = result[7]
        curr_2 = result[6]
        if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_2.upper_y_value(curr_1.rightX) - curr_2.bottomY > curr_1.rightX - curr_1.left_x_value(curr_2.bottomY)) {
            // switch the vertical upper left divide line to horizontal
            curr_2 = IntOctagon(
                curr_1.leftX,
                curr_2.bottomY,
                curr_2.rightX,
                max(curr_2.topY, curr_1.topY),
                curr_1.upperLeftDiagonalX,
                curr_2.lowerRightDiagonalX,
                curr_1.lowerLeftDiagonalX,
                curr_2.upperRightDiagonalX
            )

            curr_1 = IntOctagon(curr_1.leftX, curr_1.bottomY, curr_1.rightX, curr_2.bottomY, curr_1.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_1.upperRightDiagonalX)

            result[7] = curr_1.normalize()
            result[6] = curr_2.normalize()
        }
        curr_1 = result[6]
        curr_2 = result[5]
        if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_2.upper_y_value(curr_1.rightX) - curr_1.bottomY > curr_2.right_x_value(curr_1.bottomY) - curr_2.leftX) {
            // switch the vertical upper right divide line to horizontal
            curr_1 = IntOctagon(
                curr_1.leftX,
                curr_1.bottomY,
                curr_2.rightX,
                max(curr_2.topY, curr_1.topY),
                curr_1.upperLeftDiagonalX,
                curr_2.lowerRightDiagonalX,
                curr_1.lowerLeftDiagonalX,
                curr_2.upperRightDiagonalX
            )

            curr_2 = IntOctagon(curr_2.leftX, curr_2.bottomY, curr_2.rightX, curr_1.bottomY, curr_2.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_2.upperRightDiagonalX)

            result[6] = curr_1.normalize()
            result[5] = curr_2.normalize()
        }
        curr_1 = result[5]
        curr_2 = result[4]
        if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_2.right_x_value(curr_2.topY) - curr_2.leftX > curr_1.upper_y_value(curr_2.leftX) - curr_2.topY) {
            // switch the horizontal upper right divide line to vertical
            curr_2 = IntOctagon(
                curr_2.leftX,
                curr_2.bottomY,
                max(curr_2.rightX, curr_1.rightX),
                curr_1.topY,
                curr_1.upperLeftDiagonalX,
                curr_2.lowerRightDiagonalX,
                curr_2.lowerLeftDiagonalX,
                curr_1.upperRightDiagonalX
            )

            curr_1 = IntOctagon(curr_1.leftX, curr_1.bottomY, curr_2.leftX, curr_1.topY, curr_1.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_1.upperRightDiagonalX)

            result[5] = curr_1.normalize()
            result[4] = curr_2.normalize()
        }
        curr_1 = result[4]
        curr_2 = result[3]
        if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_1.right_x_value(curr_1.bottomY) - curr_1.leftX > curr_1.bottomY - curr_2.lower_y_value(curr_1.leftX)) {
            // switch the horizontal lower right divide line to vertical
            curr_1 = IntOctagon(
                curr_1.leftX,
                curr_2.bottomY,
                max(curr_2.rightX, curr_1.rightX),
                curr_1.topY,
                curr_1.upperLeftDiagonalX,
                curr_2.lowerRightDiagonalX,
                curr_2.lowerLeftDiagonalX,
                curr_1.upperRightDiagonalX
            )

            curr_2 = IntOctagon(curr_2.leftX, curr_2.bottomY, curr_1.leftX, curr_2.topY, curr_2.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_2.upperRightDiagonalX)

            result[4] = curr_1.normalize()
            result[3] = curr_2.normalize()
        }

        curr_1 = result[3]
        curr_2 = result[2]

        if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_2.topY - curr_2.lower_y_value(curr_2.rightX) > curr_1.right_x_value(curr_2.topY) - curr_2.rightX) {
            // switch the vertical lower right divide line to horizontal
            curr_2 = IntOctagon(
                curr_2.leftX,
                min(curr_1.bottomY, curr_2.bottomY),
                curr_1.rightX,
                curr_2.topY,
                curr_2.upperLeftDiagonalX,
                curr_1.lowerRightDiagonalX,
                curr_2.lowerLeftDiagonalX,
                curr_1.upperRightDiagonalX
            )

            curr_1 = IntOctagon(curr_1.leftX, curr_2.topY, curr_1.rightX, curr_1.topY, curr_1.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_1.upperRightDiagonalX)

            result[3] = curr_1.normalize()
            result[2] = curr_2.normalize()
        }

        curr_1 = result[2]
        curr_2 = result[1]

        if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_1.topY - curr_1.lower_y_value(curr_1.leftX) > curr_1.leftX - curr_2.left_x_value(curr_1.topY)) {
            // switch the vertical lower left divide line to horizontal
            curr_1 = IntOctagon(
                curr_2.leftX,
                min(curr_1.bottomY, curr_2.bottomY),
                curr_1.rightX,
                curr_1.topY,
                curr_2.upperLeftDiagonalX,
                curr_1.lowerRightDiagonalX,
                curr_2.lowerLeftDiagonalX,
                curr_1.upperRightDiagonalX
            )

            curr_2 = IntOctagon(curr_2.leftX, curr_1.topY, curr_2.rightX, curr_2.topY, curr_2.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_2.upperRightDiagonalX)

            result[2] = curr_1.normalize()
            result[1] = curr_2.normalize()
        }

        curr_1 = result[1]
        curr_2 = result[0]

        if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_2.rightX - curr_2.left_x_value(curr_2.bottomY) > curr_2.bottomY - curr_1.lower_y_value(curr_2.rightX)) {
            // switch the horizontal lower left divide line to vertical
            curr_2 = IntOctagon(
                min(curr_2.leftX, curr_1.leftX),
                curr_1.bottomY,
                curr_2.rightX,
                curr_2.topY,
                curr_2.upperLeftDiagonalX,
                curr_1.lowerRightDiagonalX,
                curr_1.lowerLeftDiagonalX,
                curr_2.upperRightDiagonalX
            )

            curr_1 = IntOctagon(curr_2.rightX, curr_1.bottomY, curr_1.rightX, curr_1.topY, curr_1.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_1.upperRightDiagonalX)

            result[1] = curr_1.normalize()
            result[0] = curr_2.normalize()
        }

        return result
    }

    public override fun cutout_from(p_simplex: Simplex): Array<Simplex> {
        return this.to_Simplex().cutout_from(p_simplex)
    }

    override fun toString(): String {
        return "IntOctagon(lx=$leftX, ly=$bottomY, rx=$rightX, uy=$topY, ulx=$upperLeftDiagonalX, lrx=$lowerRightDiagonalX, llx=$lowerLeftDiagonalX, urx=$upperRightDiagonalX)"
    }

    companion object {
        /**
         * Reusable instance of an empty octagon.
         */
        @JvmField
        val EMPTY = IntOctagon(
            Limits.CRIT_INT,
            Limits.CRIT_INT,
            -Limits.CRIT_INT,
            -Limits.CRIT_INT,
            Limits.CRIT_INT,
            -Limits.CRIT_INT,
            Limits.CRIT_INT,
            -Limits.CRIT_INT
        )
    }
}
