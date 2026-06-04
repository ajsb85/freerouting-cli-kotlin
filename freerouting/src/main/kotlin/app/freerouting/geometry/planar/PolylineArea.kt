package app.freerouting.geometry.planar

import app.freerouting.datastructures.Stoppable
import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.util.LinkedList

/**
 * A PolylineArea is an Area, where the outside border curve and the hole borders consist of straight lines.
 */
class PolylineArea(
    @JvmField val border_shape: PolylineShape,
    @JvmField val hole_arr: Array<PolylineShape>
) : Area, Serializable {

    @Transient
    private var precalculated_convex_pieces: Array<TileShape>? = null

    override fun dimension(): Int {
        return border_shape.dimension()
    }

    override fun is_bounded(): Boolean {
        return border_shape.is_bounded()
    }

    override fun is_empty(): Boolean {
        return border_shape.is_empty()
    }

    override fun is_contained_in(p_box: IntBox): Boolean {
        return border_shape.is_contained_in(p_box)
    }

    override fun get_border(): PolylineShape {
        return border_shape
    }

    override fun get_holes(): Array<PolylineShape> {
        return hole_arr
    }

    override fun bounding_box(): IntBox {
        return border_shape.bounding_box()
    }

    override fun bounding_octagon(): IntOctagon {
        return border_shape.bounding_octagon() ?: IntOctagon.EMPTY
    }

    override fun contains(p_point: FloatPoint): Boolean {
        if (!border_shape.contains(p_point)) {
            return false
        }
        for (hole in hole_arr) {
            if (hole.contains(p_point)) {
                return false
            }
        }
        return true
    }

    override fun contains(p_point: Point): Boolean {
        if (!border_shape.contains(p_point)) {
            return false
        }
        for (hole in hole_arr) {
            if (hole.contains_inside(p_point)) {
                return false
            }
        }
        return true
    }

    override fun nearest_point_approx(p_from_point: FloatPoint): FloatPoint {
        var min_dist = Double.MAX_VALUE
        var result: FloatPoint? = null
        val convex_shapes = split_to_convex()
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

    override fun translate_by(p_vector: Vector): PolylineArea {
        if (p_vector.equals(Vector.ZERO)) {
            return this
        }
        val translated_border = border_shape.translate_by(p_vector)
        val translated_holes = Array(hole_arr.size) { i ->
            hole_arr[i].translate_by(p_vector)
        }
        return PolylineArea(translated_border, translated_holes)
    }

    override fun corner_approx_arr(): Array<FloatPoint> {
        var corner_count = border_shape.border_line_count()
        for (hole in hole_arr) {
            corner_count += hole.border_line_count()
        }
        val result = Array(corner_count) { FloatPoint(Double.MAX_VALUE, Double.MAX_VALUE) }
        val curr_corner_arr = border_shape.corner_approx_arr()
        System.arraycopy(curr_corner_arr, 0, result, 0, curr_corner_arr.size)
        var dest_pos = curr_corner_arr.size
        for (hole in hole_arr) {
            val next_corner_arr = hole.corner_approx_arr()
            System.arraycopy(next_corner_arr, 0, result, dest_pos, next_corner_arr.size)
            dest_pos += next_corner_arr.size
        }
        return result
    }

    /**
     * Splits this polygon shape with holes into convex pieces. The result is not exact, because rounded intersections of lines are used in the result pieces. It can be made exact, if Polylines are
     * returned instead of Polygons, so that no intersection points are needed in the result.
     */
    override fun split_to_convex(): Array<TileShape> {
        return split_to_convex(null) ?: emptyArray()
    }

    /**
     * Splits this polygon shape with holes into convex pieces. The result is not exact, because rounded intersections of lines are used in the result pieces. It can be made exact, if Polylines are
     * returned instead of Polygons, so that no intersection points are needed in the result. If p_stoppable_thread != null, this function can be interrupted.
     */
    fun split_to_convex(p_stoppable_thread: Stoppable?): Array<TileShape>? {
        var currentConvexPieces = precalculated_convex_pieces
        if (currentConvexPieces == null) {
            val convex_border_pieces = border_shape.split_to_convex()
                ?: // split failed
                return null
            var curr_piece_list: Collection<TileShape> = LinkedList(convex_border_pieces.asList())
            for (hole in hole_arr) {
                if (hole.dimension() < 2) {
                    FRLogger.warn("PolylineArea. split_to_convex: dimension 2 for hole expected")
                    continue
                }
                val convex_hole_pieces = hole.split_to_convex() ?: return null
                for (curr_hole_piece in convex_hole_pieces) {
                    val new_piece_list = LinkedList<TileShape>()
                    for (curr_divide_piece in curr_piece_list) {
                        if (p_stoppable_thread != null && p_stoppable_thread.isStopRequested) {
                            return null
                        }
                        cutout_hole_piece(curr_divide_piece, curr_hole_piece, new_piece_list)
                    }
                    curr_piece_list = new_piece_list
                }
            }
            currentConvexPieces = curr_piece_list.toTypedArray()
            precalculated_convex_pieces = currentConvexPieces
        }
        return currentConvexPieces
    }

    override fun turn_90_degree(p_factor: Int, p_pole: IntPoint): PolylineArea {
        val new_border = border_shape.turn_90_degree(p_factor, p_pole)
        val new_hole_arr = Array(hole_arr.size) { i ->
            hole_arr[i].turn_90_degree(p_factor, p_pole)
        }
        return PolylineArea(new_border, new_hole_arr)
    }

    override fun rotate_approx(p_angle: Double, p_pole: FloatPoint): PolylineArea {
        val new_border = border_shape.rotate_approx(p_angle, p_pole)
        val new_hole_arr = Array(hole_arr.size) { i ->
            hole_arr[i].rotate_approx(p_angle, p_pole)
        }
        return PolylineArea(new_border, new_hole_arr)
    }

    override fun mirror_vertical(p_pole: IntPoint): PolylineArea {
        val new_border = border_shape.mirror_vertical(p_pole)
        val new_hole_arr = Array(hole_arr.size) { i ->
            hole_arr[i].mirror_vertical(p_pole)
        }
        return PolylineArea(new_border, new_hole_arr)
    }

    override fun mirror_horizontal(p_pole: IntPoint): PolylineArea {
        val new_border = border_shape.mirror_horizontal(p_pole)
        val new_hole_arr = Array(hole_arr.size) { i ->
            hole_arr[i].mirror_horizontal(p_pole)
        }
        return PolylineArea(new_border, new_hole_arr)
    }

    companion object {
        private fun cutout_hole_piece(
            p_divide_piece: TileShape,
            p_hole_piece: TileShape,
            p_result_pieces: MutableCollection<TileShape>
        ) {
            val result_pieces = p_divide_piece.cutout(p_hole_piece)
            for (curr_piece in result_pieces) {
                if (curr_piece.dimension() == 2) {
                    p_result_pieces.add(curr_piece)
                }
            }
        }
    }
}
