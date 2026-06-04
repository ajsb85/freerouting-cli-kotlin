package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.util.LinkedList
import java.util.Random

/**
 * Shape described by a closed polygon of corner points. The corners are ordered in counterclock sense around the border of the shape. The corners are normalised, so that the corner with the lowest
 * y-value comes first. In case of equal y-value the corner with the lowest x-value comes first.
 */
class PolygonShape : PolylineShape, Serializable {

    @JvmField
    val corners: Array<Point>

    /**
     * the following fields are for storing precalculated data
     */
    @Transient
    private var precalculated_bounding_box: IntBox? = null
    @Transient
    private var precalculated_bounding_octagon: IntOctagon? = null
    @Transient
    private var precalculated_convex_pieces: Array<TileShape>? = null

    /**
     * Creates a new instance of PolygonShape
     */
    constructor(p_polygon: Polygon) {
        var curr_polygon = p_polygon
        if (p_polygon.winding_number_after_closing() < 0) {
            // the corners of the polygon are in clockwise sense
            curr_polygon = p_polygon.revert_corners()
        }
        val curr_corners = curr_polygon.corner_array()
        var last_corner_no = curr_corners.size - 1

        if (last_corner_no > 0) {
            if (curr_corners[0] == curr_corners[last_corner_no]) {
                // skip last point
                --last_corner_no
            }
        }

        var last_point_collinear = false

        if (last_corner_no >= 2) {
            last_point_collinear = curr_corners[last_corner_no].side_of(curr_corners[last_corner_no - 1], curr_corners[0]) == Side.COLLINEAR
        }
        if (last_point_collinear) {
            // skip last point
            --last_corner_no
        }

        var first_corner_no = 0
        var first_point_collinear = false

        if (last_corner_no - first_corner_no >= 2) {
            first_point_collinear = curr_corners[0].side_of(curr_corners[1], curr_corners[last_corner_no]) == Side.COLLINEAR
        }

        if (first_point_collinear) {
            // skip first point
            ++first_corner_no
        }
        // search the point with the lowest y and then with the lowest x
        var start_corner_no = first_corner_no
        var start_corner = curr_corners[start_corner_no].to_float()
        for (i in start_corner_no + 1..last_corner_no) {
            val curr_corner = curr_corners[i].to_float()
            if (curr_corner.y < start_corner.y || curr_corner.y == start_corner.y && curr_corner.x < start_corner.x) {
                start_corner_no = i
                start_corner = curr_corner
            }
        }
        val new_corner_count = last_corner_no - first_corner_no + 1
        val result = Array<Point>(new_corner_count) { Point.ZERO }
        var curr_corner_no = 0
        for (i in start_corner_no..last_corner_no) {
            result[curr_corner_no] = curr_corners[i]
            ++curr_corner_no
        }
        for (i in first_corner_no until start_corner_no) {
            result[curr_corner_no] = curr_corners[i]
            ++curr_corner_no
        }
        corners = result
    }

    constructor(p_corner_arr: Array<Point>) : this(Polygon(p_corner_arr))

    override fun corner(p_no: Int): Point {
        if (p_no < 0 || p_no >= corners.size) {
            FRLogger.warn("PolygonShape.corner: p_no out of range")
            return corners[0]
        }
        return corners[p_no]
    }

    override fun border_line_count(): Int {
        return corners.size
    }

    override fun corner_is_bounded(p_no: Int): Boolean {
        return true
    }

    override fun intersects(p_shape: Shape): Boolean {
        return p_shape.intersects(this)
    }

    override fun intersects(p_circle: Circle): Boolean {
        val convex_pieces = split_to_convex() ?: return false
        for (piece in convex_pieces) {
            if (piece.intersects(p_circle)) {
                return true
            }
        }
        return false
    }

    override fun intersects(p_simplex: Simplex): Boolean {
        val convex_pieces = split_to_convex() ?: return false
        for (piece in convex_pieces) {
            if (piece.intersects(p_simplex)) {
                return true
            }
        }
        return false
    }

    override fun intersects(p_oct: IntOctagon): Boolean {
        val convex_pieces = split_to_convex() ?: return false
        for (piece in convex_pieces) {
            if (piece.intersects(p_oct)) {
                return true
            }
        }
        return false
    }

    override fun intersects(p_box: IntBox): Boolean {
        val convex_pieces = split_to_convex() ?: return false
        for (piece in convex_pieces) {
            if (piece.intersects(p_box)) {
                return true
            }
        }
        return false
    }

    override fun cutout(p_polyline: Polyline): Array<Polyline> {
        FRLogger.warn("PolygonShape.cutout not yet implemented")
        return emptyArray()
    }

    override fun enlarge(p_offset: Double): Shape {
        if (p_offset == 0.0) {
            return this
        }
        FRLogger.warn("PolygonShape.enlarge not yet implemented")
        return this
    }

    override fun border_distance(p_point: FloatPoint): Double {
        FRLogger.warn("PolygonShape.border_distance not yet implemented")
        return 0.0
    }

    override fun smallest_radius(): Double {
        return border_distance(centre_of_gravity())
    }

    override fun contains(p_point: FloatPoint): Boolean {
        val convex_pieces = split_to_convex() ?: return false
        for (piece in convex_pieces) {
            if (piece.contains(p_point)) {
                return true
            }
        }
        return false
    }

    override fun contains_inside(p_point: Point): Boolean {
        if (contains_on_border(p_point)) {
            return false
        }
        return !is_outside(p_point)
    }

    override fun is_outside(p_point: Point): Boolean {
        val convex_pieces = split_to_convex() ?: return true
        for (piece in convex_pieces) {
            if (!piece.is_outside(p_point)) {
                return false
            }
        }
        return true
    }

    override fun contains(p_point: Point): Boolean {
        return !is_outside(p_point)
    }

    override fun contains_on_border(p_point: Point): Boolean {
        // FRLogger.warn("PolygonShape.contains_on_edge not yet implemented");
        return false
    }

    override fun distance(p_point: FloatPoint): Double {
        FRLogger.warn("PolygonShape.distance not yet implemented")
        return 0.0
    }

    override fun translate_by(p_vector: Vector): PolygonShape {
        if (p_vector.equals(Vector.ZERO)) {
            return this
        }
        val new_corners = Array(corners.size) { i ->
            corners[i].translate_by(p_vector)
        }
        return PolygonShape(new_corners)
    }

    override fun bounding_shape(p_dirs: ShapeBoundingDirections): RegularTileShape {
        return p_dirs.bounds(this)
    }

    override fun bounding_box(): IntBox {
        var box = precalculated_bounding_box
        if (box == null) {
            var llx = Integer.MAX_VALUE.toDouble()
            var lly = Integer.MAX_VALUE.toDouble()
            var urx = Integer.MIN_VALUE.toDouble()
            var ury = Integer.MIN_VALUE.toDouble()
            for (corner in corners) {
                val curr = corner.to_float()
                llx = Math.min(llx, curr.x)
                lly = Math.min(lly, curr.y)
                urx = Math.max(urx, curr.x)
                ury = Math.max(ury, curr.y)
            }
            val lower_left = IntPoint(Math.floor(llx).toInt(), Math.floor(lly).toInt())
            val upper_right = IntPoint(Math.ceil(urx).toInt(), Math.ceil(ury).toInt())
            box = IntBox(lower_left, upper_right)
            precalculated_bounding_box = box
        }
        return box
    }

    override fun bounding_octagon(): IntOctagon {
        var octagon = precalculated_bounding_octagon
        if (octagon == null) {
            var lx = Integer.MAX_VALUE.toDouble()
            var ly = Integer.MAX_VALUE.toDouble()
            var rx = Integer.MIN_VALUE.toDouble()
            var uy = Integer.MIN_VALUE.toDouble()
            var ulx = Integer.MAX_VALUE.toDouble()
            var lrx = Integer.MIN_VALUE.toDouble()
            var llx = Integer.MAX_VALUE.toDouble()
            var urx = Integer.MIN_VALUE.toDouble()
            for (corner in corners) {
                val curr = corner.to_float()
                lx = Math.min(lx, curr.x)
                ly = Math.min(ly, curr.y)
                rx = Math.max(rx, curr.x)
                uy = Math.max(uy, curr.y)

                val tmp = curr.x - curr.y
                ulx = Math.min(ulx, tmp)
                lrx = Math.max(lrx, tmp)

                val tmp2 = curr.x + curr.y
                llx = Math.min(llx, tmp2)
                urx = Math.max(urx, tmp2)
            }
            octagon = IntOctagon(
                Math.floor(lx).toInt(), Math.floor(ly).toInt(), Math.ceil(rx).toInt(), Math.ceil(uy).toInt(),
                Math.floor(ulx).toInt(), Math.ceil(lrx).toInt(), Math.floor(llx).toInt(), Math.ceil(urx).toInt()
            )
            precalculated_bounding_octagon = octagon
        }
        return octagon
    }

    /**
     * Checks, if every line segment between 2 points of the shape is contained completely in the shape.
     */
    fun is_convex(): Boolean {
        if (corners.size <= 2) {
            return true
        }
        var prev_point = corners[corners.size - 1]
        var curr_point = corners[0]
        var next_point = corners[1]

        for (ind in corners.indices) {
            if (next_point.side_of(prev_point, curr_point) == Side.ON_THE_RIGHT) {
                return false
            }
            prev_point = curr_point
            curr_point = next_point
            next_point = if (ind == corners.size - 2) {
                corners[0]
            } else {
                corners[ind + 2]
            }
        }
        // check, if the sum of the interior angles is at most 2 * pi

        val first_line = Line(corners[corners.size - 1], corners[0])
        var curr_line = Line(corners[0], corners[1])
        val first_direction = first_line.direction() as IntDirection
        var curr_direction = curr_line.direction() as IntDirection
        var last_det = first_direction.determinant(curr_direction)

        for (ind2 in 2 until corners.size) {
            curr_line = Line(curr_line.b, corners[ind2])
            curr_direction = curr_line.direction() as IntDirection
            val curr_det = first_direction.determinant(curr_direction)
            if (last_det <= 0.0 && curr_det > 0.0) {
                return false
            }
            last_det = curr_det
        }

        return true
    }

    fun convex_hull(): PolygonShape {
        if (corners.size <= 2) {
            return this
        }
        var prev_point = corners[corners.size - 1]
        var curr_point = corners[0]
        var next_point: Point
        for (ind in corners.indices) {
            next_point = if (ind == corners.size - 1) {
                corners[0]
            } else {
                corners[ind + 1]
            }
            if (next_point.side_of(prev_point, curr_point) != Side.ON_THE_LEFT) {
                // skip curr_point;
                 val new_corners = Array<Point>(corners.size - 1) { Point.ZERO }
                 System.arraycopy(corners, 0, new_corners, 0, ind)
                 if (ind < new_corners.size) {
                     // copy remaining elements if present
                     System.arraycopy(corners, ind + 1, new_corners, ind, new_corners.size - ind)
                 }
                 val result = PolygonShape(new_corners)
                return result.convex_hull()
            }
            prev_point = curr_point
            curr_point = next_point
        }
        return this
    }

    override fun bounding_tile(): TileShape {
        val hull = convex_hull()
        val bounding_lines = Array(hull.corners.size) { Line.ZERO }
        for (i in 0 until bounding_lines.size - 1) {
            bounding_lines[i] = Line(hull.corners[i], hull.corners[i + 1])
        }
        bounding_lines[bounding_lines.size - 1] = Line(hull.corners[hull.corners.size - 1], hull.corners[0])
        return TileShape.get_instance(bounding_lines)
    }

    override fun area(): Double {
        if (dimension() <= 2) {
            return 0.0
        }
        // calculate half of the absolute value of
        // x0 (y1 - yn-1) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ xn-1( y0 - yn-2)
        // where xi, yi are the coordinates of the i-th corner of this polygon.

        var result = 0.0
        var prev_corner = corners[corners.size - 2].to_float()
        var curr_corner = corners[corners.size - 1].to_float()
        for (i in corners.indices) {
            val next_corner = corners[i].to_float()
            result += curr_corner.x * (next_corner.y - prev_corner.y)
            prev_corner = curr_corner
            curr_corner = next_corner
        }
        result = 0.5 * Math.abs(result)
        return result
    }

    override fun dimension(): Int {
        if (corners.isEmpty()) {
            return -1
        }
        if (corners.size == 1) {
            return 0
        }
        if (corners.size == 2) {
            return 1
        }
        return 2
    }

    override fun is_bounded(): Boolean {
        return true
    }

    override fun is_empty(): Boolean {
        return corners.isEmpty()
    }

    override fun border_line(p_no: Int): Line {
        if (p_no < 0 || p_no >= corners.size) {
            FRLogger.warn("PolygonShape.edge_line: p_no out of range")
            return Line.ZERO
        }
        val next_corner = if (p_no == corners.size - 1) {
            corners[0]
        } else {
            corners[p_no + 1]
        }
        return Line(corners[p_no], next_corner)
    }

    override fun nearest_point_approx(p_from_point: FloatPoint): FloatPoint {
        var min_dist = Double.MAX_VALUE
        var result: FloatPoint? = null
        val convex_shapes = split_to_convex() ?: return p_from_point
        for (shape in convex_shapes) {
            val curr_nearest_point = shape.nearest_point_approx(p_from_point)
            val curr_dist = curr_nearest_point.distance_square(p_from_point)
            if (curr_dist < min_dist) {
                min_dist = curr_dist
                result = curr_nearest_point
            }
        }
        return result ?: p_from_point
    }

    override fun turn_90_degree(p_factor: Int, p_pole: IntPoint): PolygonShape {
        val new_corners = Array(corners.size) { i ->
            corners[i].turn_90_degree(p_factor, p_pole)
        }
        return PolygonShape(new_corners)
    }

    override fun rotate_approx(p_angle: Double, p_pole: FloatPoint): PolygonShape {
        if (p_angle == 0.0) {
            return this
        }
        val new_corners = Array<Point>(corners.size) { i ->
            corners[i].to_float().rotate(p_angle, p_pole).round()
        }
        return PolygonShape(new_corners)
    }

    override fun mirror_vertical(p_pole: IntPoint): PolygonShape {
        val new_corners = Array(corners.size) { i ->
            corners[i].mirror_vertical(p_pole)
        }
        return PolygonShape(new_corners)
    }

    override fun mirror_horizontal(p_pole: IntPoint): PolygonShape {
        val new_corners = Array(corners.size) { i ->
            corners[i].mirror_horizontal(p_pole)
        }
        return PolygonShape(new_corners)
    }

    /**
     * Splits this polygon shape into convex pieces. The result is not exact, because rounded intersections of lines are used in the result pieces. It can be made exact, if Polylines are returned
     * instead of Polygons, so that no intersection points are needed in the result.
     */
    override fun split_to_convex(): Array<TileShape> {
        var currentConvexPieces = precalculated_convex_pieces
        if (currentConvexPieces == null) {
            // use a fixed seed to get reproducible result
            random_generator.setSeed(seed.toLong())
            val convex_pieces = split_to_convex_recu() ?: return emptyArray()
            currentConvexPieces = Array(convex_pieces.size) { TileShape.EMPTY }
            val it = convex_pieces.iterator()
            for (i in currentConvexPieces.indices) {
                val curr_piece = it.next()
                currentConvexPieces[i] = TileShape.get_instance(curr_piece.corners)
            }
            precalculated_convex_pieces = currentConvexPieces
        }
        return currentConvexPieces
    }

    /**
     * Private recursive part of split_to_convex. Returns a collection of polygon shape pieces.
     */
    private fun split_to_convex_recu(): Collection<PolygonShape>? {
        // start with a hashed corner and search the first concave corner
        var start_corner_no = random_generator.nextInt(corners.size)
        var curr_corner = corners[start_corner_no]
        var prev_corner = if (start_corner_no != 0) {
            corners[start_corner_no - 1]
        } else {
            corners[corners.size - 1]
        }

        var next_corner: Point

        // search for the next concave corner from here
        var concave_corner_no = -1
        for (i in corners.indices) {
            next_corner = if (start_corner_no < corners.size - 1) {
                corners[start_corner_no + 1]
            } else {
                corners[0]
            }
            if (next_corner.side_of(prev_corner, curr_corner) == Side.ON_THE_RIGHT) {
                // concave corner found
                concave_corner_no = start_corner_no
                break
            }
            prev_corner = curr_corner
            curr_corner = next_corner
            start_corner_no = (start_corner_no + 1) % corners.size
        }
        val result = LinkedList<PolygonShape>()
        if (concave_corner_no < 0) {
            // no concave corner found, this shape is already convex
            result.add(this)
            return result
        }
        val d = DivisionPoint(concave_corner_no)
        if (d.projection == null) {
            // projection not found, maybe polygon has selfintersections
            return null
        }

        // construct the result pieces from p_polygon and the division point
        var corner_count = d.corner_no_after_projection - concave_corner_no

        if (corner_count < 0) {
            corner_count += corners.size
        }
        ++corner_count
        val first_arr = Array<Point>(corner_count) { Point.ZERO }
        var corner_ind = concave_corner_no

        for (i in 0 until corner_count - 1) {
            first_arr[i] = corners[corner_ind]
            corner_ind = (corner_ind + 1) % corners.size
        }
        first_arr[corner_count - 1] = d.projection.round()
        val first_piece = PolygonShape(first_arr)

        corner_count = concave_corner_no - d.corner_no_after_projection
        if (corner_count < 0) {
            corner_count += corners.size
        }
        corner_count += 2
        val last_arr = Array<Point>(corner_count) { Point.ZERO }
        last_arr[0] = d.projection.round()
        corner_ind = d.corner_no_after_projection
        for (i in 1 until corner_count) {
            last_arr[i] = corners[corner_ind]
            corner_ind = (corner_ind + 1) % corners.size
        }
        val last_piece = PolygonShape(last_arr)
        val c1 = first_piece.split_to_convex_recu() ?: return null
        val c2 = last_piece.split_to_convex_recu() ?: return null
        result.addAll(c1)
        result.addAll(c2)
        return result
    }

    private inner class DivisionPoint {

        val corner_no_after_projection: Int
        val projection: FloatPoint?

        /**
         * At a concave corner of the closed polygon, a minimal axis parallel division line is constructed, to divide the closed polygon into two.
         */
        constructor(p_concave_corner_no: Int) {
            val concave_corner = corners[p_concave_corner_no].to_float()
            val before_concave_corner = if (p_concave_corner_no != 0) {
                corners[p_concave_corner_no - 1].to_float()
            } else {
                corners[corners.size - 1].to_float()
            }

            val after_concave_corner = if (p_concave_corner_no == corners.size - 1) {
                corners[0].to_float()
            } else {
                corners[p_concave_corner_no + 1].to_float()
            }

            val search_right = before_concave_corner.y > concave_corner.y || concave_corner.y > after_concave_corner.y

            val search_left = before_concave_corner.y < concave_corner.y || concave_corner.y < after_concave_corner.y

            val search_up = before_concave_corner.x < concave_corner.x || concave_corner.x < after_concave_corner.x

            val search_down = before_concave_corner.x > concave_corner.x || concave_corner.x > after_concave_corner.x

            var min_projection_dist = Integer.MAX_VALUE.toDouble()
            var min_projection: FloatPoint? = null
            var corner_no_after_min_projection = 0

            var corner_no_after_curr_projection = (p_concave_corner_no + 2) % corners.size

            var corner_before_curr_projection = if (corner_no_after_curr_projection != 0) {
                corners[corner_no_after_curr_projection - 1]
            } else {
                corners[corners.size - 1]
            }
            var corner_before_projection_approx = corner_before_curr_projection.to_float()

            var curr_dist: Double
            val loop_end = corners.size - 2

            for (i in 0 until loop_end) {
                val corner_after_curr_projection = corners[corner_no_after_curr_projection]
                val corner_after_projection_approx = corner_after_curr_projection.to_float()
                if (corner_before_projection_approx.y != corner_after_projection_approx.y) {
                    // try a horizontal division
                    val min_y: Double
                    val max_y: Double

                    if (corner_after_projection_approx.y > corner_before_projection_approx.y) {
                        min_y = corner_before_projection_approx.y
                        max_y = corner_after_projection_approx.y
                    } else {
                        min_y = corner_after_projection_approx.y
                        max_y = corner_before_projection_approx.y
                    }

                    if (concave_corner.y >= min_y && concave_corner.y <= max_y) {
                        val curr_line = Line(corner_before_curr_projection, corner_after_curr_projection)
                        val x_intersect = curr_line.function_in_y_value_approx(concave_corner.y)
                        curr_dist = Math.abs(x_intersect - concave_corner.x)
                        // Make sure, that the new shape will not be concave at the projection point.
                        // That might happen, if the boundary curve runs back in itself.
                        val projection_ok = curr_dist < min_projection_dist && (search_right && x_intersect > concave_corner.x && concave_corner.y <= corner_after_projection_approx.y
                            || search_left && x_intersect < concave_corner.x && concave_corner.y >= corner_after_projection_approx.y)
                        if (projection_ok) {
                            min_projection_dist = curr_dist
                            corner_no_after_min_projection = corner_no_after_curr_projection
                            min_projection = FloatPoint(x_intersect, concave_corner.y)
                        }
                    }
                }

                if (corner_before_projection_approx.x != corner_after_projection_approx.x) {
                    // try a vertical division
                    val min_x: Double
                    val max_x: Double
                    if (corner_after_projection_approx.x > corner_before_projection_approx.x) {
                        min_x = corner_before_projection_approx.x
                        max_x = corner_after_projection_approx.x
                    } else {
                        min_x = corner_after_projection_approx.x
                        max_x = corner_before_projection_approx.x
                    }
                    if (concave_corner.x >= min_x && concave_corner.x <= max_x) {
                        val curr_line = Line(corner_before_curr_projection, corner_after_curr_projection)
                        val y_intersect = curr_line.function_value_approx(concave_corner.x)
                        curr_dist = Math.abs(y_intersect - concave_corner.y)
                        // make sure, that the new shape will be convex at the projection point
                        val projection_ok = curr_dist < min_projection_dist && (search_up && y_intersect > concave_corner.y && concave_corner.x >= corner_after_projection_approx.x
                            || search_down && y_intersect < concave_corner.y && concave_corner.x <= corner_after_projection_approx.x)

                        if (projection_ok) {
                            min_projection_dist = curr_dist
                            corner_no_after_min_projection = corner_no_after_curr_projection
                            min_projection = FloatPoint(concave_corner.x, y_intersect)
                        }
                    }
                }
                corner_before_curr_projection = corner_after_curr_projection
                corner_before_projection_approx = corner_after_projection_approx
                if (corner_no_after_curr_projection == corners.size - 1) {
                    corner_no_after_curr_projection = 0
                } else {
                    ++corner_no_after_curr_projection
                }
            }
            if (min_projection_dist == Integer.MAX_VALUE.toDouble()) {
                FRLogger.warn("PolygonShape.DivisionPoint: projection not found")
            }

            projection = min_projection
            corner_no_after_projection = corner_no_after_min_projection
        }
    }

    companion object {
        private const val seed = 99
        private val random_generator = Random(seed.toLong())
    }
}
