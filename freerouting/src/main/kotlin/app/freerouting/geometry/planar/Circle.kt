package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.text.NumberFormat
import java.util.Locale

/**
 * Describes functionality of a circle shape in the plane.
 */
class Circle(p_center: IntPoint, p_radius: Int) : ConvexShape, Serializable {

    @JvmField
    val center: IntPoint = p_center

    @JvmField
    val radius: Int

    init {
        if (p_radius < 0) {
            FRLogger.warn("Circle: unexpected negative radius")
            radius = -p_radius
        } else {
            radius = p_radius
        }
    }

    override fun is_empty(): Boolean = false

    override fun is_bounded(): Boolean = true

    override fun dimension(): Int {
        if (radius == 0) {
            // circle is reduced to a point
            return 0
        }
        return 2
    }

    override fun circumference(): Double = 2.0 * Math.PI * radius

    override fun area(): Double = (Math.PI * radius) * radius

    override fun centre_of_gravity(): FloatPoint = center.to_float()

    override fun is_outside(p_point: Point): Boolean {
        val fp = p_point.to_float()
        return fp.distance_square(center.to_float()) > radius.toDouble() * radius
    }

    override fun contains(p_point: Point): Boolean = !is_outside(p_point)

    override fun contains_inside(p_point: Point): Boolean {
        val fp = p_point.to_float()
        return fp.distance_square(center.to_float()) < radius.toDouble() * radius
    }

    override fun contains_on_border(p_point: Point): Boolean {
        val fp = p_point.to_float()
        return fp.distance_square(center.to_float()) == radius.toDouble() * radius
    }

    override fun contains(p_point: FloatPoint): Boolean {
        return p_point.distance_square(center.to_float()) <= radius.toDouble() * radius
    }

    override fun distance(p_point: FloatPoint): Double {
        val d = p_point.distance(center.to_float()) - radius
        return Math.max(d, 0.0)
    }

    override fun smallest_radius(): Double = radius.toDouble()

    override fun bounding_box(): IntBox {
        val llx = center.x - radius
        val urx = center.x + radius
        val lly = center.y - radius
        val ury = center.y + radius
        return IntBox(llx, lly, urx, ury)
    }

    override fun bounding_octagon(): IntOctagon {
        val lx = center.x - radius
        val rx = center.x + radius
        val ly = center.y - radius
        val uy = center.y + radius

        val sqrt2Minus1 = Math.sqrt(2.0) - 1.0
        val ceilCornerValue = Math.ceil(sqrt2Minus1 * radius).toInt()
        val floorCornerValue = Math.floor(sqrt2Minus1 * radius).toInt()

        val ulx = lx - (center.y + floorCornerValue)
        val lrx = rx - (center.y - ceilCornerValue)
        val llx = lx + (center.y - floorCornerValue)
        val urx = rx + (center.y + ceilCornerValue)
        return IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx)
    }

    override fun bounding_tile(): TileShape {
        return bounding_octagon()
    }

    /**
     * Creates a bounding tile shape around this circle, so that the length of the line segments of the tile is at most p_max_segment_length.
     */
    fun bounding_tile(p_max_segment_length: Int): TileShape {
        val quadrantDivisionCount = radius / p_max_segment_length + 1
        if (quadrantDivisionCount <= 2) {
            return bounding_octagon()
        }
        val tangentLineArr = Array<Line?>(quadrantDivisionCount * 4) { null }
        for (i in 0 until quadrantDivisionCount) {
            // calculate the tangential points in the first quadrant
            val borderDelta: Vector
            if (i == 0) {
                borderDelta = IntVector(radius, 0)
            } else {
                val currAngle = i * Math.PI / (2.0 * quadrantDivisionCount)
                val currX = Math.ceil(Math.sin(currAngle) * radius).toInt()
                val currY = Math.ceil(Math.cos(currAngle) * radius).toInt()
                borderDelta = IntVector(currX, currY)
            }
            val currA = center.translate_by(borderDelta)
            val currB = currA.turn_90_degree(1, center)
            val currDir = Direction.get_instance(currB.difference_by(center))
            val currTangent = Line(currA, currDir)
            tangentLineArr[quadrantDivisionCount + i] = currTangent
            tangentLineArr[2 * quadrantDivisionCount + i] = currTangent.turn_90_degree(1, center)
            tangentLineArr[3 * quadrantDivisionCount + i] = currTangent.turn_90_degree(2, center)
            tangentLineArr[i] = currTangent.turn_90_degree(3, center)
        }
        @Suppress("UNCHECKED_CAST")
        return TileShape.get_instance(tangentLineArr as Array<Line>)
    }

    override fun is_contained_in(p_box: IntBox): Boolean {
        if (p_box.ll.x > center.x - radius) {
            return false
        }
        if (p_box.ll.y > center.y - radius) {
            return false
        }
        if (p_box.ur.x < center.x + radius) {
            return false
        }
        return p_box.ur.y >= center.y + radius
    }

    override fun turn_90_degree(p_factor: Int, p_pole: IntPoint): Circle {
        val newCenter = center.turn_90_degree(p_factor, p_pole) as IntPoint
        return Circle(newCenter, radius)
    }

    override fun rotate_approx(p_angle: Double, p_pole: FloatPoint): Circle {
        val newCenter = center.to_float().rotate(p_angle, p_pole).round()
        return Circle(newCenter, radius)
    }

    override fun mirror_vertical(p_pole: IntPoint): Circle {
        val newCenter = center.mirror_vertical(p_pole) as IntPoint
        return Circle(newCenter, radius)
    }

    override fun mirror_horizontal(p_pole: IntPoint): Circle {
        val newCenter = center.mirror_horizontal(p_pole) as IntPoint
        return Circle(newCenter, radius)
    }

    override fun max_width(): Double = 2.0 * radius

    override fun min_width(): Double = 2.0 * radius

    override fun bounding_shape(p_dirs: ShapeBoundingDirections): RegularTileShape {
        return p_dirs.bounds(this)
    }

    override fun offset(p_offset: Double): Circle {
        val newRadius = radius + p_offset
        val r = Math.round(newRadius).toInt()
        return Circle(center, r)
    }

    override fun shrink(p_offset: Double): Circle {
        val newRadius = radius - p_offset
        val r = Math.max(Math.round(newRadius).toInt(), 1)
        return Circle(center, r)
    }

    override fun translate_by(p_vector: Vector): Circle {
        if (p_vector.equals(Vector.ZERO)) {
            return this
        }
        if (p_vector !is IntVector) {
            FRLogger.warn("Circle.translate_by only implemented for IntVectors till now")
            return this
        }
        val newCenter = center.translate_by(p_vector) as IntPoint
        return Circle(newCenter, radius)
    }

    override fun nearest_point_approx(p_point: FloatPoint): FloatPoint {
        FRLogger.warn("Circle.nearest_point_approx not yet implemented")
        return center.to_float()
    }

    override fun border_distance(p_point: FloatPoint): Double {
        val d = p_point.distance(center.to_float()) - radius
        return Math.abs(d)
    }

    override fun enlarge(p_offset: Double): Circle {
        if (p_offset == 0.0) {
            return this
        }
        val newRadius = radius + Math.round(p_offset).toInt()
        return Circle(center, newRadius)
    }

    override fun intersects(p_other: Shape): Boolean {
        return p_other.intersects(this)
    }

    override fun cutout(p_polyline: Polyline): Array<Polyline> {
        FRLogger.warn("Circle.cutout not yet implemented")
        return arrayOf(p_polyline)
    }

    override fun intersects(p_other: Circle): Boolean {
        var dSquare = (radius + p_other.radius).toDouble()
        dSquare *= dSquare
        return center.distance_square(p_other.center) <= dSquare
    }

    override fun intersects(p_box: IntBox): Boolean {
        return p_box.distance(center.to_float()) <= radius
    }

    override fun intersects(p_oct: IntOctagon): Boolean {
        return p_oct.distance(center.to_float()) <= radius
    }

    override fun intersects(p_simplex: Simplex): Boolean {
        return p_simplex.distance(center.to_float()) <= radius
    }

    override fun split_to_convex(): Array<TileShape> {
        val result = Array<TileShape?>(1) { null }
        result[0] = bounding_tile()
        @Suppress("UNCHECKED_CAST")
        return result as Array<TileShape>
    }

    override fun get_border(): Circle = this

    override fun get_holes(): Array<Shape> = emptyArray()

    override fun corner_approx_arr(): Array<FloatPoint> = emptyArray()

    override fun toString(): String {
        return to_string(Locale.ENGLISH)
    }

    fun to_string(p_locale: Locale): String {
        var result = "Circle: "
        if (!center.equals(Point.ZERO)) {
            val centerString = "center $center"
            result += centerString
        }
        val nf = NumberFormat.getInstance(p_locale)
        val radiusString = "radius ${nf.format(radius.toLong())}"
        result += radiusString
        return result
    }
}
