package app.freerouting.geometry.planar

/**
 * Implements the abstract class ShapeBoundingDirections as the 8 directions, which are multiples of 45 degree. The class is a singleton with the only instantiation INSTANCE.
 */
class FortyfiveDegreeBoundingDirections private constructor() : ShapeBoundingDirections {

    override fun count(): Int {
        return 8
    }

    override fun bounds(p_shape: ConvexShape): RegularTileShape {
        return p_shape.bounding_shape(this)
    }

    override fun bounds(p_box: IntBox): RegularTileShape {
        return p_box.to_IntOctagon()
    }

    override fun bounds(p_oct: IntOctagon): RegularTileShape {
        return p_oct
    }

    override fun bounds(p_simplex: Simplex): RegularTileShape {
        return p_simplex.bounding_octagon() ?: IntOctagon.EMPTY
    }

    override fun bounds(p_circle: Circle): RegularTileShape {
        return p_circle.bounding_octagon() ?: IntOctagon.EMPTY
    }

    override fun bounds(p_polygon: PolygonShape): RegularTileShape {
        return p_polygon.bounding_octagon() ?: IntOctagon.EMPTY
    }

    companion object {
        /**
         * the one and only instantiation
         */
        @JvmField
        val INSTANCE = FortyfiveDegreeBoundingDirections()
    }
}
