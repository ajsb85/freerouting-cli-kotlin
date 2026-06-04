package app.freerouting.io.specctra.parser

import app.freerouting.geometry.planar.FloatPoint
import app.freerouting.geometry.planar.IntBox
import app.freerouting.geometry.planar.Line
import app.freerouting.geometry.planar.PolylineShape
import app.freerouting.geometry.planar.Vector
import app.freerouting.logger.FRLogger
import java.io.Serializable

/**
 * Computes transformations between a specctra dsn-file coordinates and board coordinates.
 */
class CoordinateTransform(
    private val scale_factor: Double,
    private val base_x: Double,
    private val base_y: Double
) : Serializable {

    /**
     * Scale a value from the board to the dsn coordinate system
     */
    fun board_to_dsn(p_val: Double): Double {
        return p_val / scale_factor
    }

    /**
     * Scale a value from the dsn to the board coordinate system
     */
    fun dsn_to_board(p_val: Double): Double {
        return p_val * scale_factor
    }

    /**
     * Transforms a geometry.planar.FloatPoint to a tuple of doubles in the dsn coordinate system.
     */
    fun board_to_dsn(p_point: FloatPoint): DoubleArray {
        val result = DoubleArray(2)
        result[0] = board_to_dsn(p_point.x) + base_x
        result[1] = board_to_dsn(p_point.y) + base_y
        return result
    }

    /**
     * Transforms a geometry.planar.FloatPoint to a tuple of doubles in the dsn coordinate system in relative (vector) coordinates.
     */
    fun board_to_dsn_rel(p_point: FloatPoint): DoubleArray {
        val result = DoubleArray(2)
        result[0] = board_to_dsn(p_point.x)
        result[1] = board_to_dsn(p_point.y)
        return result
    }

    /**
     * Transforms an array of n geometry.planar.FloatPoints to an array of 2*n doubles in the dsn coordinate system.
     */
    fun board_to_dsn(p_points: Array<FloatPoint>): DoubleArray {
        val result = DoubleArray(2 * p_points.size)
        for (i in p_points.indices) {
            result[2 * i] = board_to_dsn(p_points[i].x) + base_x
            result[2 * i + 1] = board_to_dsn(p_points[i].y) + base_y
        }
        return result
    }

    /**
     * Transforms an array of n geometry.planar.Lines to an array of 4*n doubles in the dsn coordinate system.
     */
    fun board_to_dsn(p_lines: Array<Line>): DoubleArray {
        val result = DoubleArray(4 * p_lines.size)
        for (i in p_lines.indices) {
            val a = p_lines[i].a.to_float()
            val b = p_lines[i].b.to_float()
            result[4 * i] = board_to_dsn(a.x) + base_x
            result[4 * i + 1] = board_to_dsn(a.y) + base_y
            result[4 * i + 2] = board_to_dsn(b.x) + base_x
            result[4 * i + 3] = board_to_dsn(b.y) + base_y
        }
        return result
    }

    /**
     * Transforms an array of n geometry.planar.FloatPoints to an array of 2*n doubles in the dsn coordinate system in relative (vector) coordinates.
     */
    fun board_to_dsn_rel(p_points: Array<FloatPoint>): DoubleArray {
        val result = DoubleArray(2 * p_points.size)
        for (i in p_points.indices) {
            result[2 * i] = board_to_dsn(p_points[i].x)
            result[2 * i + 1] = board_to_dsn(p_points[i].y)
        }
        return result
    }

    /**
     * Transforms a geometry.planar.Vector to a tuple of doubles in the dsn coordinate system.
     */
    fun board_to_dsn(p_vector: Vector): DoubleArray {
        val result = DoubleArray(2)
        val v = p_vector.to_float()
        result[0] = board_to_dsn(v.x)
        result[1] = board_to_dsn(v.y)
        return result
    }

    /**
     * Transforms a dsn tuple to a geometry.planar.FloatPoint
     */
    fun dsn_to_board(p_tuple: DoubleArray): FloatPoint {
        val x = dsn_to_board(p_tuple[0] - base_x)
        val y = dsn_to_board(p_tuple[1] - base_y)
        return FloatPoint(x, y)
    }

    /**
     * Transforms a dsn tuple to a geometry.planar.FloatPoint in relative (vector) coordinates.
     */
    fun dsn_to_board_rel(p_tuple: DoubleArray): FloatPoint {
        val x = dsn_to_board(p_tuple[0])
        val y = dsn_to_board(p_tuple[1])
        return FloatPoint(x, y)
    }

    /**
     * Transforms a geometry.planar.Intbox to the coordinates of a Rectangle.
     */
    fun board_to_dsn(p_box: IntBox): DoubleArray {
        val result = DoubleArray(4)
        result[0] = p_box.ll.x / scale_factor + base_x
        result[1] = p_box.ll.y / scale_factor + base_y
        result[2] = p_box.ur.x / scale_factor + base_x
        result[3] = p_box.ur.y / scale_factor + base_y
        return result
    }

    /**
     * Transforms a geometry.planar.Intbox to a Rectangle in relative (vector) coordinates.
     */
    fun board_to_dsn_rel(p_box: IntBox): DoubleArray {
        val result = DoubleArray(4)
        result[0] = p_box.ll.x / scale_factor
        result[1] = p_box.ll.y / scale_factor
        result[2] = p_box.ur.x / scale_factor
        result[3] = p_box.ur.y / scale_factor
        return result
    }

    /**
     * Transforms a board shape to a dsn shape.
     */
    fun board_to_dsn(p_board_shape: app.freerouting.geometry.planar.Shape, p_layer: Layer): Shape? {
        val result: Shape?
        if (p_board_shape is IntBox) {
            result = Rectangle(p_layer, board_to_dsn(p_board_shape))
        } else if (p_board_shape is PolylineShape) {
            val corners = p_board_shape.corner_approx_arr()
            val coors = board_to_dsn(corners)
            result = Polygon(p_layer, coors)
        } else if (p_board_shape is app.freerouting.geometry.planar.Circle) {
            val diameter = 2 * board_to_dsn(p_board_shape.radius.toDouble())
            val centerCoor = board_to_dsn(p_board_shape.center.to_float())
            result = Circle(p_layer, diameter, centerCoor[0], centerCoor[1])
        } else {
            FRLogger.warn("CoordinateTransform.board_to_dsn not yet implemented for p_board_shape")
            result = null
        }
        return result
    }

    /**
     * Transforms the relative (vector) coordinates of a geometry.planar.Shape to a specctra dsn shape.
     */
    fun board_to_dsn_rel(p_board_shape: app.freerouting.geometry.planar.Shape, p_layer: Layer): Shape? {
        val result: Shape?
        if (p_board_shape is IntBox) {
            result = Rectangle(p_layer, board_to_dsn_rel(p_board_shape))
        } else if (p_board_shape is PolylineShape) {
            val corners = p_board_shape.corner_approx_arr()
            val coors = board_to_dsn_rel(corners)
            result = Polygon(p_layer, coors)
        } else if (p_board_shape is app.freerouting.geometry.planar.Circle) {
            val diameter = 2 * board_to_dsn(p_board_shape.radius.toDouble())
            val centerCoor = board_to_dsn_rel(p_board_shape.center.to_float())
            result = Circle(p_layer, diameter, centerCoor[0], centerCoor[1])
        } else {
            FRLogger.warn("CoordinateTransform.board_to_dsn not yet implemented for p_board_shape")
            result = null
        }
        return result
    }
}
