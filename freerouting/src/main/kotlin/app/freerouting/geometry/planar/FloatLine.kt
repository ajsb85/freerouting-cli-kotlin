package app.freerouting.geometry.planar

import app.freerouting.logger.FRLogger

/**
 * Defines a line in the plane by to FloatPoints. Calculations with FloatLines are generally not exact. For that reason collinear for example is not defined for FloatLines. If exactness is needed, use
 * the class Line instead.
 */
class FloatLine(p_a: FloatPoint?, p_b: FloatPoint?) {

    @JvmField
    val a: FloatPoint
    @JvmField
    val b: FloatPoint

    init {
        if (p_a == null || p_b == null) {
            FRLogger.debug("FloatLine: one or both endpoints are null (degenerate line segment)")
        }
        a = p_a ?: FloatPoint(0.0, 0.0)
        b = p_b ?: FloatPoint(0.0, 0.0)
    }

    /**
     * Returns the FloatLine with swapped end points.
     */
    fun opposite(): FloatLine {
        return FloatLine(this.b, this.a)
    }

    fun adjust_direction(p_other: FloatLine): FloatLine {
        if (this.b.side_of(this.a, p_other.a) == p_other.b.side_of(this.a, p_other.a)) {
            return this
        }
        return this.opposite()
    }

    /**
     * Calculates the intersection of this line with p_other. Returns null, if the lines are parallel.
     */
    fun intersection(p_other: FloatLine): FloatPoint? {
        val d1x = this.b.x - this.a.x
        val d1y = this.b.y - this.a.y
        val d2x = p_other.b.x - p_other.a.x
        val d2y = p_other.b.y - p_other.a.y
        val det_1 = this.a.x * this.b.y - this.a.y * this.b.x
        val det_2 = p_other.a.x * p_other.b.y - p_other.a.y * p_other.b.x
        val det = d2x * d1y - d2y * d1x
        if (det == 0.0) {
            return null
        }
        val is_x = (d2x * det_1 - d1x * det_2) / det
        val is_y = (d2y * det_1 - d1y * det_2) / det
        return FloatPoint(is_x, is_y)
    }

    /**
     * translates the line perpendicular at about p_dist. If p_dist > 0, the line will be translated to the left, else to the right
     */
    fun translate(p_dist: Double): FloatLine {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dxdx = dx * dx
        val dydy = dy * dy
        val length = Math.sqrt(dxdx + dydy)
        val new_a: FloatPoint
        if (dxdx <= dydy) {
            // translate along the x axis
            val rel_x = (p_dist * length) / dy
            new_a = FloatPoint(this.a.x - rel_x, this.a.y)
        } else {
            // translate along the y axis
            val rel_y = (p_dist * length) / dx
            new_a = FloatPoint(this.a.x, this.a.y + rel_y)
        }
        val new_b = FloatPoint(new_a.x + dx, new_a.y + dy)
        return FloatLine(new_a, new_b)
    }

    /**
     * Returns the signed distance of this line from p_point. The result will be positive, if the line is on the left of p_point, else negative.
     */
    fun signed_distance(p_point: FloatPoint): Double {
        val dx = this.b.x - this.a.x
        val dy = this.b.y - this.a.y
        val det = dy * (p_point.x - this.a.x) - dx * (p_point.y - this.a.y)
        // area of the parallelogramm spanned by the 3 points
        val length = Math.sqrt(dx * dx + dy * dy)
        return det / length
    }

    /**
     * Returns an approximation of the perpensicular projection of p_point onto this line.
     */
    fun perpendicular_projection(p_point: FloatPoint): FloatPoint {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (dx == 0.0 && dy == 0.0) {
            return this.a
        }

        val dxdx = dx * dx
        val dydy = dy * dy
        val dxdy = dx * dy
        val denominator = dxdx + dydy
        val det = a.x * b.y - b.x * a.y

        val x = (p_point.x * dxdx + p_point.y * dxdy + det * dy) / denominator
        val y = (p_point.x * dxdy + p_point.y * dydy - det * dx) / denominator

        return FloatPoint(x, y)
    }

    /**
     * Returns the distance of p_point to the nearest point of this line between this.a and this.b.
     */
    fun segment_distance(p_point: FloatPoint): Double {
        val projection = perpendicular_projection(p_point)
        val result: Double
        if (projection.is_contained_in_box(this.a, this.b, 0.01)) {
            result = p_point.distance(projection)
        } else {
            result = Math.min(p_point.distance(a), p_point.distance(b))
        }
        return result
    }

    /**
     * Returns the perpendicular projection of p_line_segment onto this oriented line segment, Returns null, if the projection is empty.
     */
    fun segment_projection(p_line_segment: FloatLine): FloatLine? {
        if (this.b.scalar_product(this.a, p_line_segment.a) < 0.0) {
            return null
        }
        if (this.a.scalar_product(this.b, p_line_segment.b) < 0.0) {
            return null
        }
        val projected_a: FloatPoint
        if (this.a.scalar_product(this.b, p_line_segment.a) < 0.0) {
            projected_a = this.a
        } else {
            projected_a = this.perpendicular_projection(p_line_segment.a)
            if (Math.abs(projected_a.x) >= Limits.CRIT_INT || Math.abs(projected_a.y) >= Limits.CRIT_INT) {
                return null
            }
        }
        val projected_b: FloatPoint
        if (this.b.scalar_product(this.a, p_line_segment.b) < 0.0) {
            projected_b = this.b
        } else {
            projected_b = this.perpendicular_projection(p_line_segment.b)
        }
        if (Math.abs(projected_b.x) >= Limits.CRIT_INT || Math.abs(projected_b.y) >= Limits.CRIT_INT) {
            return null
        }
        return FloatLine(projected_a, projected_b)
    }

    /**
     * Returns the projection of p_line_segment onto this oriented line segment by moving p_line_segment perpendicular into the direction of this line segment Returns null, if the projection is empty or
     * p_line_segment.a == p_line_segment.b
     */
    fun segment_projection_2(p_line_segment: FloatLine): FloatLine? {
        if (p_line_segment.a.scalar_product(p_line_segment.b, this.b) <= 0.0) {
            return null
        }
        if (p_line_segment.b.scalar_product(p_line_segment.a, this.a) <= 0.0) {
            return null
        }
        val projected_a: FloatPoint
        if (p_line_segment.a.scalar_product(p_line_segment.b, this.a) < 0.0) {
            val curr_perpendicular_line = FloatLine(p_line_segment.a, p_line_segment.b.turn_90_degree(1, p_line_segment.a))
            val isect = curr_perpendicular_line.intersection(this) ?: return null
            projected_a = isect
            if (Math.abs(projected_a.x) >= Limits.CRIT_INT || Math.abs(projected_a.y) >= Limits.CRIT_INT) {
                return null
            }
        } else {
            projected_a = this.a
        }

        val projected_b: FloatPoint

        if (p_line_segment.b.scalar_product(p_line_segment.a, this.b) < 0.0) {
            val curr_perpendicular_line = FloatLine(p_line_segment.b, p_line_segment.a.turn_90_degree(1, p_line_segment.b))
            val isect = curr_perpendicular_line.intersection(this) ?: return null
            projected_b = isect
            if (Math.abs(projected_b.x) >= Limits.CRIT_INT || Math.abs(projected_b.y) >= Limits.CRIT_INT) {
                return null
            }
        } else {
            projected_b = this.b
        }
        return FloatLine(projected_a, projected_b)
    }

    /**
     * Shrinks this line on both sides by p_value. The result will contain at least the gravity point of the line.
     */
    fun shrink_segment(p_offset: Double): FloatLine {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (dx == 0.0 && dy == 0.0) {
            return this
        }
        val length = Math.sqrt(dx * dx + dy * dy)
        val offset = Math.min(p_offset, length / 2.0)
        val new_a = FloatPoint(a.x + (dx * offset) / length, a.y + (dy * offset) / length)
        val new_length = length - offset
        val new_b = FloatPoint(a.x + (dx * new_length) / length, a.y + (dy * new_length) / length)
        return FloatLine(new_a, new_b)
    }

    /**
     * Calculates the nearest point on this line to p_from_point between this.a and this.b.
     */
    fun nearest_segment_point(p_from_point: FloatPoint): FloatPoint {
        val projection = this.perpendicular_projection(p_from_point)
        if (projection.is_contained_in_box(this.a, this.b, 0.01)) {
            return projection
        }
        // Now the projection is outside the line segment.
        val result: FloatPoint
        if (p_from_point.distance_square(this.a) <= p_from_point.distance_square(this.b)) {
            result = this.a
        } else {
            result = this.b
        }
        return result
    }

    /**
     * Divides this line segment into p_count line segments of nearly equal length. and at most p_max_section_length.
     */
    fun divide_segment_into_sections(p_count: Int): Array<FloatLine> {
        if (p_count == 0) {
            return emptyArray()
        }
        if (p_count == 1) {
            val result = arrayOfNulls<FloatLine>(1)
            result[0] = this
            return result.requireNoNulls()
        }
        val line_length = this.b.distance(this.a)
        val result = arrayOfNulls<FloatLine>(p_count)
        val section_length = line_length / p_count
        val dx = b.x - a.x
        val dy = b.y - a.y
        var curr_a = this.a
        for (i in 0 until p_count) {
            val curr_b: FloatPoint
            if (i == p_count - 1) {
                curr_b = this.b
            } else {
                val curr_b_dist = (i + 1) * section_length
                val curr_b_x = a.x + (dx * curr_b_dist) / line_length
                val curr_b_y = a.y + (dy * curr_b_dist) / line_length
                curr_b = FloatPoint(curr_b_x, curr_b_y)
            }
            result[i] = FloatLine(curr_a, curr_b)
            curr_a = curr_b
        }
        return result.requireNoNulls()
    }
}
