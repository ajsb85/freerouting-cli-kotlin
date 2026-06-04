package app.freerouting.geometry.planar

/**
 * Describing the functionality for the fixed directions of a RegularTileShape.
 */
interface ShapeBoundingDirections {

    /**
     * Returns the count of the fixed directions.
     */
    fun count(): Int

    /**
     * Calculates for an arbitrary ConvexShape a surrounding RegularTileShape with this fixed directions. Is used in the implementation of the search trees.
     */
    fun bounds(p_shape: ConvexShape): RegularTileShape

    /**
     * Auxiliary function to implement the same function with parameter type ConvexShape.
     */
    fun bounds(p_box: IntBox): RegularTileShape

    /**
     * Auxiliary function to implement the same function with parameter type ConvexShape.
     */
    fun bounds(p_oct: IntOctagon): RegularTileShape

    /**
     * Auxiliary function to implement the same function with parameter type ConvexShape.
     */
    fun bounds(p_simplex: Simplex): RegularTileShape

    /**
     * Auxiliary function to implement the same function with parameter type ConvexShape.
     */
    fun bounds(p_circle: Circle): RegularTileShape

    /**
     * Auxiliary function to implement the same function with parameter type ConvexShape.
     */
    fun bounds(p_polygon: PolygonShape): RegularTileShape
}
