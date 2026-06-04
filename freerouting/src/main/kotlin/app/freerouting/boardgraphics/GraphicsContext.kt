package app.freerouting.boardgraphics

import app.freerouting.board.LayerStructure
import app.freerouting.geometry.planar.Area
import app.freerouting.geometry.planar.Circle
import app.freerouting.geometry.planar.Ellipse
import app.freerouting.geometry.planar.FloatPoint
import app.freerouting.geometry.planar.IntBox
import app.freerouting.geometry.planar.PolylineShape
import app.freerouting.geometry.planar.Shape
import app.freerouting.logger.FRLogger
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.Locale

/**
 * Context for drawing items in the board package to the screen.
 */
class GraphicsContext : Serializable {

    @JvmField @Transient var item_color_table: ItemColorTableModel
    @JvmField @Transient var other_color_table: OtherColorTableModel
    @JvmField var color_intensity_table: ColorIntensityTable
    @JvmField var coordinate_transform: CoordinateTransform
    /**
     * layer_visibility_arr[i] is between 0 and 1, for each layer i, 0 is invisible and 1 fully visible.
     */
    private var layer_visibility_arr: DoubleArray
    /**
     * The factor for automatic layer dimming of layers different from the current layer. Values are between 0 and 1. If 1, there is no automatic layer dimming.
     */
    private var auto_layer_dim_factor: Double = 0.7
    /**
     * The layer, which is not automatically dimmed.
     */
    private var fully_visible_layer: Int = 0

    constructor(p_design_bounds: IntBox, p_panel_bounds: Dimension, p_layer_structure: LayerStructure, p_locale: Locale) {
        coordinate_transform = CoordinateTransform(p_design_bounds, p_panel_bounds)
        item_color_table = ItemColorTableModel(p_layer_structure, p_locale)
        other_color_table = OtherColorTableModel(p_locale)
        color_intensity_table = ColorIntensityTable()
        layer_visibility_arr = DoubleArray(p_layer_structure.arr.size)
        for (i in layer_visibility_arr.indices) {
            if (p_layer_structure.arr[i].is_signal) {
                layer_visibility_arr[i] = 1.00
            } else {
                layer_visibility_arr[i] = 0.25
            }
        }
    }

    /**
     * Copy constructor
     */
    constructor(p_graphics_context: GraphicsContext) {
        this.coordinate_transform = CoordinateTransform(p_graphics_context.coordinate_transform)
        this.item_color_table = ItemColorTableModel(p_graphics_context.item_color_table)
        this.other_color_table = OtherColorTableModel(p_graphics_context.other_color_table)
        this.color_intensity_table = ColorIntensityTable(p_graphics_context.color_intensity_table)
        this.layer_visibility_arr = p_graphics_context.copy_layer_visibility_arr()
        this.auto_layer_dim_factor = p_graphics_context.auto_layer_dim_factor
        this.fully_visible_layer = p_graphics_context.fully_visible_layer
    }

    /**
     * Changes the bounds of the board design to p_design_bounds. Useful when components are still placed outside the board.
     */
    fun change_design_bounds(p_new_design_bounds: IntBox) {
        if (p_new_design_bounds == this.coordinate_transform.design_box) {
            return
        }
        val screen_bounds = this.coordinate_transform.screen_bounds
        this.coordinate_transform = CoordinateTransform(p_new_design_bounds, screen_bounds)
    }

    /**
     * changes the size of the panel to p_new_bounds
     */
    fun change_panel_size(p_new_bounds: Dimension) {
        val design_box = coordinate_transform.design_box
        val left_right_swapped = coordinate_transform.is_mirror_left_right()
        val top_bottom_swapped = coordinate_transform.is_mirror_top_bottom()
        val rotation = coordinate_transform.get_rotation()
        coordinate_transform = CoordinateTransform(design_box, p_new_bounds)
        coordinate_transform.set_mirror_left_right(left_right_swapped)
        coordinate_transform.set_mirror_top_bottom(top_bottom_swapped)
        coordinate_transform.set_rotation(rotation)
    }

    /**
     * draws a polygon with corners p_points
     */
    fun draw(p_points: Array<FloatPoint>, p_half_width: Double, p_color: Color?, p_g: Graphics, p_translucency_factor: Double) {
        if (p_color == null) {
            return
        }
        val g2 = p_g as Graphics2D
        val clip_shape = p_g.clip.bounds
        // the class member update_box cannot be used here, because
        // the dirty rectangle is internally enlarged by the system.
        // Therefore, we can not improve the performance by using an
        // update octagon instead of a box.
        val clip_box = coordinate_transform.screen_to_board(clip_shape)
        val scaled_width = coordinate_transform.board_to_screen(p_half_width)

        init_draw_graphics(g2, p_color, (scaled_width * 2).toFloat())
        set_translucency(g2, p_translucency_factor)

        var draw_path: GeneralPath? = null
        if (!show_line_segments) {
            draw_path = GeneralPath()
        }

        for (i in 0 until p_points.size - 1) {
            if (line_outside_update_box(p_points[i], p_points[i + 1], p_half_width + update_offset, clip_box)) {
                // this check should be unnecessary here,
                // the system should do it in the draw(line) function
                continue
            }
            val p1 = coordinate_transform.board_to_screen(p_points[i])!!
            val p2 = coordinate_transform.board_to_screen(p_points[i + 1])!!
            val line = Line2D.Double(p1, p2)

            if (show_line_segments) {
                g2.draw(line)
            } else {
                draw_path!!.append(line, false)
            }
        }
        if (!show_line_segments) {
            g2.draw(draw_path)
        }
    }

    /**
     * draws the boundary of a circle
     */
    fun draw_circle(p_center: FloatPoint, p_radius: Double, p_draw_half_width: Double, p_color: Color?, p_g: Graphics, p_translucency_factor: Double) {
        if (p_color == null) {
            return
        }
        val g2 = p_g as Graphics2D
        val center = coordinate_transform.board_to_screen(p_center)!!

        val radius = coordinate_transform.board_to_screen(p_radius)
        val diameter = 2 * radius
        val draw_width = (2 * coordinate_transform.board_to_screen(p_draw_half_width)).toFloat()
        val circle = Ellipse2D.Double(center.x - radius, center.y - radius, diameter, diameter)
        set_translucency(g2, p_translucency_factor)
        init_draw_graphics(g2, p_color, draw_width)
        g2.draw(circle)
    }

    /**
     * draws a rectangle
     */
    fun draw_rectangle(p_corner1: FloatPoint, p_corner2: FloatPoint, p_draw_half_width: Double, p_color: Color?, p_g: Graphics, p_translucency_factor: Double) {
        if (p_color == null) {
            return
        }
        val g2 = p_g as Graphics2D
        val corner1 = coordinate_transform.board_to_screen(p_corner1)!!
        val corner2 = coordinate_transform.board_to_screen(p_corner2)!!

        val xmin = Math.min(corner1.x, corner2.x)
        val ymin = Math.min(corner1.y, corner2.y)

        val draw_width = (2 * coordinate_transform.board_to_screen(p_draw_half_width)).toFloat()
        val width = Math.abs(corner2.x - corner1.x)
        val height = Math.abs(corner2.y - corner1.y)
        val rectangle = Rectangle2D.Double(xmin, ymin, width, height)
        set_translucency(g2, p_translucency_factor)
        init_draw_graphics(g2, p_color, draw_width)
        g2.draw(rectangle)
    }

    /**
     * Draws the boundary of p_shape.
     */
    fun draw_boundary(p_shape: Shape, p_draw_half_width: Double, p_color: Color?, p_g: Graphics, p_translucency_factor: Double) {
        if (p_shape is PolylineShape) {
            val draw_corners = p_shape.corner_approx_arr()
            if (draw_corners.size <= 1) {
                return
            }
            val closed_draw_corners = Array(draw_corners.size + 1) { FloatPoint(0.0, 0.0) }
            System.arraycopy(draw_corners, 0, closed_draw_corners, 0, draw_corners.size)
            closed_draw_corners[closed_draw_corners.size - 1] = draw_corners[0]
            this.draw(closed_draw_corners, p_draw_half_width, p_color, p_g, p_translucency_factor)
        } else if (p_shape is Circle) {
            this.draw_circle(p_shape.center.to_float(), p_shape.radius.toDouble(), p_draw_half_width, p_color, p_g, p_translucency_factor)
        }
    }

    /**
     * Draws the boundary of p_area.
     */
    fun draw_boundary(p_area: Area, p_draw_half_width: Double, p_color: Color?, p_g: Graphics, p_translucency_factor: Double) {
        draw_boundary(p_area.get_border(), p_draw_half_width, p_color, p_g, p_translucency_factor)
        val holes = p_area.get_holes()
        for (curr_hole in holes) {
            draw_boundary(curr_hole, p_draw_half_width, p_color, p_g, p_translucency_factor)
        }
    }

    /**
     * Draws the interior of a circle
     */
    fun fill_circle(p_circle: Circle, p_g: Graphics, p_color: Color?, p_translucency_factor: Double) {
        if (p_color == null) {
            return
        }
        val center = coordinate_transform.board_to_screen(p_circle.center.to_float())!!
        val radius = coordinate_transform.board_to_screen(p_circle.radius.toDouble())
        if (!point_near_rectangle(center.x, center.y, p_g.clip.bounds, radius)) {
            return
        }
        val diameter = 2 * radius
        val circle = Ellipse2D.Double(center.x - radius, center.y - radius, diameter, diameter)
        val g2 = p_g as Graphics2D
        g2.color = p_color
        set_translucency(g2, p_translucency_factor)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.fill(circle)
    }

    /**
     * Draws the interior of an ellipse.
     */
    fun fill_ellipse(p_ellipse: Ellipse, p_g: Graphics, p_color: Color?, p_translucency_factor: Double) {
        val ellipse_arr = arrayOf(p_ellipse)
        fill_ellipse_arr(ellipse_arr, p_g, p_color, p_translucency_factor)
    }

    /**
     * Draws the interior of an array of ellipses. Ellipses contained in another ellipse are treated as holes.
     */
    fun fill_ellipse_arr(p_ellipse_arr: Array<Ellipse>, p_g: Graphics, p_color: Color?, p_translucency_factor: Double) {
        if (p_color == null || p_ellipse_arr.isEmpty()) {
            return
        }
        val draw_path = GeneralPath(GeneralPath.WIND_EVEN_ODD)
        for (curr_ellipse in p_ellipse_arr) {
            val center = coordinate_transform.board_to_screen(curr_ellipse.center)!!
            val bigger_radius = coordinate_transform.board_to_screen(curr_ellipse.bigger_radius)
            if (!point_near_rectangle(center.x, center.y, p_g.clip.bounds, bigger_radius)) {
                continue
            }
            val smaller_radius = coordinate_transform.board_to_screen(curr_ellipse.smaller_radius)
            val draw_ellipse = Ellipse2D.Double(center.x - bigger_radius, center.y - smaller_radius, 2 * bigger_radius, 2 * smaller_radius)
            val rotation = coordinate_transform.board_to_screen_angle(curr_ellipse.rotation)
            val affine_transform = AffineTransform()
            affine_transform.rotate(rotation, center.x, center.y)
            val rotated_ellipse = affine_transform.createTransformedShape(draw_ellipse)
            draw_path.append(rotated_ellipse, false)
        }
        val g2 = p_g as Graphics2D
        g2.color = p_color
        set_translucency(g2, p_translucency_factor)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.fill(draw_path)
    }

    /**
     * Checks, if the distance of the point with coordinates p_x, p_y to p_rect ist at most p_dist.
     */
    private fun point_near_rectangle(p_x: Double, p_y: Double, p_rect: Rectangle, p_dist: Double): Boolean {
        if (p_x < p_rect.x - p_dist) {
            return false
        }
        if (p_y < p_rect.y - p_dist) {
            return false
        }
        if (p_x > p_rect.x + p_rect.width + p_dist) {
            return false
        }
        return p_y <= p_rect.y + p_rect.height + p_dist
    }

    /**
     * Fill the interior of the polygon shape represented by p_points.
     */
    fun fill_shape(p_points: Array<FloatPoint>, p_g: Graphics, p_color: Color?, p_translucency_factor: Double) {
        if (p_color == null) {
            return
        }
        val g2 = p_g as Graphics2D
        val draw_polygon = Polygon()
        for (curr_point in p_points) {
            val curr_corner = coordinate_transform.board_to_screen(curr_point)!!
            draw_polygon.addPoint(Math.round(curr_corner.x).toInt(), Math.round(curr_corner.y).toInt())
        }
        g2.color = p_color
        set_translucency(g2, p_translucency_factor)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.fill(draw_polygon)
    }

    /**
     * Fill the interior of a list of polygons. Used for example with an area consisting of a border polygon and some holes.
     */
    fun fill_area(p_point_lists: Array<Array<FloatPoint>>, p_g: Graphics, p_color: Color?, p_translucency_factor: Double) {
        if (p_color == null) {
            return
        }
        val draw_path = GeneralPath(GeneralPath.WIND_EVEN_ODD)
        for (curr_point_list in p_point_lists) {
            val draw_polygon = Polygon()
            for (curr_point in curr_point_list) {
                val curr_corner = coordinate_transform.board_to_screen(curr_point)!!
                draw_polygon.addPoint(Math.round(curr_corner.x).toInt(), Math.round(curr_corner.y).toInt())
            }
            draw_path.append(draw_polygon, false)
        }
        val g2 = p_g as Graphics2D
        g2.color = p_color
        set_translucency(g2, p_translucency_factor)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.fill(draw_path)
    }

    /**
     * draws the interior of an item of class geometry.planar.Area
     */
    fun fill_area(p_area: Area, p_g: Graphics, p_color: Color?, p_translucency_factor: Double) {
        if (p_color == null || p_area.is_empty()) {
            return
        }
        if (p_area is Circle) {
            fill_circle(p_area, p_g, p_color, p_translucency_factor)
        } else {
            val border = p_area.get_border() as PolylineShape
            if (!border.is_bounded()) {
                FRLogger.warn("GraphicsContext.fill_area: shape not bounded")
                return
            }
            val clip_shape = p_g.clip.bounds
            val clip_box = coordinate_transform.screen_to_board(clip_shape)
            if (!border.bounding_box().intersects(clip_box)) {
                return
            }
            val holes = p_area.get_holes()

            val draw_polygons = Array(holes.size + 1) { emptyArray<FloatPoint>() }
            for (j in draw_polygons.indices) {
                val curr_draw_shape = if (j == 0) border else holes[j - 1] as PolylineShape
                val curr_draw_polygon = Array(curr_draw_shape.border_line_count() + 1) { FloatPoint(0.0, 0.0) }
                for (i in 0 until curr_draw_polygon.size - 1) {
                    curr_draw_polygon[i] = curr_draw_shape.corner_approx(i)
                }
                // close the polygon
                curr_draw_polygon[curr_draw_polygon.size - 1] = curr_draw_polygon[0]
                draw_polygons[j] = curr_draw_polygon
            }
            fill_area(draw_polygons, p_g, p_color, p_translucency_factor)
        }
        if (show_area_division) {
            val tiles = p_area.split_to_convex()
            for (curr_tile in tiles) {
                val corners = Array(curr_tile.border_line_count() + 1) { FloatPoint(0.0, 0.0) }
                for (j in 0 until corners.size - 1) {
                    corners[j] = curr_tile.corner_approx(j)
                }
                corners[corners.size - 1] = corners[0]
                draw(corners, 1.0, Color.white, p_g, 0.7)
            }
        }
    }

    fun get_background_color(): Color {
        return other_color_table.get_background_color()
    }

    fun get_hilight_color(): Color {
        return other_color_table.get_hilight_color()
    }

    fun get_incomplete_color(): Color {
        return other_color_table.get_incomplete_color()
    }

    fun get_outline_color(): Color {
        return other_color_table.get_outline_color()
    }

    fun get_component_color(p_front: Boolean): Color {
        return other_color_table.get_component_color(p_front)
    }

    fun get_violations_color(): Color {
        return other_color_table.get_violations_color()
    }

    fun get_length_matching_area_color(): Color {
        return other_color_table.get_length_matching_area_color()
    }

    fun get_trace_colors(p_fixed: Boolean): Array<Color> {
        return item_color_table.get_trace_colors(p_fixed)
    }

    fun get_via_colors(p_fixed: Boolean): Array<Color> {
        return item_color_table.get_via_colors(p_fixed)
    }

    fun get_pin_colors(): Array<Color> {
        return item_color_table.get_pin_colors()
    }

    fun get_conduction_colors(): Array<Color> {
        return item_color_table.get_conduction_colors()
    }

    fun get_obstacle_colors(): Array<Color> {
        return item_color_table.get_obstacle_colors()
    }

    fun get_via_obstacle_colors(): Array<Color> {
        return item_color_table.get_via_obstacle_colors()
    }

    fun get_place_obstacle_colors(): Array<Color> {
        return item_color_table.get_place_obstacle_colors()
    }

    fun get_trace_color_intensity(): Double {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.TRACES.ordinal)
    }

    fun set_trace_color_intensity(p_value: Double) {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.TRACES.ordinal, p_value)
    }

    fun get_via_color_intensity(): Double {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.VIAS.ordinal)
    }

    fun set_via_color_intensity(p_value: Double) {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.VIAS.ordinal, p_value)
    }

    fun get_pin_color_intensity(): Double {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.PINS.ordinal)
    }

    fun set_pin_color_intensity(p_value: Double) {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.PINS.ordinal, p_value)
    }

    fun get_conduction_color_intensity(): Double {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal)
    }

    fun set_conduction_color_intensity(p_value: Double) {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal, p_value)
    }

    fun get_obstacle_color_intensity(): Double {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal)
    }

    fun set_obstacle_color_intensity(p_value: Double) {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal, p_value)
    }

    fun get_via_obstacle_color_intensity(): Double {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal)
    }

    fun set_via_obstacle_color_intensity(p_value: Double) {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal, p_value)
    }

    fun get_place_obstacle_color_intensity(): Double {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.PLACE_KEEPOUTS.ordinal)
    }

    fun get_component_outline_color_intensity(): Double {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.COMPONENT_OUTLINES.ordinal)
    }

    fun get_hilight_color_intensity(): Double {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.HILIGHT.ordinal)
    }

    fun set_hilight_color_intensity(p_value: Double) {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.HILIGHT.ordinal, p_value)
    }

    fun get_incomplete_color_intensity(): Double {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal)
    }

    fun set_incomplete_color_intensity(p_value: Double) {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal, p_value)
    }

    fun get_length_matching_area_color_intensity(): Double {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal)
    }

    fun set_length_matching_area_color_intensity(p_value: Double) {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal, p_value)
    }

    fun get_panel_size(): Dimension {
        return coordinate_transform.screen_bounds
    }

    /**
     * Returns the center of the design on the screen.
     */
    fun get_design_center(): Point2D {
        val center = coordinate_transform.design_box_with_offset.centre_of_gravity()
        return coordinate_transform.board_to_screen(center)!!
    }

    /**
     * Returns the bounding box of the design in screen coordinates.
     */
    fun get_design_bounds(): Rectangle {
        return coordinate_transform.board_to_screen(coordinate_transform.design_box)
    }

    /**
     * gets the factor for automatic layer dimming
     */
    fun get_auto_layer_dim_factor(): Double {
        return this.auto_layer_dim_factor
    }

    /**
     * Sets the factor for automatic layer dimming. Values are between 0 and 1. If 1, there is no automatic layer dimming.
     */
    fun set_auto_layer_dim_factor(p_value: Double) {
        auto_layer_dim_factor = p_value
    }

    /**
     * Sets the layer, which will be excluded from automatic layer dimming.
     */
    fun set_fully_visible_layer(p_layer_no: Int) {
        fully_visible_layer = p_layer_no
    }

    /**
     * Gets the visibility factor of the input layer. The result is between 0 and 1. If the result is 0, the layer is invisible, if the result is 1, the layer is fully visible.
     */
    fun get_layer_visibility(p_layer_no: Int): Double {
        return if (p_layer_no == this.fully_visible_layer) {
            layer_visibility_arr[p_layer_no]
        } else {
            this.auto_layer_dim_factor * layer_visibility_arr[p_layer_no]
        }
    }

    /**
     * Gets the visibility factor of the input layer without the automatic layer dimming.
     */
    fun get_raw_layer_visibility(p_layer_no: Int): Double {
        return layer_visibility_arr[p_layer_no]
    }

    /**
     * Gets the visibility factor of the input layer. The value is expected between 0 and 1. If the value is 0, the layer is invisible, if the value is 1, the layer is fully visible.
     */
    fun set_layer_visibility(p_layer_no: Int, p_value: Double) {
        layer_visibility_arr[p_layer_no] = Math.max(0.0, Math.min(p_value, 1.0))
    }

    fun set_layer_visibility_arr(p_layer_visibility_arr: DoubleArray) {
        this.layer_visibility_arr = p_layer_visibility_arr
    }

    fun copy_layer_visibility_arr(): DoubleArray {
        val result = DoubleArray(this.layer_visibility_arr.size)
        System.arraycopy(this.layer_visibility_arr, 0, result, 0, this.layer_visibility_arr.size)
        return result
    }

    /**
     * Returns the number of layers on the board
     */
    fun layer_count(): Int {
        return layer_visibility_arr.size
    }

    /**
     * filter lines, which cannot touch the update_box to improve the performance of the draw function by avoiding unnecessary calls of draw (line)
     */
    private fun line_outside_update_box(p_1: FloatPoint?, p_2: FloatPoint?, p_update_offset: Double, p_update_box: IntBox): Boolean {
        if (p_1 == null || p_2 == null) {
            return true
        }
        if (Math.max(p_1.x, p_2.x) < p_update_box.ll.x - p_update_offset) {
            return true
        }
        if (Math.max(p_1.y, p_2.y) < p_update_box.ll.y - p_update_offset) {
            return true
        }
        if (Math.min(p_1.x, p_2.x) > p_update_box.ur.x + p_update_offset) {
            return true
        }
        return Math.min(p_1.y, p_2.y) > p_update_box.ur.y + p_update_offset
    }

    /**
     * Writes an instance of this class to a file.
     */
    @Throws(IOException::class)
    private fun writeObject(p_stream: ObjectOutputStream) {
        p_stream.defaultWriteObject()
        item_color_table.write_object(p_stream)
        other_color_table.write_object(p_stream)
    }

    /**
     * Reads an instance of this class from a file
     */
    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(p_stream: ObjectInputStream) {
        p_stream.defaultReadObject()
        this.item_color_table = ItemColorTableModel(p_stream)
        this.other_color_table = OtherColorTableModel(p_stream)
    }

    companion object {
        private const val update_offset = 10000
        private const val show_line_segments = false
        private const val show_area_division = false

        /**
         * initialise some values in p_graphics
         */
        private fun init_draw_graphics(p_graphics: Graphics2D, p_color: Color, p_width: Float) {
            val bs = BasicStroke(Math.max(p_width, 0f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            p_graphics.stroke = bs
            p_graphics.color = p_color
            p_graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        }

        private fun set_translucency(p_g2: Graphics2D, p_factor: Double) {
            val curr_alpha_composite: AlphaComposite = if (p_factor >= 0) {
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p_factor.toFloat())
            } else {
                AlphaComposite.getInstance(AlphaComposite.DST_OVER, (-p_factor).toFloat())
            }
            p_g2.composite = curr_alpha_composite
        }
    }
}
