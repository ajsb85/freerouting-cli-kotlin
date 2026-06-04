package app.freerouting.boardgraphics

import app.freerouting.geometry.planar.FloatPoint
import app.freerouting.geometry.planar.IntBox
import app.freerouting.geometry.planar.Limits
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.geom.Point2D
import java.io.Serializable

/**
 * Transformation function between the board and the screen coordinate systems.
 */
class CoordinateTransform : Serializable {

    @JvmField val design_box: IntBox
    @JvmField val design_box_with_offset: IntBox
    @JvmField val screen_bounds: Dimension
    private val scale_factor: Double
    private val display_x_offset: Double
    private val display_y_offset: Double
    private val rotation_pole: FloatPoint
    /**
     * Left side and right side of the board are swapped.
     */
    private var mirror_left_right: Boolean = false
    /**
     * Top side and bottom side of the board are swapped.
     */
    private var mirror_top_bottom: Boolean = true
    private var rotation: Double = 0.0

    constructor(p_design_box: IntBox, p_panel_bounds: Dimension) {
        this.screen_bounds = p_panel_bounds
        this.design_box = p_design_box
        this.rotation_pole = p_design_box.centre_of_gravity()

        val min_ll = Math.min(p_design_box.ll.x, p_design_box.ll.y)
        val max_ur = Math.max(p_design_box.ur.x, p_design_box.ur.y)
        if (Math.max(Math.abs(min_ll), Math.abs(max_ur)) <= 0.3 * Limits.CRIT_INT) {
            // create an offset to p_design_box to enable deep zoom out
            val design_offset = Math.max(p_design_box.width(), p_design_box.height()).toDouble()
            design_box_with_offset = p_design_box.offset(design_offset)
        } else {
            // no offset because of danger of integer overflow
            design_box_with_offset = p_design_box
        }

        val x_scale_factor = screen_bounds.getWidth() / design_box_with_offset.width()
        val y_scale_factor = screen_bounds.getHeight() / design_box_with_offset.height()

        scale_factor = Math.min(x_scale_factor, y_scale_factor)
        display_x_offset = scale_factor * design_box_with_offset.ll.x
        display_y_offset = scale_factor * design_box_with_offset.ll.y
    }

    /**
     * Copy constructor
     */
    constructor(p_coordinate_transform: CoordinateTransform) {
        this.screen_bounds = Dimension(p_coordinate_transform.screen_bounds)
        this.design_box = IntBox(p_coordinate_transform.design_box.ll, p_coordinate_transform.design_box.ur)
        this.rotation_pole = FloatPoint(p_coordinate_transform.rotation_pole.x, p_coordinate_transform.rotation_pole.y)
        this.design_box_with_offset = IntBox(p_coordinate_transform.design_box_with_offset.ll, p_coordinate_transform.design_box_with_offset.ur)
        this.scale_factor = p_coordinate_transform.scale_factor
        this.display_x_offset = p_coordinate_transform.display_x_offset
        this.display_y_offset = p_coordinate_transform.display_y_offset
        this.mirror_left_right = p_coordinate_transform.mirror_left_right
        this.mirror_top_bottom = p_coordinate_transform.mirror_top_bottom
        this.rotation = p_coordinate_transform.rotation
    }

    /**
     * scale a value from the board to the screen coordinate system
     */
    fun board_to_screen(p_val: Double): Double {
        return p_val * scale_factor
    }

    /**
     * scale a value the screen to the board coordinate system
     */
    fun screen_to_board(p_val: Double): Double {
        return p_val / scale_factor
    }

    /**
     * transform a geometry.planar.FloatPoint to a java.awt.geom.Point2D
     */
    fun board_to_screen(p_point: FloatPoint?): Point2D? {
        if (p_point == null) {
            return null
        }

        val rotated_point = p_point.rotate(this.rotation, this.rotation_pole)

        val x: Double
        val y: Double
        if (this.mirror_left_right) {
            x = (design_box_with_offset.width() - rotated_point.x - 1) * scale_factor + display_x_offset
        } else {
            x = rotated_point.x * scale_factor - display_x_offset
        }
        if (this.mirror_top_bottom) {
            y = (design_box_with_offset.height() - rotated_point.y - 1) * scale_factor + display_y_offset
        } else {
            y = rotated_point.y * scale_factor - display_y_offset
        }
        return Point2D.Double(x, y)
    }

    /**
     * Transform a java.awt.geom.Point2D to a geometry.planar.FloatPoint
     */
    fun screen_to_board(p_point: Point2D): FloatPoint {
        val x: Double
        val y: Double
        if (this.mirror_left_right) {
            x = design_box_with_offset.width() - (p_point.x - display_x_offset) / scale_factor - 1
        } else {
            x = (p_point.x + display_x_offset) / scale_factor
        }
        if (this.mirror_top_bottom) {
            y = design_box_with_offset.height() - (p_point.y - display_y_offset) / scale_factor - 1
        } else {
            y = (p_point.y + display_y_offset) / scale_factor
        }
        val result = FloatPoint(x, y)
        return result.rotate(-this.rotation, this.rotation_pole)
    }

    /**
     * Transforms an angle in radian on the board to an angle on the screen.
     */
    fun board_to_screen_angle(p_angle: Double): Double {
        var result = p_angle + this.rotation
        if (this.mirror_left_right) {
            result = Math.PI - result
        }
        if (this.mirror_top_bottom) {
            result = -result
        }
        while (result >= 2 * Math.PI) {
            result -= 2 * Math.PI
        }
        while (result < 0) {
            result += 2 * Math.PI
        }
        return result
    }

    /**
     * Transform a geometry.planar.IntBox to a java.awt.Rectangle If the internal rotation is not a multiple of Pi/2, a bounding rectangle of the rotated rectangular shape is returned.
     */
    fun board_to_screen(p_box: IntBox): Rectangle {
        val corner_1 = board_to_screen(p_box.ll.to_float())!!
        val corner_2 = board_to_screen(p_box.ur.to_float())!!
        val ll_x = Math.min(corner_1.x, corner_2.x)
        val ll_y = Math.min(corner_1.y, corner_2.y)
        val dx = Math.abs(corner_2.x - corner_1.x)
        val dy = Math.abs(corner_2.y - corner_1.y)
        return Rectangle(Math.floor(ll_x).toInt(), Math.floor(ll_y).toInt(), Math.ceil(dx).toInt(), Math.ceil(dy).toInt())
    }

    /**
     * Transform a java.awt.Rectangle to a geometry.planar.IntBox If the internal rotation is not a multiple of Pi/2, a bounding box of the rotated rectangular shape is returned.
     */
    fun screen_to_board(p_rect: Rectangle): IntBox {
        val corner_1 = screen_to_board(Point2D.Double(p_rect.x.toDouble(), p_rect.y.toDouble()))
        val corner_2 = screen_to_board(Point2D.Double(p_rect.x + p_rect.width.toDouble(), p_rect.y + p_rect.height.toDouble()))
        val llx = Math.floor(Math.min(corner_1.x, corner_2.x)).toInt()
        val lly = Math.floor(Math.min(corner_1.y, corner_2.y)).toInt()
        val urx = Math.ceil(Math.max(corner_1.x, corner_2.x)).toInt()
        val ury = Math.ceil(Math.max(corner_1.y, corner_2.y)).toInt()
        return IntBox(llx, lly, urx, ury)
    }

    /**
     * Returns, if the left side and the right side of the board are swapped.
     */
    fun is_mirror_left_right(): Boolean {
        return mirror_left_right
    }

    /**
     * If p_value is true, the left side and the right side of the board will be swapped.
     */
    fun set_mirror_left_right(p_value: Boolean) {
        mirror_left_right = p_value
    }

    /**
     * Returns, if the top side and the bottom side of the board are swapped.
     */
    fun is_mirror_top_bottom(): Boolean {
        // Because the origin of display is the upper left corner, the internal value
        // is opposite to the result of this function.
        return !mirror_top_bottom
    }

    /**
     * If p_value is true, the top side and the bottom side of the board will be swapped.
     */
    fun set_mirror_top_bottom(p_value: Boolean) {
        // Because the origin of display is the upper left corner, the internal value
        // will be opposite to the input value of this function.
        mirror_top_bottom = !p_value
    }

    /**
     * Returns the rotation of the displayed board.
     */
    fun get_rotation(): Double {
        return rotation
    }

    /**
     * Sets the rotation of the displayed board to p_value.
     */
    fun set_rotation(p_value: Double) {
        rotation = p_value
    }

    /**
     * Returns the internal rotation snapped to the nearest multiple of 90 degree. The result will be 0, 1, 2 or 3.
     */
    fun get_90_degree_rotation(): Int {
        var multiple = Math.round(Math.toDegrees(rotation) / 90.0).toInt()
        while (multiple < 0) {
            multiple += 4
        }
        while (multiple >= 4) {
            multiple -= 4
        }
        return multiple
    }
}
