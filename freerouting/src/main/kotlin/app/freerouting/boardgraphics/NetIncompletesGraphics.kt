package app.freerouting.boardgraphics

import app.freerouting.board.Pin
import app.freerouting.drc.NetIncompletes
import app.freerouting.geometry.planar.FloatPoint
import java.awt.Color
import java.awt.Graphics

/**
 * Utility class for drawing contents of NetIncompletes.
 */
object NetIncompletesGraphics {

    /**
     * Draws the incomplete connections and optional length violations.
     *
     * @param p_net_incompletes        The net incompletes data object.
     * @param p_graphics               The AWT graphics object.
     * @param p_graphics_context       The board graphics context.
     * @param p_length_violations_only If true, only draws length violation markers,
     *                                 not airlines.
     */
    @JvmStatic
    fun draw(
        p_net_incompletes: NetIncompletes,
        p_graphics: Graphics,
        p_graphics_context: GraphicsContext,
        p_length_violations_only: Boolean
    ) {
        if (!p_length_violations_only) {
            val draw_color = p_graphics_context.get_incomplete_color()
            val draw_intensity = p_graphics_context.get_incomplete_color_intensity()
            if (draw_intensity <= 0) {
                return
            }
            val draw_points = Array(2) { FloatPoint(0.0, 0.0) }
            val draw_width = 1
            for (curr_incomplete in p_net_incompletes.getIncompletes()) {
                draw_points[0] = curr_incomplete.from_corner
                draw_points[1] = curr_incomplete.to_corner
                p_graphics_context.draw(draw_points, draw_width.toDouble(), draw_color, p_graphics, draw_intensity)
                if (!curr_incomplete.from_item.shares_layer(curr_incomplete.to_item)) {
                    draw_layer_change_marker(
                        curr_incomplete.from_corner,
                        p_net_incompletes.getMarkerRadius(),
                        p_graphics,
                        p_graphics_context
                    )
                    draw_layer_change_marker(
                        curr_incomplete.to_corner,
                        p_net_incompletes.getMarkerRadius(),
                        p_graphics,
                        p_graphics_context
                    )
                }
            }
        }
        if (p_net_incompletes.get_length_violation() == 0.0) {
            return
        }
        // draw the length violation around every Pin of the net.
        val net_pins: Collection<Pin> = p_net_incompletes.getNet()?.get_pins() ?: emptyList()
        for (curr_pin in net_pins) {
            draw_length_violation_marker(
                curr_pin.get_center().to_float(),
                p_net_incompletes.get_length_violation(),
                p_graphics,
                p_graphics_context
            )
        }
    }

    /**
     * Draws a marker indicating a layer change (via or trace segment end) in an
     * airline.
     */
    @JvmStatic
    fun draw_layer_change_marker(
        p_location: FloatPoint,
        p_radius: Double,
        p_graphics: Graphics,
        p_graphics_context: GraphicsContext
    ) {
        val draw_width = 1
        val draw_color = p_graphics_context.get_incomplete_color()
        val draw_intensity = p_graphics_context.get_incomplete_color_intensity()
        val draw_points = Array(2) { FloatPoint(0.0, 0.0) }
        draw_points[0] = FloatPoint(p_location.x - p_radius, p_location.y - p_radius)
        draw_points[1] = FloatPoint(p_location.x + p_radius, p_location.y + p_radius)
        p_graphics_context.draw(draw_points, draw_width.toDouble(), draw_color, p_graphics, draw_intensity)
        draw_points[0] = FloatPoint(p_location.x + p_radius, p_location.y - p_radius)
        draw_points[1] = FloatPoint(p_location.x - p_radius, p_location.y + p_radius)
        p_graphics_context.draw(draw_points, draw_width.toDouble(), draw_color, p_graphics, draw_intensity)
    }

    /**
     * Draws a marker indicating a length violation on a pin.
     */
    @JvmStatic
    fun draw_length_violation_marker(
        p_location: FloatPoint,
        p_diameter: Double,
        p_graphics: Graphics,
        p_graphics_context: GraphicsContext
    ) {
        val draw_width = 1
        val draw_color = p_graphics_context.get_incomplete_color()
        val draw_intensity = p_graphics_context.get_incomplete_color_intensity()
        val circle_radius = 0.5 * Math.abs(p_diameter)
        p_graphics_context.draw_circle(p_location, circle_radius, draw_width.toDouble(), draw_color, p_graphics, draw_intensity)
        val draw_points = Array(2) { FloatPoint(0.0, 0.0) }
        draw_points[0] = FloatPoint(p_location.x - circle_radius, p_location.y)
        draw_points[1] = FloatPoint(p_location.x + circle_radius, p_location.y)
        p_graphics_context.draw(draw_points, draw_width.toDouble(), draw_color, p_graphics, draw_intensity)
        if (p_diameter > 0) {
            // draw also the vertical diameter to create a "+"
            draw_points[0] = FloatPoint(p_location.x, p_location.y - circle_radius)
            draw_points[1] = FloatPoint(p_location.x, p_location.y + circle_radius)
            p_graphics_context.draw(draw_points, draw_width.toDouble(), draw_color, p_graphics, draw_intensity)
        }
    }
}
