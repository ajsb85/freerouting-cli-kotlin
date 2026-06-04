package app.freerouting.geometry.planar

/**
 * TileShapes whose border lines may have only directions out of a fixed set, as for example orthogonal directions, which define axis parallel box shapes.
 */
abstract class RegularTileShape : TileShape() {

    /**
     * Compares the edgelines of index p_edge_no of this regular TileShape and p_other. returns Side.ON_THE_LEFT, if the edgeline of this simplex is to the left of the edgeline of p_other;
     * Side.COLLINEAR, if the edlines are equal, and Side.ON_THE_RIGHT, if this edgeline is to the right of the edgeline of p_other.
     */
    abstract fun compare(p_other: RegularTileShape, p_edge_no: Int): Side

    /**
     * calculates the smallest RegularTileShape containing this shape and p_other.
     */
    abstract fun union(p_other: RegularTileShape): RegularTileShape

    /**
     * returns true, if p_other is completely contained in this shape
     */
    abstract fun contains(p_other: RegularTileShape): Boolean

    /**
     * Auxiliary function to implement the same function with parameter type RegularTileShape.
     */
    abstract fun compare(p_other: IntBox, p_edge_no: Int): Side

    /**
     * Auxiliary function to implement the same function with parameter type RegularTileShape.
     */
    abstract fun compare(p_other: IntOctagon, p_edge_no: Int): Side

    /**
     * Auxiliary function to implement the same function with parameter type RegularTileShape.
     */
    abstract fun union(p_other: IntBox): RegularTileShape

    /**
     * Auxiliary function to implement the same function with parameter type RegularTileShape.
     */
    abstract fun union(p_other: IntOctagon): RegularTileShape

    abstract override fun is_contained_in(p_other: IntBox): Boolean

    /**
     * Auxiliary function to implement the same function with parameter type RegularTileShape.
     */
    abstract fun is_contained_in(p_other: IntOctagon): Boolean
}
