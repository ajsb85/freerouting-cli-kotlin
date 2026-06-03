package app.freerouting.drc

import app.freerouting.board.Item
import app.freerouting.geometry.planar.FloatPoint
import app.freerouting.rules.Net

/**
 * Represents an incomplete connection (airline) between two items on the board.
 * Each airline is associated with a net and connects two specific items
 * (from_item and to_item) at specific locations (from_corner and to_corner).
 */
class AirLine(
    @JvmField val net: Net,
    @JvmField val from_item: Item,
    @JvmField val from_corner: FloatPoint,
    @JvmField val to_item: Item,
    @JvmField val to_corner: FloatPoint
) : Comparable<AirLine> {

    override fun compareTo(other: AirLine): Int {
        return this.net.name.compareTo(other.net.name)
    }

    override fun toString(): String {
        return this.net.name + ": " + from_item + " - " + to_item
    }
}
