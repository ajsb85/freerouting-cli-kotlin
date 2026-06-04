package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.util.Arrays
import java.util.LinkedList

/**
 * Abstract class defining functionality for convex shapes, whose borders
 * consists of straight lines.
 */
abstract class TileShape : PolylineShape(), ConvexShape, Serializable {

    companion object {
        @JvmField
        val EMPTY: TileShape = Simplex.EMPTY
        /**
         * creates a Simplex as intersection of the halfplanes defined by an array of
         * directed lines
         */
        @JvmStatic
        fun get_instance(p_line_arr: Array<Line>): TileShape {
            val result = Simplex.get_instance(p_line_arr)
            return result.simplify()
        }

        /**
         * Creates a TileShape from a Point array, who forms the corners of the shape of
         * a convex polygon. May work only for IntPoints.
         */
        @JvmStatic
        fun get_instance(p_convex_polygon: Array<Point>): TileShape {
            val lineArr = Array(p_convex_polygon.size) { j ->
                if (j < p_convex_polygon.size - 1) {
                    Line(p_convex_polygon[j], p_convex_polygon[j + 1])
                } else {
                    Line(p_convex_polygon[p_convex_polygon.size - 1], p_convex_polygon[0])
                }
            }
            return get_instance(lineArr)
        }

        /**
         * creates a half_plane from a directed line
         */
        @JvmStatic
        fun get_instance(p_line: Line): TileShape {
            val lines = arrayOf(p_line)
            return Simplex.get_instance(lines)
        }

        /**
         * Creates a normalized IntOctagon from the input values. For the meaning of the
         * parameter shortcuts see class IntOctagon.
         */
        @JvmStatic
        fun get_instance(
            p_lx: Int, p_ly: Int, p_rx: Int, p_uy: Int, p_ulx: Int, p_lrx: Int, p_llx: Int,
            p_urx: Int
        ): IntOctagon {
            val oct = IntOctagon(p_lx, p_ly, p_rx, p_uy, p_ulx, p_lrx, p_llx, p_urx)
            return oct.normalize()
        }

        /**
         * creates a boxlike convex shape
         */
        @JvmStatic
        fun get_instance(
            p_lower_left_x: Int, p_lower_left_y: Int, p_upper_right_x: Int,
            p_upper_right_y: Int
        ): IntOctagon {
            val box = IntBox(p_lower_left_x, p_lower_left_y, p_upper_right_x, p_upper_right_y)
            return box.to_IntOctagon()
        }

        /**
         * creates the smallest IntOctagon containing p_point
         */
        @JvmStatic
        fun get_instance(p_point: Point): IntBox {
            return p_point.surrounding_box()
        }
    }

    /**
     * Tries to simplify the result shape to a simpler shape. Simplifying always in
     * the intersection function may cause performance problems.
     */
    fun intersection_with_simplify(p_other: TileShape): TileShape {
        val result = this.intersection(p_other)
        return result.simplify()
    }

    /**
     * Converts the physical instance of this shape to a simpler physical instance,
     * if possible.
     */
    abstract fun simplify(): TileShape

    /**
     * Returns a unique ID for this shape for deterministic tie-breaking.
     */
    abstract fun get_id_no(): Int

    /**
     * checks if this TileShape is an IntBox or can be converted into an IntBox
     */
    abstract fun is_IntBox(): Boolean

    /**
     * checks if this TileShape is an IntOctagon or can be converted into an
     * IntOctagon
     */
    abstract fun is_IntOctagon(): Boolean

    /**
     * Returns the intersection of this shape with p_other
     */
    abstract fun intersection(p_other: TileShape): TileShape

    /**
     * Returns the p_no-th edge line of this shape for p_no between 0 and
     * edge_line_count() - 1. The edge lines are sorted in counterclock sense around
     * the shape starting with the edge with the smallest
     * direction.
     */
    abstract override fun border_line(p_no: Int): Line

    /**
     * if p_line is a borderline of this shape the number of that edge is returned,
     * otherwise -1
     */
    abstract fun border_line_index(p_line: Line): Int

    /**
     * Converts the internal representation of this TieShape to a Simplex
     */
    abstract fun to_Simplex(): Simplex

    /**
     * Returns the content of the area of the shape. If the shape is unbounded,
     * Double.MAX_VALUE is returned.
     */
    override fun area(): Double {
        if (!is_bounded()) {
            return Double.MAX_VALUE
        }

        if (dimension() < 2) {
            return 0.0
        }
        // calculate half of the absolute value of
        // x0 (y1 - yn-1) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ xn-1( y0 - yn-2)
        // where xi, yi are the coordinates of the i-th corner of this TileShape.

        var result = 0.0
        val cornerCount = border_line_count()
        var prevCorner = corner_approx(cornerCount - 2)
        var currCorner = corner_approx(cornerCount - 1)
        for (i in 0 until cornerCount) {
            val nextCorner = corner_approx(i)
            result += currCorner.x * (nextCorner.y - prevCorner.y)
            prevCorner = currCorner
            currCorner = nextCorner
        }
        result = 0.5 * Math.abs(result)
        return result
    }

    /**
     * Returns true, if p_point is not contained in the inside or the edge of the
     * shape
     */
    override fun is_outside(p_point: Point): Boolean {
        val lineCount = border_line_count()
        if (lineCount == 0) {
            return true
        }
        for (i in 0 until lineCount) {
            if (border_line(i).side_of(p_point) == Side.ON_THE_LEFT) {
                return true
            }
        }
        return false
    }

    override fun contains(p_point: Point): Boolean {
        return !is_outside(p_point)
    }

    /**
     * Returns true, if p_point is contained in this shape, but not on an edge line
     */
    override fun contains_inside(p_point: Point): Boolean {
        val lineCount = border_line_count()
        if (lineCount == 0) {
            return false
        }
        for (i in 0 until lineCount) {
            if (border_line(i).side_of(p_point) != Side.ON_THE_RIGHT) {
                return false
            }
        }
        return true
    }

    /**
     * Returns true, if p_point is contained in this shape.
     */
    override fun contains(p_point: FloatPoint): Boolean {
        return contains(p_point, 0.0)
    }

    /**
     * Returns true, if p_point is contained in this shape with tolerance
     * p_tolerance. p_tolerance is used when determing, if a point is on the left
     * side of a border line. It is used there in
     * calculating a determinant and is not the distance of p_point to the border.
     */
    fun contains(p_point: FloatPoint, p_tolerance: Double): Boolean {
        val lineCount = border_line_count()
        if (lineCount == 0) {
            return false
        }
        for (i in 0 until lineCount) {
            if (border_line(i).side_of(p_point, p_tolerance) != Side.ON_THE_RIGHT) {
                return false
            }
        }
        return true
    }

    /**
     * Returns Side.COLLINEAR if p_point is on the border of this shape with
     * tolerance p_tolerance. p_tolerance is used when determing, if a point is on
     * the right side of a border line. It is used there
     * in calculating a determinant and is not the distance of p_point to the
     * border. Otherwise, the function returns Side.ON_THE_LEFT if p_point is
     * outside of this shape, and Side.ON_THE_RIGHT if
     * p_point is inside this shape.
     */
    fun side_of_border(p_point: FloatPoint, p_tolerance: Double): Side {
        val lineCount = border_line_count()
        if (lineCount == 0) {
            return Side.COLLINEAR
        }
        var result = Side.ON_THE_RIGHT // point is inside
        for (i in 0 until lineCount) {
            val currSide = border_line(i).side_of(p_point, p_tolerance)
            if (currSide == Side.ON_THE_LEFT) {
                return Side.ON_THE_LEFT // point is outside
            } else if (currSide == Side.COLLINEAR) {
                result = currSide
            }
        }
        return result
    }

    /**
     * If p_point lies on the border of this shape, the number of the edge line
     * segment containing p_point is returned, otherwise -1 is returned.
     */
    fun contains_on_border_line_no(p_point: Point): Int {
        val lineCount = border_line_count()
        if (lineCount == 0) {
            return -1
        }
        var containingLineNo = -1
        for (i in 0 until lineCount) {
            val sideOf = border_line(i).side_of(p_point)
            if (sideOf == Side.ON_THE_LEFT) {
                // p_point outside the convex shape
                return -1
            }
            if (sideOf == Side.COLLINEAR) {
                containingLineNo = i
            }
        }
        return containingLineNo
    }

    /**
     * Returns true, if p_point lies exact on the boundary of the shape
     */
    override fun contains_on_border(p_point: Point): Boolean {
        return contains_on_border_line_no(p_point) >= 0
    }

    /**
     * Returns true, if this shape contains p_other completely. THere may be some
     * numerical inaccuracy.
     */
    fun contains_approx(p_other: TileShape): Boolean {
        val corners = p_other.corner_approx_arr()
        for (currCorner in corners) {
            if (!this.contains(currCorner)) {
                return false
            }
        }
        return true
    }

    /**
     * Returns true, if this shape contains p_other completely.
     */
    fun contains(p_other: TileShape): Boolean {
        for (i in 0 until p_other.border_line_count()) {
            if (!this.contains(p_other.corner(i))) {
                return false
            }
        }
        return true
    }

    /**
     * Returns the distance between p_point and its nearest point on the shape. 0,
     * if p_point is contained in this shape
     */
    override fun distance(p_point: FloatPoint): Double {
        val nearestPoint = nearest_point_approx(p_point)
        return nearestPoint.distance(p_point)
    }

    /**
     * Returns the distance between p_point and its nearest point on the edge of the
     * shape.
     */
    override fun border_distance(p_point: FloatPoint): Double {
        val nearestPoint = nearest_border_point_approx(p_point)
        return nearestPoint.distance(p_point)
    }

    override fun smallest_radius(): Double {
        return border_distance(centre_of_gravity())
    }

    /**
     * Returns the point in this shape, which has the smallest distance to
     * p_from_point. p_from_point, if that point is contained in this shape
     */
    fun nearest_point(p_from_point: Point): Point {
        if (!is_outside(p_from_point)) {
            return p_from_point
        }
        return nearest_border_point(p_from_point) ?: p_from_point
    }

    override fun nearest_point_approx(p_from_point: FloatPoint): FloatPoint {
        if (this.contains(p_from_point)) {
            return p_from_point
        }
        return nearest_border_point_approx(p_from_point)
    }

    /**
     * Returns the nearest point to p_from_point on the edge of the shape
     */
    fun nearest_border_point(p_from_point: Point): Point? {
        val lineCount = border_line_count()
        if (lineCount == 0) {
            return null
        }
        val fromPointF = p_from_point.to_float()
        if (lineCount == 1) {
            return border_line(0).perpendicular_projection(p_from_point)
        }
        var minDist = Double.MAX_VALUE
        var minDistInd = 0

        // calculate the distance to the nearest corner first
        for (i in 0 until lineCount) {
            val currCornerF = corner_approx(i)
            val currDist = currCornerF.distance_square(fromPointF)
            if (currDist < minDist) {
                minDist = currDist
                minDistInd = i
            }
        }

        var nearestPoint = corner(minDistInd)

        var prevInd = lineCount - 2
        var currInd = lineCount - 1

        for (nextInd in 0 until lineCount) {
            val projection = border_line(currInd).perpendicular_projection(p_from_point)
            val leftOk = !corner_is_bounded(currInd) || border_line(prevInd).side_of(projection) == Side.ON_THE_RIGHT
            val rightOk = !corner_is_bounded(nextInd) || border_line(nextInd).side_of(projection) == Side.ON_THE_RIGHT
            if (leftOk && rightOk) {
                val projectionF = projection.to_float()
                val currDist = projectionF.distance_square(fromPointF)
                if (currDist < minDist) {
                    minDist = currDist
                    nearestPoint = projection
                }
            }
            prevInd = currInd
            currInd = nextInd
        }
        return nearestPoint
    }

    /**
     * Returns an approximation of the nearest point to p_from_point on the border
     * of this shape
     */
    fun nearest_border_point_approx(p_from_point: FloatPoint): FloatPoint {
        val nearestPoints = nearest_border_points_approx(p_from_point, 1)
        if (nearestPoints.isEmpty()) {
            return FloatPoint.ZERO // Fallback, shouldn't happen for valid shapes
        }
        return nearestPoints[0]
    }

    /**
     * Returns an approximation of the p_count nearest points to p_from_point on the
     * border of this shape. The result points must be located on different border
     * lines and are sorted in ascending order
     * (the nearest point comes first).
     */
    fun nearest_border_points_approx(p_from_point: FloatPoint, p_count: Int): Array<FloatPoint> {
        if (p_count <= 0) {
            return emptyArray()
        }
        val lineCount = border_line_count()
        val resultCount = Math.min(p_count, lineCount)
        if (lineCount == 0) {
            return emptyArray()
        }
        if (lineCount == 1) {
            return arrayOf(p_from_point.projection_approx(border_line(0)))
        }
        if (this.dimension() == 0) {
            return arrayOf(corner_approx(0))
        }
        val nearestPoints = Array<FloatPoint?>(resultCount) { null }
        val minDists = DoubleArray(resultCount) { Double.MAX_VALUE }

        // calculate the distances to the nearest corners first
        for (i in 0 until lineCount) {
            if (corner_is_bounded(i)) {
                val currCorner = corner_approx(i)
                val currDist = currCorner.distance_square(p_from_point)
                for (j in 0 until resultCount) {
                    if (currDist < minDists[j]) {
                        for (k in resultCount - 1 downTo j + 1) {
                            minDists[k] = minDists[k - 1]
                            nearestPoints[k] = nearestPoints[k - 1]
                        }
                        minDists[j] = currDist
                        nearestPoints[j] = currCorner
                        break
                    }
                }
            }
        }

        var prevInd = lineCount - 2
        var currInd = lineCount - 1

        for (nextInd in 0 until lineCount) {
            val projection = p_from_point.projection_approx(border_line(currInd))
            val leftOk = !corner_is_bounded(currInd) || border_line(prevInd).side_of(projection) == Side.ON_THE_RIGHT
            val rightOk = !corner_is_bounded(nextInd) || border_line(nextInd).side_of(projection) == Side.ON_THE_RIGHT
            if (leftOk && rightOk) {
                val currDist = projection.distance_square(p_from_point)
                for (j in 0 until resultCount) {
                    if (currDist < minDists[j]) {
                        for (k in resultCount - 1 downTo j + 1) {
                            minDists[k] = minDists[k - 1]
                            nearestPoints[k] = nearestPoints[k - 1]
                        }
                        minDists[j] = currDist
                        nearestPoints[j] = projection
                        break
                    }
                }
            }
            prevInd = currInd
            currInd = nextInd
        }
        @Suppress("UNCHECKED_CAST")
        return nearestPoints.filterNotNull().toTypedArray()
    }

    /**
     * Returns the number of the nearest corner of the shape to p_from_point
     */
    fun index_of_nearest_corner(p_from_point: Point): Int {
        val fromPointF = p_from_point.to_float()
        var result = 0
        val cornerCount = border_line_count()
        var minDist = Double.MAX_VALUE
        for (i in 0 until cornerCount) {
            val currDist = corner_approx(i).distance(fromPointF)
            if (currDist < minDist) {
                minDist = currDist
                result = i
            }
        }
        return result
    }

    /**
     * Returns a line segment consisting of an approximations of the corners with
     * index 0 and corner_count / 2.
     */
    fun diagonal_corner_segment(): FloatLine? {
        if (this.is_empty()) {
            return null
        }
        val firstCorner = this.corner_approx(0)
        val lastCorner = this.corner_approx(this.border_line_count() / 2)
        return FloatLine(firstCorner, lastCorner)
    }

    /**
     * Returns an approximation of the p_count nearest relative outside locations of
     * p_shape in the direction of different border lines of this shape. These
     * relative locations are sorted in ascending
     * order (the shortest comes first).
     */
    fun nearest_relative_outside_locations(p_shape: TileShape, p_count: Int): Array<FloatPoint> {
        val lineCount = border_line_count()
        if (p_count <= 0 || lineCount < 3 || !this.intersects(p_shape)) {
            return emptyArray()
        }

        val resultCount = Math.min(p_count, lineCount)

        val translateCoors = Array<FloatPoint?>(resultCount) { null }
        val minDists = DoubleArray(resultCount) { Double.MAX_VALUE }

        var currInd = lineCount - 1
        val otherLineCount = p_shape.border_line_count()

        for (nextInd in 0 until lineCount) {
            var currMaxDist = 0.0
            var currTranslateCoor = FloatPoint.ZERO
            for (cornerNo in 0 until otherLineCount) {
                val currCorner = p_shape.corner_approx(cornerNo)
                if (border_line(currInd).side_of(currCorner) == Side.ON_THE_RIGHT) {
                    val projection = currCorner.projection_approx(border_line(currInd))
                    val currDist = projection.distance_square(currCorner)
                    if (currDist > currMaxDist) {
                        currMaxDist = currDist
                        currTranslateCoor = projection.substract(currCorner)
                    }
                }
            }

            for (j in 0 until resultCount) {
                if (currMaxDist < minDists[j]) {
                    for (k in resultCount - 1 downTo j + 1) {
                        minDists[k] = minDists[k - 1]
                        translateCoors[k] = translateCoors[k - 1]
                    }
                    minDists[j] = currMaxDist
                    translateCoors[j] = currTranslateCoor
                    break
                }
            }
            currInd = nextInd
        }
        @Suppress("UNCHECKED_CAST")
        return translateCoors.filterNotNull().toTypedArray()
    }

    override fun shrink(p_offset: Double): ConvexShape {
        var result: ConvexShape = this.offset(-p_offset)
        if (result.is_empty()) {
            val centreBox = this.centre_of_gravity().bounding_box()
            result = this.intersection(centreBox)
        }
        return result
    }

    /**
     * Returns the maximum of the edge widths of the shape. Only defined when the
     * shape is bounded.
     */
    fun length(): Double {
        if (!this.is_bounded()) {
            return Integer.MAX_VALUE.toDouble()
        }
        val dimension = this.dimension()
        if (dimension <= 0) {
            return 0.0
        }
        if (dimension == 1) {
            return this.circumference() / 2.0
        }
        // now the shape is 2-dimensional
        var maxDistance = -1.0
        var maxDistance2 = -1.0
        val gravityPoint = this.centre_of_gravity()
        for (i in 0 until border_line_count()) {
            val currDistance = Math.abs(border_line(i).signed_distance(gravityPoint))
            if (currDistance > maxDistance) {
                maxDistance2 = maxDistance
                maxDistance = currDistance
            } else if (currDistance > maxDistance2) {
                maxDistance2 = currDistance
            }
        }
        return maxDistance + maxDistance2
    }

    /**
     * Calculates, if this Shape and p_other have a common border piece and returns
     * an 2 dimensional array with the indices in this shape and p_other of the
     * touching edge lines in this case. Otherwise,
     * an array of dimension 0 is returned. Used if the intersection shape is
     * 1-dimensional.
     */
    fun touching_sides(p_other: TileShape): IntArray {
        // search the first edge line of p_other with reverse direction >= right

        var sideNo2 = -1
        var dir2: Direction? = null
        for (i in 0 until p_other.border_line_count()) {
            val currDir = p_other.border_line(i).direction()
            if (currDir.compareTo(Direction.LEFT) >= 0) {
                sideNo2 = i
                dir2 = currDir.opposite()
                break
            }
        }
        if (dir2 == null) {
            FRLogger.warn("touching_side : dir2 not found")
            return intArrayOf()
        }
        var sideNo1 = 0
        var dir1 = this.border_line(0).direction()
        val maxInd = this.border_line_count() + p_other.border_line_count()

        for (i in 0 until maxInd) {
            val compare = dir2!!.compareTo(dir1)
            if (compare == 0) {
                if (this.border_line(sideNo1).is_equal_or_opposite(p_other.border_line(sideNo2))) {
                    val result = IntArray(2)
                    result[0] = sideNo1
                    result[1] = sideNo2
                    return result
                }
            }
            if (compare >= 0) { // dir2 is bigger than dir1
                sideNo1 = (sideNo1 + 1) % this.border_line_count()
                dir1 = this.border_line(sideNo1).direction()
            } else { // dir1 is bigger than dir2
                sideNo2 = (sideNo2 + 1) % p_other.border_line_count()
                dir2 = p_other.border_line(sideNo2).direction().opposite()
            }
        }
        return intArrayOf()
    }

    /**
     * Calculates the minimal distance of p_line to this shape, assuming, that
     * p_line is on the left of this shape. Returns -1, if p_line is on the right of
     * this shape or intersects with the interior of
     * this shape.
     */
    fun distance_to_the_left(p_line: Line): Double {
        var result = Integer.MAX_VALUE.toDouble()
        for (i in 0 until this.border_line_count()) {
            val currCorner = this.corner_approx(i)
            var lineSide = p_line.side_of(currCorner, 1.0)
            if (lineSide == Side.COLLINEAR) {
                lineSide = p_line.side_of(this.corner(i))
            }
            if (lineSide == Side.ON_THE_RIGHT) {
                // curr_point would be outside the result shape
                result = -1.0
                break
            }
            result = Math.min(result, p_line.signed_distance(currCorner))
        }
        return result
    }

    /**
     * Returns Side.COLLINEAR, if p_line intersects with the interior of this shape,
     * Side.ON_THE_LEFT, if this shape is completely on the left of p_line or
     * Side.ON_THE_RIGHT, if this shape is completely
     * on the right of p_line.
     */
    fun side_of(p_line: Line): Side {
        var onTheLeft = false
        var onTheRight = false
        for (i in 0 until this.border_line_count()) {
            val currSide = p_line.side_of(this.corner(i))
            if (currSide == Side.ON_THE_LEFT) {
                onTheRight = true
            } else if (currSide == Side.ON_THE_RIGHT) {
                onTheLeft = true
            }
            if (onTheLeft && onTheRight) {
                return Side.COLLINEAR
            }
        }
        return if (onTheLeft) Side.ON_THE_LEFT else Side.ON_THE_RIGHT
    }

    override fun turn_90_degree(p_factor: Int, p_pole: IntPoint): TileShape {
        val newLines = Array(border_line_count()) { i ->
            this.border_line(i).turn_90_degree(p_factor, p_pole)
        }
        return get_instance(newLines)
    }

    override fun rotate_approx(p_angle: Double, p_pole: FloatPoint): TileShape {
        if (p_angle == 0.0) {
            return this
        }
        val newCorners = Array<Point>(border_line_count()) { i ->
            this.corner_approx(i).rotate(p_angle, p_pole).round()
        }
        val cornerPolygon = Polygon(newCorners)
        val polygonCorners = cornerPolygon.corner_array()
        val result: TileShape = when {
            polygonCorners.size >= 3 -> get_instance(polygonCorners)
            polygonCorners.size == 2 -> {
                val currPolyline = Polyline(polygonCorners)
                val currSegment = LineSegment(currPolyline, 0)
                currSegment.to_simplex()
            }
            polygonCorners.size == 1 -> get_instance(polygonCorners[0])
            else -> Simplex.EMPTY
        }
        return result
    }

    override fun mirror_vertical(p_pole: IntPoint): TileShape {
        val newLines = Array(border_line_count()) { i ->
            this.border_line(i).mirror_vertical(p_pole)
        }
        return get_instance(newLines)
    }

    override fun mirror_horizontal(p_pole: IntPoint): TileShape {
        val newLines = Array(border_line_count()) { i ->
            this.border_line(i).mirror_horizontal(p_pole)
        }
        return get_instance(newLines)
    }

    /**
     * Calculates the border line of this shape intersecting the ray from
     * p_from_point into the direction p_direction. p_from_point is assumed to be
     * inside this shape, otherwise -1 is returned.
     */
    fun intersecting_border_line_no(p_from_point: Point, p_direction: Direction): Int {
        if (!this.contains(p_from_point)) {
            return -1
        }
        val fromPoint = p_from_point.to_float()
        val intersectionLine = Line(p_from_point, p_direction)
        val secondLinePoint = intersectionLine.b.to_float()
        var result = -1
        var minDistance = Double.MAX_VALUE
        for (i in 0 until this.border_line_count()) {
            val currBorderLine = this.border_line(i)
            val currIntersection = currBorderLine.intersection_approx(intersectionLine)
            if (currIntersection.x >= Integer.MAX_VALUE) {
                continue // lines are parallel
            }
            val currDistance = currIntersection.distance_square(fromPoint)
            if (currDistance < minDistance) {
                val directionOk = currBorderLine.side_of(secondLinePoint) == Side.ON_THE_LEFT
                        || secondLinePoint.distance_square(currIntersection) < currDistance
                if (directionOk) {
                    result = i
                    minDistance = currDistance
                }
            }
        }
        return result
    }

    /**
     * Cuts p_shape out of this shape and divides the result into convex pieces
     */
    abstract fun cutout(p_shape: TileShape): Array<TileShape>

    /**
     * Returns an array of tuples of integers. The length of the array is the number
     * of points, where p_polyline enters or leaves the interior of this shape. The
     * first coordinate of the tuple is the
     * number of the line segment of p_polyline, which enters the simplex and the
     * second coordinate of the tuple is the number of the edge_line of the simplex,
     * which is crossed there. That means that
     * the entrance point is the intersection of this 2 lines.
     */
    fun entrance_points(p_polyline: Polyline): Array<IntArray> {
        val result = Array(2 * p_polyline.arr.size) { IntArray(2) }
        var intersectionCount = 0
        var prevIntersectionLineNo = -1
        var prevIntersectionEdgeNo = -1
        for (lineNo in 1 until p_polyline.arr.size - 1) {
            val currLineSeg = LineSegment(p_polyline, lineNo)
            val currIntersections = currLineSeg.border_intersections(this)
            for (i in currIntersections.indices) {
                val edgeNo = currIntersections[i]
                if (lineNo != prevIntersectionLineNo || edgeNo != prevIntersectionEdgeNo) {
                    result[intersectionCount][0] = lineNo
                    result[intersectionCount][1] = edgeNo
                    ++intersectionCount
                    prevIntersectionLineNo = lineNo
                    prevIntersectionEdgeNo = edgeNo
                }
            }
        }
        return Arrays.copyOf(result, intersectionCount)
    }

    /**
     * Cuts out the parts of p_polyline in the interior of this shape and returns a
     * list of the remaining pieces of p_polyline. Pieces completely contained in
     * the border of this shape are not returned.
     */
    override fun cutout(p_polyline: Polyline): Array<Polyline> {
        val intersectionNo = this.entrance_points(p_polyline)
        val firstCorner = p_polyline.first_corner()!!
        val firstCornerIsInside = this.contains_inside(firstCorner)
        if (intersectionNo.isEmpty()) {
            if (firstCornerIsInside) {
                return emptyArray()
            }
            return arrayOf(p_polyline)
        }
        val pieces = LinkedList<Polyline>()
        var currIntersectionNo = 0
        var currIntersectionTuple = intersectionNo[currIntersectionNo]
        val firstIntersection = p_polyline.arr[currIntersectionTuple[0]]
            .intersection(this.border_line(currIntersectionTuple[1]))
        if (!firstCornerIsInside) {
            if (firstCorner != firstIntersection) {
                val currPolylineIntersectionNo = currIntersectionTuple[0]
                val currLines = Array(currPolylineIntersectionNo + 2) { i ->
                    if (i <= currPolylineIntersectionNo) p_polyline.arr[i] else this.border_line(currIntersectionTuple[1])
                }
                val currPiece = Polyline(currLines)
                if (!currPiece.is_empty()) {
                    pieces.add(currPiece)
                }
            }
            ++currIntersectionNo
        }
        while (currIntersectionNo < intersectionNo.size - 1) {
            currIntersectionTuple = intersectionNo[currIntersectionNo]
            val nextIntersectionTuple = intersectionNo[currIntersectionNo + 1]
            val currIntersectionNoOfPolyline = currIntersectionTuple[0]
            val nextIntersectionNoOfPolyline = nextIntersectionTuple[0]

            var insertPiece = false
            for (i in currIntersectionNoOfPolyline + 1 until nextIntersectionNoOfPolyline) {
                if (this.is_outside(p_polyline.corner(i)!!)) {
                    insertPiece = true
                    break
                }
            }

            if (insertPiece) {
                val currLines = Array(nextIntersectionNoOfPolyline - currIntersectionNoOfPolyline + 3) { i ->
                    when {
                        i == 0 -> this.border_line(currIntersectionTuple[1])
                        i == nextIntersectionNoOfPolyline - currIntersectionNoOfPolyline + 2 -> this.border_line(nextIntersectionTuple[1])
                        else -> p_polyline.arr[currIntersectionNoOfPolyline + i - 1]
                    }
                }
                val currPiece = Polyline(currLines)
                if (!currPiece.is_empty()) {
                    pieces.add(currPiece)
                }
            }
            currIntersectionNo += 2
        }
        if (currIntersectionNo <= intersectionNo.size - 1) {
            currIntersectionTuple = intersectionNo[currIntersectionNo]
            val currPolylineIntersectionNo = currIntersectionTuple[0]
            val currLines = Array(p_polyline.arr.size - currPolylineIntersectionNo + 1) { i ->
                if (i == 0) this.border_line(currIntersectionTuple[1]) else p_polyline.arr[currPolylineIntersectionNo + i - 1]
            }
            val currPiece = Polyline(currLines)
            if (!currPiece.is_empty()) {
                pieces.add(currPiece)
            }
        }
        val result = Array(pieces.size) { i -> pieces[i] }
        return result
    }

    override fun split_to_convex(): Array<TileShape> {
        return arrayOf(this)
    }

    /**
     * Divides this shape into sections with width and height at most
     * p_max_section_width of about equal size.
     */
    open fun divide_into_sections(p_max_section_width: Double): Array<out TileShape> {
        if (this.is_empty()) {
            return arrayOf(this)
        }
        val sectionBoxes = this.bounding_box().divide_into_sections(p_max_section_width)
        val sectionList = LinkedList<TileShape>()
        for (i in sectionBoxes.indices) {
            val currSection = this.intersection_with_simplify(sectionBoxes[i])
            if (currSection.dimension() == 2) {
                sectionList.add(currSection)
            }
        }
        val result = Array(sectionList.size) { i -> sectionList[i] }
        return result
    }

    /**
     * Checks, if p_line_segment has a common point with the interior of this shape.
     */
    fun is_intersected_interior_by(p_line_segment: LineSegment): Boolean {
        val line = p_line_segment.get_line() ?: return false
        return is_intersected_interior_by(
            p_line_segment.start_point(), p_line_segment.end_point(),
            line
        )
    }

    /**
     * Checks if the line segment defined by p_start_point, p_end_point and p_line
     * has a common point with the interior of
     * this shape.
     */
    fun is_intersected_interior_by(p_start_point: Point, p_end_point: Point, p_line: Line): Boolean {
        val floatStartPoint = p_start_point.to_float()
        val floatEndPoint = p_end_point.to_float()

        val borderLineSideOfStartPointArr = Array<Side?>(this.border_line_count()) { null }
        val borderLineSideOfEndPointArr = Array<Side?>(borderLineSideOfStartPointArr.size) { null }
        for (i in borderLineSideOfStartPointArr.indices) {
            val currBorderLine = this.border_line(i)
            var borderLineSideOfStartPoint = currBorderLine.side_of(floatStartPoint, 1.0)
            if (borderLineSideOfStartPoint == Side.COLLINEAR) {
                borderLineSideOfStartPoint = currBorderLine.side_of(p_start_point)
            }
            var borderLineSideOfEndPoint = currBorderLine.side_of(floatEndPoint, 1.0)
            if (borderLineSideOfEndPoint == Side.COLLINEAR) {
                borderLineSideOfEndPoint = currBorderLine.side_of(p_end_point)
            }
            if (borderLineSideOfStartPoint != Side.ON_THE_RIGHT && borderLineSideOfEndPoint != Side.ON_THE_RIGHT) {
                // both endpoints are outside the border_line, no intersection possible
                return false
            }
            borderLineSideOfStartPointArr[i] = borderLineSideOfStartPoint
            borderLineSideOfEndPointArr[i] = borderLineSideOfEndPoint
        }
        var startPointIsInside = true
        for (i in borderLineSideOfStartPointArr.indices) {
            if (borderLineSideOfStartPointArr[i] != Side.ON_THE_RIGHT) {
                startPointIsInside = false
                break
            }
        }
        if (startPointIsInside) {
            return true
        }
        var endPointIsInside = true
        for (i in borderLineSideOfEndPointArr.indices) {
            if (borderLineSideOfEndPointArr[i] != Side.ON_THE_RIGHT) {
                endPointIsInside = false
                break
            }
        }
        if (endPointIsInside) {
            return true
        }
        val segmentLine = p_line
        // Check, if this line segments intersect a border line of p_shape.
        for (i in borderLineSideOfStartPointArr.indices) {
            val borderLineSideOfStartPoint = borderLineSideOfStartPointArr[i]
            val borderLineSideOfEndPoint = borderLineSideOfEndPointArr[i]
            if (borderLineSideOfStartPoint != borderLineSideOfEndPoint) {
                val startCollinearLeft = borderLineSideOfStartPoint == Side.COLLINEAR && borderLineSideOfEndPoint == Side.ON_THE_LEFT
                val endCollinearLeft = borderLineSideOfEndPoint == Side.COLLINEAR && borderLineSideOfStartPoint == Side.ON_THE_LEFT
                if (startCollinearLeft || endCollinearLeft) {
                    // the interior of p_shape is not intersected.
                    continue
                }
                var prevCornerSide = segmentLine.side_of(this.corner_approx(i), 1.0)
                if (prevCornerSide == Side.COLLINEAR) {
                    prevCornerSide = segmentLine.side_of(this.corner(i))
                }
                val nextCornerIndex = if (i == borderLineSideOfStartPointArr.size - 1) 0 else i + 1
                var nextCornerSide = segmentLine.side_of(this.corner_approx(nextCornerIndex), 1.0)
                if (nextCornerSide == Side.COLLINEAR) {
                    nextCornerSide = segmentLine.side_of(this.corner(nextCornerIndex))
                }
                val crossing1 = prevCornerSide == Side.ON_THE_LEFT && nextCornerSide == Side.ON_THE_RIGHT
                val crossing2 = prevCornerSide == Side.ON_THE_RIGHT && nextCornerSide == Side.ON_THE_LEFT
                if (crossing1 || crossing2) {
                    // this line segment crosses a border line of p_shape
                    return true
                }
            }
        }
        return false
    }

    // auxiliary functions needed because the virtual function mechanism does
    // not work in parameter position
    internal abstract fun intersection(p_other: Simplex): TileShape

    internal abstract fun intersection(p_other: IntOctagon): TileShape

    internal abstract fun intersection(p_other: IntBox): TileShape

    /**
     * Auxiliary function to implement the public function cutout(TileShape p_shape)
     */
    internal abstract fun cutout_from(p_shape: IntBox): Array<out TileShape>

    /**
     * Auxiliary function to implement the public function cutout(TileShape p_shape)
     */
    internal abstract fun cutout_from(p_shape: IntOctagon): Array<out TileShape>

    /**
     * Auxiliary function to implement the public function cutout(TileShape p_shape)
     */
    internal abstract fun cutout_from(p_shape: Simplex): Array<out TileShape>
}
