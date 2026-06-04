package app.freerouting.boardgraphics

import java.awt.Color
import java.awt.Graphics

/**
 * items to be drawn by the functions in GraphicsContext must implement this interface
 */
interface Drawable {

    /**
     * Draws this item to the device provided in p_graphics_context. p_color_arr is an array of dimension layer_count. p_intensity is a number between 0 and 1.
     */
    fun draw(p_g: Graphics, p_graphics_context: GraphicsContext, p_color_arr: Array<Color>, p_intensity: Double)

    /**
     * Draws this item to the device provided in p_graphics_context. It is drawn on each layer with the same color p_color. p_intensity is a number between 0 and 1.
     */
    fun draw(p_g: Graphics, p_graphics_context: GraphicsContext, p_color: Color, p_intensity: Double)

    /**
     * Returns the priority for drawing an item. Items with higher priority are drawn later than items with lower priority.
     */
    fun get_draw_priority(): Int

    /**
     * Gets the drawing intensity in the alpha blending for this item.
     */
    fun get_draw_intensity(p_graphics_context: GraphicsContext): Double

    /**
     * gets the draw colors for this object from p_graphics_context
     */
    fun get_draw_colors(p_graphics_context: GraphicsContext): Array<Color>

    companion object {
        const val MIN_DRAW_PRIORITY: Int = 1
        const val MIDDLE_DRAW_PRIORITY: Int = 3
        const val MAX_DRAW_PRIORITY: Int = 3
    }
}
