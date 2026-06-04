package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.*

/**
 * Implements a point in the plane as a tuple of double's. Because arithmetic calculations with doubles are in general not exact, FloatPoint is not derived from the abstract class Point.
 */
class FloatPoint : Serializable {

    /**
     * the x coordinate of this point
     */
    @JvmField
    val x: Double

    /**
     * the y coordinate of this point
     */
    @JvmField
    val y: Double

    /**
     * creates an instance of class FloatPoint from two doubles,
     */
    constructor(p_x: Double, p_y: Double) {
        x = p_x
        y = p_y
    }

    constructor(p_pt: IntPoint) {
        x = p_pt.x.toDouble()
        y = p_pt.y.toDouble()
    }

    // static bounding_octagon method
    companion object {
        @JvmField
        val ZERO = FloatPoint(0.0, 0.0)

        /**
         * Calculates the smallest IntOctagon containing all the input points
         */
        @JvmStatic
        fun bounding_octagon(p_point_arr: Array<FloatPoint>): IntOctagon {
            var lx = Int.MAX_VALUE.toDouble()
            var ly = Int.MAX_VALUE.toDouble()
            var rx = Int.MIN_VALUE.toDouble()
            var uy = Int.MIN_VALUE.toDouble()
            var ulx = Int.MAX_VALUE.toDouble()
            var lrx = Int.MIN_VALUE.toDouble()
            var llx = Int.MAX_VALUE.toDouble()
            var urx = Int.MIN_VALUE.toDouble()
            for (curr in p_point_arr) {
                lx = min(lx, curr.x)
                ly = min(ly, curr.y)
                rx = max(rx, curr.x)
                uy = max(uy, curr.y)
                var tmp = curr.x - curr.y
                ulx = min(ulx, tmp)
                lrx = max(lrx, tmp)
                tmp = curr.x + curr.y
                llx = min(llx, tmp)
                urx = max(urx, tmp)
            }
            return IntOctagon(
                floor(lx).toInt(),
                floor(ly).toInt(),
                ceil(rx).toInt(),
                ceil(uy).toInt(),
                floor(ulx).toInt(),
                ceil(lrx).toInt(),
                floor(llx).toInt(),
                ceil(urx).toInt()
            )
        }
    }

    /**
     * returns the square of the distance from this point to the zero point
     */
    fun size_square(): Double {
        return x * x + y * y
    }

    /**
     * returns the distance from this point to the zero point
     */
    fun size(): Double {
        return sqrt(size_square())
    }

    /**
     * returns the square of the distance from this Point to the Point p_other
     */
    fun distance_square(p_other: FloatPoint): Double {
        val dx = p_other.x - x
        val dy = p_other.y - y
        return dx * dx + dy * dy
    }

    /**
     * returns the distance from this point to the point p_other
     */
    fun distance(p_other: FloatPoint): Double {
        return sqrt(distance_square(p_other))
    }

    /**
     * Computes the weighted distance to p_other.
     */
    fun weighted_distance(p_other: FloatPoint, p_horizontal_weight: Double, p_vertical_weight: Double): Double {
        var delta_x = this.x - p_other.x
        var delta_y = this.y - p_other.y
        delta_x *= p_horizontal_weight
        delta_y *= p_vertical_weight
        return sqrt(delta_x * delta_x + delta_y * delta_y)
    }

    /**
     * rounds the coordinates from an object of class Point_double to an object of class IntPoint
     */
    fun round(): IntPoint {
        return IntPoint(round(x).toInt(), round(y).toInt())
    }

    /**
     * Rounds this point, so that if this point is on the right side of any directed line with direction p_dir, the result point will also be on the right side.
     */
    fun round_to_the_right(p_dir: Direction): IntPoint {
        val dir = p_dir.get_vector().to_float()
        val rounded_x: Int = when {
            dir.y > 0 -> ceil(x).toInt()
            dir.y < 0 -> floor(x).toInt()
            else -> round(x).toInt()
        }

        val rounded_y: Int = when {
            dir.x > 0 -> floor(y).toInt()
            dir.x < 0 -> ceil(y).toInt()
            else -> round(y).toInt()
        }
        return IntPoint(rounded_x, rounded_y)
    }

    /**
     * Round this Point so the x coordinate of the result will be a multiple of p_horizontal_grid and the y coordinate a multiple of p_vertical_grid.
     */
    fun round_to_grid(p_horizontal_grid: Int, p_vertical_grid: Int): IntPoint {
        val rounded_x: Double = if (p_horizontal_grid > 0) {
            val hg = p_horizontal_grid.toDouble()
            java.lang.Math.rint(this.x / hg) * hg
        } else {
            this.x
        }
        val rounded_y: Double = if (p_vertical_grid > 0) {
            val vg = p_vertical_grid.toDouble()
            java.lang.Math.rint(this.y / vg) * vg
        } else {
            this.y
        }
        return IntPoint(rounded_x.toInt(), rounded_y.toInt())
    }

    /**
     * Rounds this point, so that if this point is on the left side of any directed line with direction p_dir, the result point will also be on the left side.
     */
    fun round_to_the_left(p_dir: Direction): IntPoint {
        val dir = p_dir.get_vector().to_float()
        val rounded_x: Int = when {
            dir.y > 0 -> floor(x).toInt()
            dir.y < 0 -> ceil(x).toInt()
            else -> round(x).toInt()
        }

        val rounded_y: Int = when {
            dir.x > 0 -> ceil(y).toInt()
            dir.x < 0 -> floor(y).toInt()
            else -> round(y).toInt()
        }
        return IntPoint(rounded_x, rounded_y)
    }

    /**
     * Adds the coordinates of this FloatPoint and p_other.
     */
    fun add(p_other: FloatPoint): FloatPoint {
        return FloatPoint(this.x + p_other.x, this.y + p_other.y)
    }

    /**
     * Substracts the coordinates of p_other from this FloatPoint.
     */
    fun substract(p_other: FloatPoint): FloatPoint {
        return FloatPoint(this.x - p_other.x, this.y - p_other.y)
    }

    /**
     * Returns an approximation of the perpendicular projection of this point onto p_line
     */
    fun projection_approx(p_line: Line): FloatPoint {
        val line = FloatLine(p_line.a.to_float(), p_line.b.to_float())
        return line.perpendicular_projection(this)
    }

    /**
     * Calculates the scalar product of (p_1 - this). with (p_2 - this).
     */
    fun scalar_product(p_1: FloatPoint?, p_2: FloatPoint?): Double {
        if (p_1 == null || p_2 == null) {
            FRLogger.warn("FloatPoint.scalar_product: parameter point is null")
            return 0.0
        }
        val dx_1 = p_1.x - this.x
        val dx_2 = p_2.x - this.x
        val dy_1 = p_1.y - this.y
        val dy_2 = p_2.y - this.y
        return dx_1 * dx_2 + dy_1 * dy_2
    }

    /**
     * Approximates a FloatPoint on the line from zero to this point with distance p_new_length from zero.
     */
    fun change_size(p_new_size: Double): FloatPoint {
        if (x == 0.0 && y == 0.0) {
            // the size of the zero point cannot be changed
            return this
        }
        val length = sqrt(x * x + y * y)
        val new_x = (x * p_new_size) / length
        val new_y = (y * p_new_size) / length
        return FloatPoint(new_x, new_y)
    }

    /**
     * Approximates a FloatPoint on the line from this point to p_to_point with distance p_new_length from this point.
     */
    fun change_length(p_to_point: FloatPoint, p_new_length: Double): FloatPoint {
        val dx = p_to_point.x - this.x
        val dy = p_to_point.y - this.y
        if (dx == 0.0 && dy == 0.0) {
            FRLogger.warn("IntPoint.change_length: Points are equal")
            return p_to_point
        }
        val length = sqrt(dx * dx + dy * dy)
        val new_x = this.x + (dx * p_new_length) / length
        val new_y = this.y + (dy * p_new_length) / length
        return FloatPoint(new_x, new_y)
    }

    /**
     * Returns the middle point between this point and p_to_point.
     */
    fun middle_point(p_to_point: FloatPoint): FloatPoint {
        if (p_to_point === this) {
            return this
        }
        val middle_x = 0.5 * (this.x + p_to_point.x)
        val middle_y = 0.5 * (this.y + p_to_point.y)
        return FloatPoint(middle_x, middle_y)
    }

    /**
     * The function returns Side.ON_THE_LEFT, if this Point is on the left of the line from p_1 to p_2; and Side.ON_THE_RIGHT, if this Point is on the right of the line from p_1 to p_2. Collinearity is
     * not defined, because numerical calculations ar not exact for FloatPoints.
     */
    fun side_of(p_1: FloatPoint, p_2: FloatPoint): Side {
        val d21_x = p_2.x - p_1.x
        val d21_y = p_2.y - p_1.y
        val d01_x = this.x - p_1.x
        val d01_y = this.y - p_1.y
        val determinant = d21_x * d01_y - d21_y * d01_x
        return Side.of(determinant)
    }

    /**
     * Rotates this FloatPoints by p_angle ( in radian ) around the p_pole.
     */
    fun rotate(p_angle: Double, p_pole: FloatPoint): FloatPoint {
        if (p_angle == 0.0) {
            return this
        }

        val dx = x - p_pole.x
        val dy = y - p_pole.y
        val sin_angle = sin(p_angle)
        val cos_angle = cos(p_angle)
        val new_dx = dx * cos_angle - dy * sin_angle
        val new_dy = dx * sin_angle + dy * cos_angle
        return FloatPoint(p_pole.x + new_dx, p_pole.y + new_dy)
    }

    /**
     * Turns this FloatPoint by p_factor times 90 degree around ZERO.
     */
    fun turn_90_degree(p_factor: Int): FloatPoint {
        var n = p_factor
        while (n < 0) {
            n += 4
        }
        while (n >= 4) {
            n -= 4
        }
        val new_x: Double
        val new_y: Double
        when (n) {
            0 -> { // 0 degree
                new_x = x
                new_y = y
            }
            1 -> { // 90 degree
                new_x = -y
                new_y = x
            }
            2 -> { // 180 degree
                new_x = -x
                new_y = -y
            }
            3 -> { // 270 degree
                new_x = y
                new_y = -x
            }
            else -> {
                new_x = 0.0
                new_y = 0.0
            }
        }
        return FloatPoint(new_x, new_y)
    }

    /**
     * Turns this FloatPoint by p_factor times 90 degree around p_pole.
     */
    fun turn_90_degree(p_factor: Int, p_pole: FloatPoint): FloatPoint {
        val v = this.substract(p_pole)
        val turned_v = v.turn_90_degree(p_factor)
        return p_pole.add(turned_v)
    }

    /**
     * Checks, if this point is contained in the box spanned by p_1 and p_2 with the input tolerance.
     */
    fun is_contained_in_box(p_1: FloatPoint, p_2: FloatPoint, p_tolerance: Double): Boolean {
        val min_x: Double
        val max_x: Double
        if (p_1.x < p_2.x) {
            min_x = p_1.x
            max_x = p_2.x
        } else {
            min_x = p_2.x
            max_x = p_1.x
        }
        if (this.x < min_x - p_tolerance || this.x > max_x + p_tolerance) {
            return false
        }
        val min_y: Double
        val max_y: Double
        if (p_1.y < p_2.y) {
            min_y = p_1.y
            max_y = p_2.y
        } else {
            min_y = p_2.y
            max_y = p_1.y
        }
        return this.y >= min_y - p_tolerance && this.y <= max_y + p_tolerance
    }

    /**
     * Creates the smallest IntBox containing this point.
     */
    fun bounding_box(): IntBox {
        val lower_left = IntPoint(floor(this.x).toInt(), floor(this.y).toInt())
        val upper_right = IntPoint(ceil(this.x).toInt(), ceil(this.y).toInt())
        return IntBox(lower_left, upper_right)
    }

    /**
     * Calculates the touching points of the tangents from this point to a circle around p_to_point with radius p_distance. Solves the quadratic equation, which results by substituting x by the term in
     * y from the equation of the polar line of a circle with center p_to_point and radius p_distance and putting it into the circle equation. The polar line is the line through the 2 tangential points
     * of the circle looked at from this point and has the equation (this.x - p_to_point.x) * (x - p_to_point.x) + (this.y - p_to_point.y) * (y - p_to_point.y) = p_distance **2
     */
    fun tangential_points(p_to_point: FloatPoint, p_distance: Double): Array<FloatPoint> {
        // turn the situation 90 degree if the x difference is smaller
        // than the y difference for better numerical stability

        var dx = abs(this.x - p_to_point.x)
        var dy = abs(this.y - p_to_point.y)
        val situation_turned = dy > dx
        val pole: FloatPoint
        val circle_center: FloatPoint

        if (situation_turned) {
            // turn the situation by 90 degree
            pole = FloatPoint(-this.y, this.x)
            circle_center = FloatPoint(-p_to_point.y, p_to_point.x)
        } else {
            pole = this
            circle_center = p_to_point
        }

        dx = pole.x - circle_center.x
        dy = pole.y - circle_center.y
        val dx_square = dx * dx
        val dy_square = dy * dy
        val dist_square = dx_square + dy_square
        val radius_square = p_distance * p_distance
        val discriminant = radius_square * dy_square - (radius_square - dx_square) * dist_square

        if (discriminant <= 0) {
            // pole is inside the circle.
            return emptyArray()
        }
        val square_root = sqrt(discriminant)

        val result = Array(2) { FloatPoint.ZERO }

        val a1 = radius_square * dy
        val dy1 = (a1 + p_distance * square_root) / dist_square
        val dy2 = (a1 - p_distance * square_root) / dist_square

        val first_point_y = dy1 + circle_center.y
        val first_point_x = (radius_square - dy * dy1) / dx + circle_center.x
        val second_point_y = dy2 + circle_center.y
        val second_point_x = (radius_square - dy * dy2) / dx + circle_center.x

        if (situation_turned) {
            // turn the result by 270 degree
            result[0] = FloatPoint(first_point_y, -first_point_x)
            result[1] = FloatPoint(second_point_y, -second_point_x)
        } else {
            result[0] = FloatPoint(first_point_x, first_point_y)
            result[1] = FloatPoint(second_point_x, second_point_y)
        }
        return result
    }

    /**
     * Calculates the left tangential point of the line from this point to a circle around p_to_point with radius p_distance. Returns null, if this point is inside this circle.
     */
    fun left_tangential_point(p_to_point: FloatPoint?, p_distance: Double): FloatPoint? {
        if (p_to_point == null) {
            return null
        }
        val tangent_points = tangential_points(p_to_point, p_distance)
        if (tangent_points.size < 2) {
            return null
        }
        val result: FloatPoint = if (p_to_point.side_of(this, tangent_points[0]) == Side.ON_THE_RIGHT) {
            tangent_points[0]
        } else {
            tangent_points[1]
        }
        return result
    }

    /**
     * Calculates the right tangential point of the line from this point to a circle around p_to_point with radius p_distance. Returns null, if this point is inside this circle.
     */
    fun right_tangential_point(p_to_point: FloatPoint?, p_distance: Double): FloatPoint? {
        if (p_to_point == null) {
            return null
        }
        val tangent_points = tangential_points(p_to_point, p_distance)
        if (tangent_points.size < 2) {
            return null
        }
        val result: FloatPoint = if (p_to_point.side_of(this, tangent_points[0]) == Side.ON_THE_LEFT) {
            tangent_points[0]
        } else {
            tangent_points[1]
        }
        return result
    }

    /**
     * Calculates the center of the circle through this point, p_1 and p_2 by calculating the intersection of the two lines perpendicular to and passing through the midpoints of the lines (this, p_1)
     * and (p_1, p_2).
     */
    fun circle_center(p_1: FloatPoint, p_2: FloatPoint): FloatPoint {
        val slope_1 = (p_1.y - this.y) / (p_1.x - this.x)
        val slope_2 = (p_2.y - p_1.y) / (p_2.x - p_1.x)
        val x_center = (slope_1 * slope_2 * (this.y - p_2.y) + slope_2 * (this.x + p_1.x) - slope_1 * (p_1.x + p_2.x)) / (2 * (slope_2 - slope_1))
        val y_center = (0.5 * (this.x + p_1.x) - x_center) / slope_1 + 0.5 * (this.y + p_1.y)
        return FloatPoint(x_center, y_center)
    }

    /**
     * Returns true, if this point is contained in the circle through p_1, p_2 and p_3.
     */
    fun inside_circle(p_1: FloatPoint, p_2: FloatPoint, p_3: FloatPoint): Boolean {
        val center = p_1.circle_center(p_2, p_3)
        val radius_square = center.distance_square(p_1)
        return this.distance_square(center) < radius_square - 1 // - 1 is a tolerance for numerical stability.
    }

    fun to_string(p_locale: Locale): String {
        val nf = NumberFormat.getInstance(p_locale)
        nf.maximumFractionDigits = 4
        return "(" + nf.format(x) + " , " + nf.format(y) + ")"
    }

    fun to_string(p_locale: Locale, fractionDigits: Int, padding: Int): String {
        val nf = NumberFormat.getInstance(p_locale)
        nf.minimumFractionDigits = fractionDigits
        nf.maximumFractionDigits = fractionDigits
        return "X " + String.format("%" + padding + "s", nf.format(x)) + "   Y " + String.format("%" + padding + "s", nf.format(-y))
    }

    override fun toString(): String {
        return to_string(Locale.ENGLISH)
    }
}
