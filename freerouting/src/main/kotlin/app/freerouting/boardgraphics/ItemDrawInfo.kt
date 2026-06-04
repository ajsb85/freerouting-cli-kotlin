package app.freerouting.boardgraphics

import java.awt.Color

/**
 * Information for drawing an item on the screen.
 */
class ItemDrawInfo(
    @JvmField val layer_color: Array<Color>,
    @JvmField val intensity: Double
)
