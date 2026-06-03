package app.freerouting.datastructures

import app.freerouting.geometry.planar.IntPoint
import app.freerouting.geometry.planar.Limits
import app.freerouting.geometry.planar.Point
import app.freerouting.geometry.planar.Side
import app.freerouting.logger.FRLogger
import java.util.Collections
import java.util.LinkedList
import java.util.Random
import java.util.TreeSet

/**
 * Creates a Delaunay triangulation in the plane for the input objects. The objects in the input list must implement the interface PlanarDelaunayTriangulation.Storable, which consists of the method
 * get_triangulation_corners(). The result can be read by the function get_edge_lines(). The algorithm is from Chapter 9.3. of the book Computational Geometry, Algorithms and Applications from M. de
 * Berg, M. van Kreveld, M Overmars and O Schwarzkopf.
 */
class PlanarDelaunayTriangulation(p_object_list: Collection<Storable>) {

    private val search_graph: TriangleGraph
    private val degenerate_edges: MutableCollection<Edge> = LinkedList()
    private var last_edge_id_no: Int = 0

    init {
        val corner_list = LinkedList<Corner>()
        for (curr_object in p_object_list) {
            val curr_corners = curr_object.get_triangulation_corners()
            for (curr_corner in curr_corners) {
                corner_list.add(Corner(curr_object, curr_corner))
            }
        }

        // create a random permutation of the corners.
        // use a fixed seed to get reproducible result
        random_generator.setSeed(seed.toLong())
        Collections.shuffle(corner_list, random_generator)

        // create a big triangle containing all corners in the list to start with.
        val bounding_coor = Limits.CRIT_INT
        val bounding_corners = arrayOf(
            Corner(null, IntPoint(bounding_coor, 0)),
            Corner(null, IntPoint(0, bounding_coor)),
            Corner(null, IntPoint(-bounding_coor, -bounding_coor))
        )

        val edge_lines = arrayOfNulls<Edge>(3)
        for (i in 0 until 2) {
            edge_lines[i] = Edge(bounding_corners[i], bounding_corners[i + 1])
        }
        edge_lines[2] = Edge(bounding_corners[2], bounding_corners[0])

        val start_triangle = Triangle(edge_lines.filterNotNull().toTypedArray(), null)

        // Set the left triangle of the edge lines to start_triangle.
        // The right triangles remains null.
        for (curr_edge in edge_lines) {
            curr_edge?.left_triangle = start_triangle
        }

        // Initialize the search graph.
        this.search_graph = TriangleGraph(start_triangle)

        // Insert the corners in the corner list into the search graph.
        for (curr_corner in corner_list) {
            val triangle_to_split = this.search_graph.position_locate(curr_corner)
            if (triangle_to_split != null) {
                this.split(triangle_to_split, curr_corner)
            }
        }
    }

    /**
     * Returns all edge lines of the result of the Delaunay Triangulation.
     */
    fun get_edge_lines(): Collection<ResultEdge> {
        val result = LinkedList<ResultEdge>()
        for (curr_edge in this.degenerate_edges) {
            result.add(
                ResultEdge(
                    curr_edge.start_corner.coor,
                    curr_edge.start_corner.`object`,
                    curr_edge.end_corner.coor,
                    curr_edge.end_corner.`object`
                )
            )
        }
        val anchorNode = this.search_graph.anchor
        if (anchorNode != null) {
            val result_edges = TreeSet<Edge>()
            anchorNode.get_leaf_edges(result_edges)
            for (curr_edge in result_edges) {
                result.add(
                    ResultEdge(
                        curr_edge.start_corner.coor,
                        curr_edge.start_corner.`object`,
                        curr_edge.end_corner.coor,
                        curr_edge.end_corner.`object`
                    )
                )
            }
        }
        return result
    }

    /**
     * Splits p_triangle into 3 new triangles at p_corner, if p_corner lies in the interior. If p_corner lies on the border, p_triangle and the corresponding neighbour are split into 2 new triangles
     * each at p_corner. If p_corner lies outside this triangle or on a corner, nothing is split. In this case the function returns false.
     */
    private fun split(p_triangle: Triangle, p_corner: Corner): Boolean {
        // check, if p_corner is in the interior of this triangle or
        // if p_corner is contained in an edge line.
        var containing_edge: Edge? = null
        for (i in 0 until 3) {
            val curr_edge = p_triangle.edge_lines[i]
            val curr_side = if (curr_edge.left_triangle === p_triangle) {
                p_corner.side_of(curr_edge.start_corner, curr_edge.end_corner)
            } else {
                p_corner.side_of(curr_edge.end_corner, curr_edge.start_corner)
            }
            if (curr_side == Side.ON_THE_RIGHT) {
                // p_corner is outside this triangle
                FRLogger.warn("PlanarDelaunayTriangulation.split: p_corner is outside")
                return false
            } else if (curr_side == Side.COLLINEAR) {
                if (containing_edge != null) {
                    // p_corner is equal to a corner of this triangle
                    val common_corner = curr_edge.common_corner(containing_edge)
                    if (common_corner == null) {
                        FRLogger.warn("PlanarDelaunayTriangulation.split: common corner expected")
                        return false
                    }
                    if (p_corner.`object` === common_corner.`object`) {
                        return false
                    }
                    this.degenerate_edges.add(Edge(p_corner, common_corner))
                    return true
                }
                containing_edge = curr_edge
            }
        }

        if (containing_edge == null) {
            // split p_triangle into 3 new triangles by adding edges from
            // the corners of  p_triangle to p_corner.
            val new_triangles = p_triangle.split_at_inner_point(p_corner) ?: return false

            for (curr_triangle in new_triangles) {
                this.search_graph.insert(curr_triangle, p_triangle)
            }

            for (i in 0 until 3) {
                legalize_edge(p_corner, p_triangle.edge_lines[i])
            }
        } else {
            // split this triangle and the neighbour triangle into 4 new triangles by adding edges from
            // the corners of the triangles to p_corner.
            val neighbour_to_split = containing_edge.other_neighbour(p_triangle) ?: return false

            val new_triangles = p_triangle.split_at_border_point(p_corner, neighbour_to_split) ?: return false

            // There are exact four new triangles with the first 2 dividing p_triangle and
            // the last 2 dividing neighbour_to_split.
            this.search_graph.insert(new_triangles[0], p_triangle)
            this.search_graph.insert(new_triangles[1], p_triangle)
            this.search_graph.insert(new_triangles[2], neighbour_to_split)
            this.search_graph.insert(new_triangles[3], neighbour_to_split)

            for (i in 0 until 3) {
                val curr_edge = p_triangle.edge_lines[i]
                if (curr_edge !== containing_edge) {
                    legalize_edge(p_corner, curr_edge)
                }
            }
            for (i in 0 until 3) {
                val curr_edge = neighbour_to_split.edge_lines[i]
                if (curr_edge !== containing_edge) {
                    legalize_edge(p_corner, curr_edge)
                }
            }
        }
        return true
    }

    /**
     * Flips p_edge, if it is no legal edge of the Delaunay Triangulation. p_corner is the last inserted corner of the triangulation Return true, if the triangulation was changed.
     */
    private fun legalize_edge(p_corner: Corner, p_edge: Edge): Boolean {
        if (p_edge.is_legal()) {
            return false
        }
        val leftTriangle = p_edge.left_triangle ?: return false
        val rightTriangle = p_edge.right_triangle ?: return false

        val triangle_to_change = when (p_corner) {
            leftTriangle.opposite_corner(p_edge) -> rightTriangle
            rightTriangle.opposite_corner(p_edge) -> leftTriangle
            else -> {
                FRLogger.warn("PlanarDelaunayTriangulation.legalize_edge: edge lines inconsistent")
                return false
            }
        }
        val flipped_edge = p_edge.flip() ?: return false

        // Update the search graph.
        this.search_graph.insert(flipped_edge.left_triangle!!, leftTriangle)
        this.search_graph.insert(flipped_edge.right_triangle!!, leftTriangle)
        this.search_graph.insert(flipped_edge.left_triangle!!, rightTriangle)
        this.search_graph.insert(flipped_edge.right_triangle!!, rightTriangle)

        // Call this function recursively for the other edge lines of triangle_to_change.
        for (i in 0 until 3) {
            val curr_edge = triangle_to_change.edge_lines[i]
            if (curr_edge !== p_edge) {
                legalize_edge(p_corner, curr_edge)
            }
        }
        return true
    }

    /**
     * Checks the consistency of the triangles in this triangulation. Used for debugging purposes.
     */
    fun validate(): Boolean {
        val result = this.search_graph.anchor?.validate() ?: false
        if (result) {
            FRLogger.warn("Delaunay triangulation check passed ok")
        } else {
            FRLogger.warn("Delaunay triangulation check has detected problems")
        }
        return result
    }

    /**
     * Creates a new unique edge id number.
     */
    private fun new_edge_id_no(): Int {
        ++this.last_edge_id_no
        return this.last_edge_id_no
    }

    /**
     * Interface with functionality required for objects to be used in a planar triangulation.
     */
    interface Storable {
        /**
         * Returns an array of corners, which can be used in a planar triangulation.
         */
        fun get_triangulation_corners(): Array<Point>
    }

    /**
     * Describes a line segment in the result of the Delaunay Triangulation.
     */
    class ResultEdge internal constructor(
        @JvmField val start_point: Point,
        @JvmField val start_object: Storable?,
        @JvmField val end_point: Point,
        @JvmField val end_object: Storable?
    )

    /**
     * Contains a corner point together with the objects this corner belongs to.
     */
    private class Corner(
        val `object`: Storable?,
        val coor: Point
    ) {
        /**
         * The function returns Side.ON_THE_LEFT, if this corner is on the left of the line from p_1 to p_2; Side.ON_THE_RIGHT, if this corner is on the right of the line from p_1 to p_2; and
         * Side.COLLINEAR, if this corner is collinear with p_1 and p_2.
         */
        fun side_of(p_1: Corner, p_2: Corner): Side {
            return this.coor.side_of(p_1.coor, p_2.coor)
        }
    }

    /**
     * Directed acyclic graph for finding the triangle containing a search point p. The leaves contain the triangles of the current triangulation. The internal nodes are triangles, that were part of the
     * triangulation at some earlier stage, but have been replaced their children.
     */
    private class TriangleGraph(p_triangle: Triangle?) {
        var anchor: Triangle? = null

        init {
            if (p_triangle != null) {
                insert(p_triangle, null)
            } else {
                this.anchor = null
            }
        }

        fun insert(p_triangle: Triangle, p_parent: Triangle?) {
            p_triangle.initialize_is_on_the_left_of_edge_line_array()
            if (p_parent == null) {
                anchor = p_triangle
            } else {
                p_parent.children.add(p_triangle)
            }
        }

        /**
         * Search for the leaf triangle containing p_corner. It will not be unique, if p_corner lies on a triangle edge.
         */
        fun position_locate(p_corner: Corner): Triangle? {
            val anchorNode = this.anchor ?: return null
            if (anchorNode.children.isEmpty()) {
                return anchorNode
            }
            for (curr_child in anchorNode.children) {
                val result = position_locate_reku(p_corner, curr_child)
                if (result != null) {
                    return result
                }
            }
            FRLogger.warn("TriangleGraph.position_locate: containing triangle not found")
            return null
        }

        /**
         * Recursive part of position_locate.
         */
        private fun position_locate_reku(p_corner: Corner, p_triangle: Triangle): Triangle? {
            if (!p_triangle.contains(p_corner)) {
                return null
            }

            if (p_triangle.is_leaf()) {
                return p_triangle
            }
            for (curr_child in p_triangle.children) {
                val result = position_locate_reku(p_corner, curr_child)
                if (result != null) {
                    return result
                }
            }
            FRLogger.warn("TriangleGraph.position_locate_reku: containing triangle not found")
            return null
        }
    }

    /**
     * Describes an edge between two triangles in the triangulation. The unique id_nos are for making edges comparable.
     */
    private inner class Edge(val start_corner: Corner, val end_corner: Corner) : Comparable<Edge> {
        private val id_no: Int = new_edge_id_no()
        var left_triangle: Triangle? = null
        var right_triangle: Triangle? = null

        override fun compareTo(other: Edge): Int {
            return this.id_no - other.id_no
        }

        /**
         * Returns the common corner of this edge and p_other, or null, if no common corner exists.
         */
        fun common_corner(p_other: Edge): Corner? {
            var result: Corner? = null
            if (p_other.start_corner == this.start_corner || p_other.end_corner == this.start_corner) {
                result = this.start_corner
            } else if (p_other.start_corner == this.end_corner || p_other.end_corner == this.end_corner) {
                result = this.end_corner
            }
            return result
        }

        /**
         * Returns the neighbour triangle of this edge, which is different from p_triangle. If p_triangle is not a neighbour of this edge, null is returned.
         */
        fun other_neighbour(p_triangle: Triangle): Triangle? {
            return when (p_triangle) {
                this.left_triangle -> this.right_triangle
                this.right_triangle -> this.left_triangle
                else -> {
                    FRLogger.warn("Edge.other_neighbour: inconsistent neighbour triangle")
                    null
                }
            }
        }

        /**
         * Returns true, if this is a legal edge of the Delaunay Triangulation.
         */
        fun is_legal(): Boolean {
            val leftTriangle = this.left_triangle ?: return true
            val rightTriangle = this.right_triangle ?: return true
            val left_opposite_corner = leftTriangle.opposite_corner(this) ?: return true
            val right_opposite_corner = rightTriangle.opposite_corner(this) ?: return true

            val inside_circle = right_opposite_corner.coor.to_float().inside_circle(
                this.start_corner.coor.to_float(),
                left_opposite_corner.coor.to_float(),
                this.end_corner.coor.to_float()
            )
            return !inside_circle
        }

        /**
         * Flips this edge line to the edge line between the opposite corners of the adjacent triangles. Returns the new constructed Edge.
         */
        fun flip(): Edge? {
            val leftTriangle = this.left_triangle ?: return null
            val rightTriangle = this.right_triangle ?: return null
            val leftOppCorner = leftTriangle.opposite_corner(this) ?: return null
            val rightOppCorner = rightTriangle.opposite_corner(this) ?: return null

            val flipped_edge = Edge(rightOppCorner, leftOppCorner)
            val first_parent = leftTriangle

            // Calculate the index of this edge line in the left and right adjacent triangles.
            var left_index = -1
            var right_index = -1
            for (i in 0 until 3) {
                if (leftTriangle.edge_lines[i] === this) {
                    left_index = i
                }
                if (rightTriangle.edge_lines[i] === this) {
                    right_index = i
                }
            }
            if (left_index < 0 || right_index < 0) {
                FRLogger.warn("Edge.flip: edge line inconsistent")
                return null
            }
            val left_prev_edge = leftTriangle.edge_lines[(left_index + 2) % 3]
            val left_next_edge = leftTriangle.edge_lines[(left_index + 1) % 3]
            val right_prev_edge = rightTriangle.edge_lines[(right_index + 2) % 3]
            val right_next_edge = rightTriangle.edge_lines[(right_index + 1) % 3]

            // Create the left triangle of the flipped edge.
            var curr_edge_lines = arrayOf(flipped_edge, left_prev_edge, right_next_edge)
            val new_left_triangle = Triangle(curr_edge_lines, first_parent)
            flipped_edge.left_triangle = new_left_triangle
            if (left_prev_edge.left_triangle === leftTriangle) {
                left_prev_edge.left_triangle = new_left_triangle
            } else {
                left_prev_edge.right_triangle = new_left_triangle
            }
            if (right_next_edge.left_triangle === rightTriangle) {
                right_next_edge.left_triangle = new_left_triangle
            } else {
                right_next_edge.right_triangle = new_left_triangle
            }

            // Create the right triangle of the flipped edge.
            curr_edge_lines = arrayOf(flipped_edge, right_prev_edge, left_next_edge)
            val new_right_triangle = Triangle(curr_edge_lines, first_parent)
            flipped_edge.right_triangle = new_right_triangle
            if (right_prev_edge.left_triangle === rightTriangle) {
                right_prev_edge.left_triangle = new_right_triangle
            } else {
                right_prev_edge.right_triangle = new_right_triangle
            }
            if (left_next_edge.left_triangle === leftTriangle) {
                left_next_edge.left_triangle = new_right_triangle
            } else {
                left_next_edge.right_triangle = new_right_triangle
            }

            return flipped_edge
        }

        /**
         * Checks the consistency of this edge in its database. Used for debugging purposes.
         */
        fun validate(): Boolean {
            var result = true
            if (this.left_triangle == null) {
                if (this.start_corner.`object` != null || this.end_corner.`object` != null) {
                    FRLogger.warn("Edge.validate: left triangle may be null only for bounding edges")
                    result = false
                }
            } else {
                // check if the left triangle contains this edge
                var found = false
                for (i in 0 until 3) {
                    if (left_triangle!!.edge_lines[i] === this) {
                        found = true
                        break
                    }
                }
                if (!found) {
                    FRLogger.warn("Edge.validate: left triangle does not contain this edge")
                    result = false
                }
            }
            if (this.right_triangle == null) {
                if (this.start_corner.`object` != null || this.end_corner.`object` != null) {
                    FRLogger.warn("Edge.validate: right triangle may be null only for bounding edges")
                    result = false
                }
            } else {
                // check if the left triangle contains this edge
                var found = false
                for (i in 0 until 3) {
                    if (right_triangle!!.edge_lines[i] === this) {
                        found = true
                        break
                    }
                }
                if (!found) {
                    FRLogger.warn("Edge.validate: right triangle does not contain this edge")
                    result = false
                }
            }

            return result
        }
    }

    /**
     * Describes a triangle in the triangulation. edge_lines ia an array of dimension 3. The edge lines arec sorted in counter clock sense around the border of this triangle. The list children points to
     * the children of this triangle, when used as a node in the search graph.
     */
    private inner class Triangle(
        val edge_lines: Array<Edge>,
        val first_parent: Triangle?
    ) {
        val children: MutableCollection<Triangle> = LinkedList()
        private var is_on_the_left_of_edge_line: BooleanArray? = null

        /**
         * Returns true, if this triangle node is a leaf, and false, if it is an inner node.
         */
        fun is_leaf(): Boolean {
            return this.children.isEmpty()
        }

        /**
         * Gets the corner with index p_no.
         */
        fun get_corner(p_no: Int): Corner? {
            if (p_no < 0 || p_no >= 3) {
                FRLogger.warn("Triangle.get_corner: p_no out of range")
                return null
            }
            val curr_edge = edge_lines[p_no]
            return when (this) {
                curr_edge.left_triangle -> curr_edge.start_corner
                curr_edge.right_triangle -> curr_edge.end_corner
                else -> {
                    FRLogger.warn("Triangle.get_corner: inconsistent edge lines")
                    null
                }
            }
        }

        /**
         * Calculates the opposite corner of this triangle to p_edge_line. Returns null, if p_edge_line is nor an edge line of this triangle.
         */
        fun opposite_corner(p_edge_line: Edge): Corner? {
            var edge_line_no = -1
            for (i in 0 until 3) {
                if (this.edge_lines[i] === p_edge_line) {
                    edge_line_no = i
                    break
                }
            }
            if (edge_line_no < 0) {
                FRLogger.warn("Triangle.opposite_corner: p_edge_line not found")
                return null
            }
            val next_edge = this.edge_lines[(edge_line_no + 1) % 3]
            return if (next_edge.left_triangle === this) {
                next_edge.end_corner
            } else {
                next_edge.start_corner
            }
        }

        /**
         * Checks if p_point is inside or on the border of this triangle.
         */
        fun contains(p_corner: Corner): Boolean {
            val isOnLeft = this.is_on_the_left_of_edge_line
            if (isOnLeft == null) {
                FRLogger.warn("Triangle.contains: array is_on_the_left_of_edge_line not initialized")
                return false
            }
            for (i in 0 until 3) {
                val curr_edge = this.edge_lines[i]
                val curr_side = p_corner.side_of(curr_edge.start_corner, curr_edge.end_corner)
                if (isOnLeft[i]) {
                    // checking curr_edge.left_triangle == this instead will not work, if this triangle is an
                    // inner node.
                    if (curr_side == Side.ON_THE_RIGHT) {
                        return false
                    }
                } else {
                    if (curr_side == Side.ON_THE_LEFT) {
                        return false
                    }
                }
            }
            return true
        }

        /**
         * Puts the edges of all leafs below this node into the list p_result_edges
         */
        fun get_leaf_edges(p_result_edges: MutableSet<Edge>) {
            if (this.is_leaf()) {
                for (i in 0 until 3) {
                    val curr_edge = this.edge_lines[i]
                    if (curr_edge.start_corner.`object` != null && curr_edge.end_corner.`object` != null) {
                        // Skip edges containing a bounding corner.
                        p_result_edges.add(curr_edge)
                    }
                }
            } else {
                for (curr_child in this.children) {
                    if (curr_child.first_parent === this) { // to prevent traversing nodes more than once
                        curr_child.get_leaf_edges(p_result_edges)
                    }
                }
            }
        }

        /**
         * Split this triangle into 3 new triangles by adding edges from the corners of this triangle to p_corner, p_corner has to be located in the interior of this triangle.
         */
        fun split_at_inner_point(p_corner: Corner): Array<Triangle>? {
            val new_triangles = arrayOfNulls<Triangle>(3)

            val c0 = this.get_corner(0) ?: return null
            val c1 = this.get_corner(1) ?: return null
            val c2 = this.get_corner(2) ?: return null

            // construct the 3 new triangles.
            var curr_edge_lines = arrayOf(this.edge_lines[0], Edge(c1, p_corner), Edge(p_corner, c0))
            new_triangles[0] = Triangle(curr_edge_lines, this)

            val t0 = new_triangles[0]!!
            curr_edge_lines = arrayOf(this.edge_lines[1], Edge(c2, p_corner), t0.edge_lines[1])
            new_triangles[1] = Triangle(curr_edge_lines, this)

            val t1 = new_triangles[1]!!
            curr_edge_lines = arrayOf(this.edge_lines[2], t0.edge_lines[2], t1.edge_lines[1])
            new_triangles[2] = Triangle(curr_edge_lines, this)

            val t2 = new_triangles[2]!!

            // Set the new neighbour triangles of the edge lines.
            for (i in 0 until 3) {
                val curr_edge = new_triangles[i]!!.edge_lines[0]
                if (curr_edge.left_triangle === this) {
                    curr_edge.left_triangle = new_triangles[i]!!
                } else {
                    curr_edge.right_triangle = new_triangles[i]!!
                }
                // The other neighbour triangle remains valid.
            }

            var curr_edge = t0.edge_lines[1]
            curr_edge.left_triangle = t0
            curr_edge.right_triangle = t1

            curr_edge = t1.edge_lines[1]
            curr_edge.left_triangle = t1
            curr_edge.right_triangle = t2

            curr_edge = t2.edge_lines[1]
            curr_edge.left_triangle = t0
            curr_edge.right_triangle = t2

            return new_triangles.filterNotNull().toTypedArray()
        }

        /**
         * Split this triangle and p_neighbour_to_split into 4 new triangles by adding edges from the corners of the triangles to p_corner. p_corner is assumed to be located on the common edge line of
         * this triangle and p_neighbour_to_split. If that is not true, the function returns null. The first 2 result triangles are from splitting this triangle, and the last 2 result triangles are from
         * splitting p_neighbour_to_split.
         */
        fun split_at_border_point(p_corner: Corner, p_neighbour_to_split: Triangle): Array<Triangle>? {
            val new_triangles = arrayOfNulls<Triangle>(4)
            // look for the triangle edge of this and the neighbour triangle containing p_point;
            var this_touching_edge_no = -1
            var neighbour_touching_edge_no = -1
            var touching_edge: Edge? = null
            var other_touching_edge: Edge? = null
            for (i in 0 until 3) {
                var curr_edge = this.edge_lines[i]
                if (p_corner.side_of(curr_edge.start_corner, curr_edge.end_corner) == Side.COLLINEAR) {
                    this_touching_edge_no = i
                    touching_edge = curr_edge
                }
                curr_edge = p_neighbour_to_split.edge_lines[i]
                if (p_corner.side_of(curr_edge.start_corner, curr_edge.end_corner) == Side.COLLINEAR) {
                    neighbour_touching_edge_no = i
                    other_touching_edge = curr_edge
                }
            }
            if (this_touching_edge_no < 0 || neighbour_touching_edge_no < 0) {
                FRLogger.warn("Triangle.split_at_border_point: touching edge not found")
                return null
            }
            if (touching_edge !== other_touching_edge) {
                FRLogger.warn("Triangle.split_at_border_point: edges inconsistent")
                return null
            }

            val tEdge = touching_edge ?: return null

            val first_common_new_edge: Edge
            val second_common_new_edge: Edge
            // Construct the new edge lines that 2 split triangles of this triangle
            // will be on the left side of the new common touching edges.
            if (this === tEdge.left_triangle) {
                first_common_new_edge = Edge(tEdge.start_corner, p_corner)
                second_common_new_edge = Edge(p_corner, tEdge.end_corner)
            } else {
                first_common_new_edge = Edge(tEdge.end_corner, p_corner)
                second_common_new_edge = Edge(p_corner, tEdge.start_corner)
            }

            // Construct the first split triangle of this triangle.
            val prev_edge = this.edge_lines[(this_touching_edge_no + 2) % 3]
            val this_splitting_edge: Edge
            // construct the splitting edge line of this triangle, so that the first split
            // triangle lies on the left side, and the second split triangle on the right side.
            if (this === prev_edge.left_triangle) {
                this_splitting_edge = Edge(p_corner, prev_edge.start_corner)
            } else {
                this_splitting_edge = Edge(p_corner, prev_edge.end_corner)
            }
            var curr_edge_lines = arrayOf(prev_edge, first_common_new_edge, this_splitting_edge)
            new_triangles[0] = Triangle(curr_edge_lines, this)
            if (this === prev_edge.left_triangle) {
                prev_edge.left_triangle = new_triangles[0]!!
            } else {
                prev_edge.right_triangle = new_triangles[0]!!
            }
            first_common_new_edge.left_triangle = new_triangles[0]!!
            this_splitting_edge.left_triangle = new_triangles[0]!!

            // Construct the second split triangle of this triangle.
            val next_edge = this.edge_lines[(this_touching_edge_no + 1) % 3]
            curr_edge_lines = arrayOf(this_splitting_edge, second_common_new_edge, next_edge)
            new_triangles[1] = Triangle(curr_edge_lines, this)
            this_splitting_edge.right_triangle = new_triangles[1]!!
            second_common_new_edge.left_triangle = new_triangles[1]!!
            if (this === next_edge.left_triangle) {
                next_edge.left_triangle = new_triangles[1]!!
            } else {
                next_edge.right_triangle = new_triangles[1]!!
            }

            // construct the first split triangle of p_neighbour_to_split
            val next_edge_neighbour = p_neighbour_to_split.edge_lines[(neighbour_touching_edge_no + 1) % 3]
            val neighbour_splitting_edge: Edge
            // construct the splitting edge line of p_neighbour_to_split, so that the first split
            // triangle lies on the left side, and the second split triangle on the right side.
            if (p_neighbour_to_split === next_edge_neighbour.left_triangle) {
                neighbour_splitting_edge = Edge(next_edge_neighbour.end_corner, p_corner)
            } else {
                neighbour_splitting_edge = Edge(next_edge_neighbour.start_corner, p_corner)
            }
            curr_edge_lines = arrayOf(neighbour_splitting_edge, first_common_new_edge, next_edge_neighbour)
            new_triangles[2] = Triangle(curr_edge_lines, p_neighbour_to_split)
            neighbour_splitting_edge.left_triangle = new_triangles[2]!!
            first_common_new_edge.right_triangle = new_triangles[2]!!
            if (p_neighbour_to_split === next_edge_neighbour.left_triangle) {
                next_edge_neighbour.left_triangle = new_triangles[2]!!
            } else {
                next_edge_neighbour.right_triangle = new_triangles[2]!!
            }

            // construct the second split triangle of p_neighbour_to_split
            val prev_edge_neighbour = p_neighbour_to_split.edge_lines[(neighbour_touching_edge_no + 2) % 3]
            curr_edge_lines = arrayOf(prev_edge_neighbour, second_common_new_edge, neighbour_splitting_edge)
            new_triangles[3] = Triangle(curr_edge_lines, p_neighbour_to_split)
            if (p_neighbour_to_split === prev_edge_neighbour.left_triangle) {
                prev_edge_neighbour.left_triangle = new_triangles[3]!!
            } else {
                prev_edge_neighbour.right_triangle = new_triangles[3]!!
            }
            second_common_new_edge.right_triangle = new_triangles[3]!!
            neighbour_splitting_edge.right_triangle = new_triangles[3]!!

            return new_triangles.filterNotNull().toTypedArray()
        }

        /**
         * Checks the consistency of this triangle and its children. Used for debugging purposes.
         */
        fun validate(): Boolean {
            var result = true
            if (this.is_leaf()) {
                var prev_edge = this.edge_lines[2]
                for (i in 0 until 3) {
                    val curr_edge = this.edge_lines[i]
                    if (!curr_edge.validate()) {
                        result = false
                    }
                    // Check, if the ens corner of the previous line equals to the start corner of this line.
                    val prev_end_corner = if (prev_edge.left_triangle === this) {
                        prev_edge.end_corner
                    } else {
                        prev_edge.start_corner
                    }
                    val curr_start_corner = when (this) {
                        curr_edge.left_triangle -> curr_edge.start_corner
                        curr_edge.right_triangle -> curr_edge.end_corner
                        else -> {
                            FRLogger.warn("Triangle.validate: edge inconsistent")
                            return false
                        }
                    }
                    if (curr_start_corner !== prev_end_corner) {
                        FRLogger.warn("Triangle.validate: corner inconsistent")
                        result = false
                    }
                    prev_edge = curr_edge
                }
            } else {
                for (curr_child in this.children) {
                    if (curr_child.first_parent === this) { // to avoid traversing nodes more than once.
                        curr_child.validate()
                    }
                }
            }
            return result
        }

        /**
         * Must be done as long as this triangle node is a leaf and after for all its edge lines the left_triangle or the right_triangle reference is set to this triangle.
         */
        fun initialize_is_on_the_left_of_edge_line_array() {
            if (this.is_on_the_left_of_edge_line != null) {
                return // already initialized
            }
            this.is_on_the_left_of_edge_line = BooleanArray(3)
            for (i in 0 until 3) {
                this.is_on_the_left_of_edge_line!![i] = this.edge_lines[i].left_triangle === this
            }
        }
    }

    companion object {
        /**
         * Randum generatur to shuffle the input corners. A fixed seed is used to make the results reproducible.
         */
        private const val seed = 99
        private val random_generator = Random(seed.toLong())
    }
}
