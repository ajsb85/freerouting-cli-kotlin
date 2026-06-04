package app.freerouting.geometry.planar

/**
 * Implements the abstract class ShapeDirections as the 4 orthogonal directions. The class is a singleton with the only instantiation INSTANCE.
 */
class OrthogonalBoundingDirections private constructor() : ShapeBoundingDirections {

    override fun count(): Int {
        return 4
    }

    override fun bounds(p_shape: ConvexShape): RegularTileShape {
        return p_shape.bounding_shape(this)
    }

    override fun bounds(p_box: IntBox): RegularTileShape {
        return p_box
    }

    override fun bounds(p_oct: IntOctagon): RegularTileShape {
        return p_oct.bounding_box()
    }

    override fun bounds(p_simplex: Simplex): RegularTileShape {
        return p_simplex.bounding_box()
    }

    override fun bounds(p_circle: Circle): RegularTileShape {
        return p_circle.bounding_box()
    }

    override fun bounds(p_polygon: PolygonShape): RegularTileShape {
        return p_polygon.bounding_box()
    }

    companion object {
        /**
         * the one and only instantiation
         */
        @JvmField
        val INSTANCE = OrthogonalBoundingDirections()
    }
}
