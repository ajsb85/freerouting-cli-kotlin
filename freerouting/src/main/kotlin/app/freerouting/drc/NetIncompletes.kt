package app.freerouting.drc

import app.freerouting.board.BasicBoard
import app.freerouting.board.ConductionArea
import app.freerouting.board.DrillItem
import app.freerouting.board.Item
import app.freerouting.datastructures.PlanarDelaunayTriangulation
import app.freerouting.datastructures.Signum
import app.freerouting.geometry.planar.FloatPoint
import app.freerouting.geometry.planar.Point
import app.freerouting.logger.FRLogger
import app.freerouting.rules.Net
import java.util.LinkedList
import java.util.TreeSet

/**
 * Computes and holds the incomplete connections (ratsnest) for a single net.
 * This class is responsible for calculating the minimum spanning tree (or
 * similar structure) to determine which items need to be connected
 * to satisfy the net's connectivity requirements.
 * It also tracks length violations if the net has length constraints.
 */
class NetIncompletes(p_net_no: Int, p_net_items: Collection<Item>, p_board: BasicBoard) {

    /**
     * Collection of elements of class AirLine representing the incomplete
     * connections.
     */
    @JvmField
    val incompletes: MutableCollection<AirLine> = LinkedList()

    /**
     * The net for which the incompletes are calculated.
     */
    private val net: Net?

    /**
     * The radius of the markers drawn at the ends of airlines or layer changes.
     */
    private val draw_marker_radius: Double

    /**
     * The length of the violation of the length restriction of the net.
     * > 0: cumulative trace length is too big.
     * < 0: trace length is too small.
     * 0: trace length is ok or the net has no length restrictions.
     */
    private var length_violation = 0.0

    /**
     * Number of connected groups in this net at calculation time.
     */
    private var connected_group_count = 0

    init {
        this.draw_marker_radius = p_board.rules.get_min_trace_half_width() * 2.0
        this.net = p_board.rules.nets.get(p_net_no)

        val netLabel = "Net #$p_net_no" + if (net != null) " (${net.name})" else ""

        FRLogger.trace("NetIncompletes.<init>", "start_calculation",
            "Starting incomplete calculation: net=" + p_net_no
                + ", name=" + (net?.name ?: "null")
                + ", total_items_in_collection=" + p_net_items.size,
            netLabel,
            emptyArray<Point>()
        )

        // Filter out dangling items (vias and tracks with is_tail() == true)
        // AND items with zero contacts (unconnected pins/pads)
        // These are DRC violations, not unrouted connections, and should not be counted
        // as incompletes
        val filtered_items = LinkedList<Item>()
        var dangling_count = 0
        var unconnected_count = 0
        var conduction_area_count = 0
        var conduction_area_filtered_count = 0
        for (item in p_net_items) {
            // Track ConductionArea items
            if (item is ConductionArea) {
                conduction_area_count++
            }

            // Skip dangling vias and traces - they're violations, not incomplete
            // connections
            if (item.is_tail) {
                dangling_count++
                continue
            }
            // Skip items with no contacts - they're isolated/unconnected, not incomplete
            // connections
            // EXCEPT for DrillItems (pins/vias) - unrouted pins legitimately have no
            // contacts
            // and SHOULD appear in the ratsnest
            // EXCEPT for ConductionArea which acts as a connection medium
            if (item !is ConductionArea && item !is DrillItem
                && item.get_normal_contacts().isEmpty()
            ) {
                unconnected_count++
                continue
            }

            // Track if ConductionArea made it through the filter
            if (item is ConductionArea) {
                conduction_area_filtered_count++
            }

            filtered_items.add(item)
        }

        FRLogger.trace("NetIncompletes.<init>", "filtering_complete",
            "Filtering complete: filtered_items=" + filtered_items.size
                + ", dangling=" + dangling_count
                + ", unconnected=" + unconnected_count
                + ", conduction_areas_total=" + conduction_area_count
                + ", conduction_areas_kept=" + conduction_area_filtered_count,
            netLabel,
            emptyArray<Point>()
        )

        // Create an array of Item-connected_set pairs.
        val net_items = calculate_net_items(filtered_items)

        val unique_connected_sets = HashSet<MutableCollection<Item>>()
        for (net_item in net_items) {
            unique_connected_sets.add(net_item.connected_set)
        }
        this.connected_group_count = unique_connected_sets.size

        FRLogger.trace("NetIncompletes.<init>", "connected_sets_calculated",
            "Connected sets calculated: net_items_count=" + net_items.size
                + ", unique_connected_sets=" + unique_connected_sets.size
                + " (for N groups, expect N-1 airlines)",
            netLabel,
            emptyArray<Point>()
        )

        if (net_items.size <= 1) {
            this.connected_group_count = net_items.size
            FRLogger.trace("NetIncompletes.<init>", "fully_connected",
                "Net is fully connected or has no routable items: net_items=" + net_items.size,
                netLabel,
                emptyArray<Point>()
            )
        } else {
            // create a Delaunay Triangulation for the net_items
            val triangulation_objects = ArrayList<PlanarDelaunayTriangulation.Storable>(net_items.toList())
            val triangulation = PlanarDelaunayTriangulation(triangulation_objects)

            // sort the result edges of the triangulation by length in ascending order.
            val triangulation_lines = triangulation.get_edge_lines()
            val sorted_edges = TreeSet<Edge>()

            for (curr_line in triangulation_lines) {
                val new_edge = Edge(
                    curr_line.start_object as NetItem, curr_line.start_point.to_float(),
                    curr_line.end_object as NetItem, curr_line.end_point.to_float()
                )
                sorted_edges.add(new_edge)
            }

            // Create the Airlines. Skip edges, whose from_item and to_item are already in the same connected set
            // or whose connected sets have already an airline.
            val curr_net = p_board.rules.nets.get(p_net_no)
            if (curr_net != null) {
                for (curr_edge in sorted_edges) {
                    if (curr_edge.from_item.connected_set === curr_edge.to_item.connected_set) {
                        continue // airline exists already
                    }

                    this.incompletes.add(
                        AirLine(
                            curr_net, curr_edge.from_item.item, curr_edge.from_corner,
                            curr_edge.to_item.item, curr_edge.to_corner
                        )
                    )
                    join_connected_sets(net_items, curr_edge.from_item.connected_set, curr_edge.to_item.connected_set)
                }
            }

            FRLogger.trace("NetIncompletes.<init>", "airlines_created",
                "Airlines created: incomplete_count=" + this.incompletes.size
                    + ", total_items=" + p_net_items.size
                    + ", filtered_items=" + filtered_items.size
                    + ", net_items=" + net_items.size
                    + ", connected_groups=" + unique_connected_sets.size
                    + " => Formula: total_items - incomplete_count = " + (p_net_items.size - this.incompletes.size),
                netLabel,
                emptyArray<Point>()
            )
        }

        calc_length_violation()
    }

    /**
     * Returns the collection of airlines (incomplete connections) for this net.
     */
    fun getIncompletes(): Collection<AirLine> {
        return this.incompletes
    }

    /**
     * Returns the net associated with these incompletes.
     */
    fun getNet(): Net? {
        return this.net
    }

    /**
     * Returns the radius used for drawing markers (e.g., layer changes).
     * This is typically derived from the minimum trace width rules.
     */
    fun getMarkerRadius(): Double {
        return this.draw_marker_radius
    }

    /**
     * Returns the number of incompletes/airlines of this net.
     */
    fun count(): Int {
        return incompletes.size
    }

    /**
     * Returns the number of connected groups used to compute airlines.
     */
    fun get_connected_group_count(): Int {
        return this.connected_group_count
    }

    /**
     * Recalculates the length violations. Return false, if the length violation has
     * not changed.
     */
    fun calc_length_violation(): Boolean {
        val old_violation = this.length_violation
        if (this.net == null) {
            this.length_violation = 0.0
            return false
        }
        val max_length = this.net
            .get_class()
            .get_maximum_trace_length()
        val min_length = this.net
            .get_class()
            .get_minimum_trace_length()
        if (max_length <= 0 && min_length <= 0) {
            this.length_violation = 0.0
            return false
        }
        var new_violation = 0.0
        val trace_length = this.net?.get_trace_length() ?: 0.0
        if (max_length > 0 && trace_length > max_length) {
            new_violation = trace_length - max_length
        }
        if (min_length > 0 && trace_length < min_length && this.incompletes.isEmpty()) {
            new_violation = trace_length - min_length
        }
        this.length_violation = new_violation
        return Math.abs(new_violation - old_violation) > 0.1
    }

    /**
     * Returns the length of the violation of the length restriction of the net.
     *
     * @return > 0 if too long, < 0 if too short, 0 if valid.
     */
    fun get_length_violation(): Double {
        return this.length_violation
    }

    /**
     * Calculates an array of Item-connected_set pairs for the items of this net.
     * Groups items that are physically connected into the same connected set.
     *
     * @param p_item_list The list of items to group.
     * @return An array of NetItem objects representing the grouped items.
     */
    private fun calculate_net_items(p_item_list: Collection<Item>): Array<NetItem> {
        val result = ArrayList<NetItem>()
        val unique_items = HashSet(p_item_list)
        val unique_items_count = unique_items.size

        while (!unique_items.isEmpty()) {
            val start_item = unique_items.iterator().next()
            val curr_connected_set = start_item.get_connected_set(this.net?.net_number ?: 0)

            // Prevent ConcurrentModificationException by creating a list of items to remove
            val items_in_component = ArrayList<Item>()
            for (item_in_set in curr_connected_set) {
                if (unique_items.contains(item_in_set)) {
                    items_in_component.add(item_in_set)
                }
            }

            for (curr_item in items_in_component) {
                result.add(NetItem(curr_item, curr_connected_set))
            }
            unique_items.removeAll(items_in_component)
        }

        if (result.size > unique_items_count) {
            FRLogger.warn("NetIncompletes.calculate_net_items: too many items")
        } else if (result.size < unique_items_count) {
            FRLogger.warn("NetIncompletes.calculate_net_items: too few items")
        }
        return result.toTypedArray()
    }

    /**
     * Joins p_from_connected_set to p_to_connected_set and updates the connected
     * sets of the items in p_net_items. Used during Kruskal's algorithm to merge
     * sets.
     */
    private fun join_connected_sets(
        p_net_items: Array<NetItem>, p_from_connected_set: MutableCollection<Item>,
        p_to_connected_set: MutableCollection<Item>
    ) {
        for (i in p_net_items.indices) {
            val curr_item = p_net_items[i]
            if (curr_item.connected_set === p_from_connected_set) {
                p_to_connected_set.add(curr_item.item)
                curr_item.connected_set = p_to_connected_set
            }
        }
    }

    /**
     * Represents a potential edge (connection) between two NetItems in the Delaunay
     * triangulation.
     * Sortable by length to facilitate finding the shortest connections (Minimum
     * Spanning Tree-like approach).
     */
    private class Edge(
        val from_item: NetItem,
        val from_corner: FloatPoint,
        val to_item: NetItem,
        val to_corner: FloatPoint
    ) : Comparable<Edge> {
        val length_square: Double = to_corner.distance_square(from_corner)

        override fun compareTo(other: Edge): Int {
            var result = this.length_square - other.length_square
            if (result == 0.0) {
                // prevent result 0, so that edges with the same length as another edge are not
                // skipped in the set
                result = this.from_corner.x - other.from_corner.x
                if (result == 0.0) {
                    result = this.from_corner.y - other.from_corner.y
                }
                if (result == 0.0) {
                    result = this.to_corner.x - other.to_corner.x
                }
                if (result == 0.0) {
                    result = this.to_corner.y - other.to_corner.y
                }
            }
            return Signum.as_int(result)
        }
    }

    /**
     * Wrapper for an Item used in the Delaunay triangulation, including its
     * connected set.
     */
    private class NetItem(val item: Item, var connected_set: MutableCollection<Item>) : PlanarDelaunayTriangulation.Storable {
        override fun get_triangulation_corners(): Array<Point> {
            return this.item.get_ratsnest_corners()
        }
    }
}
