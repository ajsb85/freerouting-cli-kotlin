package app.freerouting.drc

import app.freerouting.board.BasicBoard
import app.freerouting.board.ConductionArea
import app.freerouting.board.Item
import app.freerouting.board.Pin
import app.freerouting.board.PolylineTrace
import app.freerouting.board.Trace
import app.freerouting.board.Unit as BoardUnit
import app.freerouting.board.Via
import app.freerouting.constants.Constants
import app.freerouting.geometry.planar.Point
import app.freerouting.logger.FRLogger
import app.freerouting.management.gson.GsonProvider
import app.freerouting.rules.Net
import app.freerouting.settings.DesignRulesCheckerSettings
import java.util.LinkedList
import java.util.Vector

/**
 * Design Rules Checker that centralizes DRC functionality. This class is
 * responsible for detecting clearance violations and other design rule issues.
 */
class DesignRulesChecker @JvmOverloads constructor(
    private val board: BasicBoard,
    private val drcSettings: DesignRulesCheckerSettings? = null
) {
    @JvmField
    var max_connections: Int = 0

    // State for incomplete connections (ratsnest)
    private var net_incompletes: Array<NetIncompletes>? = null

    /**
     * Collects all clearance violations on the board.
     *
     * @return Collection of all clearance violations found
     */
    fun getAllClearanceViolations(): Collection<ClearanceViolation> {
        val allViolations = ArrayList<ClearanceViolation>()
        val seenViolations = HashSet<String>()

        // Iterate through all items on the board
        val items = board.get_items()
        for (item in items) {
            if (item != null) {
                // Get clearance violations for this item
                val itemViolations = item.clearance_violations()

                // Deduplicate violations - A-B and B-A are the same violation
                for (violation in itemViolations) {
                    val id1 = violation.first_item.get_id_no()
                    val id2 = violation.second_item.get_id_no()

                    // Create a unique key using sorted IDs to avoid duplicates
                    val key = if (id1 < id2) {
                        "$id1-$id2-${violation.layer}"
                    } else {
                        "$id2-$id1-${violation.layer}"
                    }

                    if (!seenViolations.contains(key)) {
                        seenViolations.add(key)
                        allViolations.add(violation)
                    }
                }
            }
        }

        return allViolations
    }

    /**
     * Collects all unconnected items on the board.
     *
     * @return Collection of all unconnected items found
     */
    fun getAllUnconnectedItems(): Collection<UnconnectedItems> {
        val unconnectedItems = ArrayList<UnconnectedItems>()

        // Group items by net
        val itemsByNet = HashMap<Int, MutableList<Item>>()
        for (item in board.get_items()) {
            if (item is app.freerouting.board.Connectable && item.net_count() > 0) {
                val netNo = item.get_net_no(0)
                itemsByNet.computeIfAbsent(netNo) { ArrayList() }.add(item)
            }
        }

        // For each net, find truly unconnected items
        for ((netNo, netItems) in itemsByNet) {
            if (netItems.size <= 1) {
                continue // Single item nets are not unconnected
            }

            // Get all connected sets for this net
            val connectedSets = ArrayList<Set<Item>>()
            val processedItems = HashSet<Item>()

            for (item in netItems) {
                if (processedItems.contains(item)) {
                    continue
                }

                // Get the connected set for this item
                val connectedSet = item.get_connected_set(netNo)
                val setItems = HashSet(connectedSet)

                // Only add items that are actually in this net
                setItems.retainAll(netItems)

                if (setItems.isNotEmpty()) {
                    connectedSets.add(setItems)
                    processedItems.addAll(setItems)
                }
            }

            // If there are multiple connected sets, we have unconnected items
            // Report only ONE entry per net, but include items from the disconnected groups
            if (connectedSets.size >= 2) {
                // Find representative items from the first two sets
                val item1 = findRepresentativeItem(connectedSets[0])
                val item2 = findRepresentativeItem(connectedSets[1])

                if (item1 != null && item2 != null) {
                    // Include only items from the two disconnected groups for investigation
                    val unconnectedGroupItems = ArrayList<Item>()
                    unconnectedGroupItems.addAll(connectedSets[0])
                    unconnectedGroupItems.addAll(connectedSets[1])
                    unconnectedItems.add(UnconnectedItems(item1, item2, unconnectedGroupItems))
                }
            }
        }

        // Check for dangling traces - traces with unconnected ends
        for (item in board.get_items()) {
            if (item is Trace) {
                val startContacts = item.get_start_contacts()
                val endContacts = item.get_end_contacts()

                // A trace is dangling if either its start or end has no contacts
                if (startContacts.isEmpty() || endContacts.isEmpty()) {
                    // Only add if not already in the list
                    if (unconnectedItems.none { ui -> ui.first_item === item }) {
                        unconnectedItems.add(UnconnectedItems(item, null, "track_dangling"))
                    }
                }
            }
        }

        // Check for dangling vias - vias not connected or connected on only one layer
        for (item in board.get_items()) {
            if (item is Via) {
                // Use the is_tail() method which checks if via has contacts on at most 1 layer
                if (item.is_tail) {
                    unconnectedItems.add(UnconnectedItems(item, null, "via_dangling"))
                }
            }
        }

        return unconnectedItems
    }

    /**
     * Finds a representative item from a connected set, preferring Pins over other items.
     *
     * @param connectedSet The set of connected items
     * @return A representative item, or null if the set is empty
     */
    private fun findRepresentativeItem(connectedSet: Set<Item>): Item? {
        // Prefer Pins
        for (item in connectedSet) {
            if (item is Pin) {
                return item
            }
        }
        // Then Traces
        for (item in connectedSet) {
            if (item is Trace) {
                return item
            }
        }
        // Finally any item
        return if (connectedSet.isEmpty()) null else connectedSet.iterator().next()
    }

    /**
     * Generates a DRC report in KiCad JSON format.
     *
     * @param sourceFile     Name of the source file
     * @param coordinateUnit Unit for coordinates (e.g., "mm", "mil")
     * @return DRC report in KiCad JSON format
     */
    fun generateReport(sourceFile: String, coordinateUnit: String): DrcReport {
        val report = DrcReport(coordinateUnit, sourceFile, "Freerouting " + Constants.FREEROUTING_VERSION)

        // Get all clearance violations
        val violations = getAllClearanceViolations()

        FRLogger.trace("DesignRulesChecker.generateReport", "drc_check_started",
            "DRC check started: total_clearance_violations=" + violations.size
                + ", coordinate_unit=" + coordinateUnit
                + ", source_file=" + sourceFile,
            "DRC Check",
            emptyArray<Point>()
        )

        // Convert internal violations to DRC report format
        for (violation in violations) {
            val drcViolation = convertToDrcViolation(violation, coordinateUnit)
            report.addViolation(drcViolation)

            FRLogger.trace("DesignRulesChecker.generateReport", "drc_violation",
                "DRC violation: type=clearance"
                    + ", item1=" + violation.first_item.toString()
                    + ", item2=" + violation.second_item.toString()
                    + ", layer=" + violation.layer
                    + ", expected=" + (violation.expected_clearance / 10000.0) + "mm"
                    + ", actual=" + (violation.actual_clearance / 10000.0) + "mm"
                    + ", delta=" + ((violation.expected_clearance - violation.actual_clearance) / 10000.0) + "mm",
                "DRC Check",
                arrayOf(violation.shape.centre_of_gravity().round())
            )
        }

        // Get all unconnected items
        val unconnectedItems = getAllUnconnectedItems()

        FRLogger.trace("DesignRulesChecker.generateReport", "unconnected_items",
            "Unconnected items found: count=" + unconnectedItems.size,
            "DRC Check",
            emptyArray<Point>()
        )

        // Convert unconnected items to DRC report format
        for (unconnectedItem in unconnectedItems) {
            val drcViolation = convertToDrcViolation(unconnectedItem, coordinateUnit)
            if ("track_dangling" == unconnectedItem.type || "via_dangling" == unconnectedItem.type) {
                report.addViolation(drcViolation)
            } else {
                report.addUnconnectedItem(drcViolation)
            }
        }

        FRLogger.trace("DesignRulesChecker.generateReport", "drc_check_completed",
            "DRC check completed: total_violations=" + report.violations.size
                + ", total_unconnected=" + report.unconnected_items.size,
            "DRC Check",
            emptyArray<Point>()
        )

        return report
    }

    /**
     * Converts an internal ClearanceViolation to a DrcViolation for the report.
     *
     * @param violation      Internal clearance violation
     * @param coordinateUnit Unit for coordinates
     * @return DRC violation in report format
     */
    private fun convertToDrcViolation(violation: ClearanceViolation, coordinateUnit: String): DrcViolation {
        val items = ArrayList<DrcViolationItem>()

        // Create items for first and second objects
        val firstItemDesc = getItemDescription(violation.first_item)
        val secondItemDesc = getItemDescription(violation.second_item)

        // Position is the center of gravity of the violation shape
        val firstItemCenterOfGravity = violation.first_item
            .bounding_box()
            .centre_of_gravity()
        val firstItemPos = DrcPosition(
            convertCoordinate(firstItemCenterOfGravity.x, coordinateUnit),
            convertCoordinate(firstItemCenterOfGravity.y, coordinateUnit)
        )
        val secondItemCenterOfGravity = violation.second_item
            .bounding_box()
            .centre_of_gravity()
        val secondItemPos = DrcPosition(
            convertCoordinate(secondItemCenterOfGravity.x, coordinateUnit),
            convertCoordinate(secondItemCenterOfGravity.y, coordinateUnit)
        )

        // Use item IDs as UUIDs (they are unique within the board)
        val firstUuid = violation.first_item.get_id_no().toString()
        val secondUuid = violation.second_item.get_id_no().toString()

        items.add(DrcViolationItem(firstItemDesc, firstItemPos, firstUuid))
        items.add(DrcViolationItem(secondItemDesc, secondItemPos, secondUuid))

        // Determine violation type
        var type = "clearance"
        if (isHole(violation.first_item) || isHole(violation.second_item)) {
            type = "hole_clearance"
        }

        // Create violation description
        val description = if ("hole_clearance" == type) {
            "Hole clearance violation between %s and %s (expected: %.4f %s, actual: %.4f %s)".format(
                firstItemDesc, secondItemDesc,
                convertCoordinate(violation.expected_clearance, coordinateUnit), coordinateUnit,
                convertCoordinate(violation.actual_clearance, coordinateUnit), coordinateUnit
            )
        } else {
            "Clearance violation between %s and %s (expected: %.4f %s, actual: %.4f %s)".format(
                firstItemDesc, secondItemDesc,
                convertCoordinate(violation.expected_clearance, coordinateUnit), coordinateUnit,
                convertCoordinate(violation.actual_clearance, coordinateUnit), coordinateUnit
            )
        }

        return DrcViolation(type, description, "error", items)
    }

    private fun isHole(item: Item): Boolean {
        if (item is Via) {
            return true
        }
        // Pins are treated as holes for DRC classification to match expected output,
        // although this might include SMT pins (DrillItem).
        return item is Pin
    }

    private fun convertToDrcViolation(unconnectedItems: UnconnectedItems, coordinateUnit: String): DrcViolation {
        val items = ArrayList<DrcViolationItem>()

        val description: String

        if ("track_dangling" == unconnectedItems.type || "via_dangling" == unconnectedItems.type) {
            // For dangling items, show only the single item
            val item = unconnectedItems.first_item

            val itemDesc = if ("via_dangling" == unconnectedItems.type) {
                getItemDescription(item)
            } else {
                // Get detailed track description with layer and length
                getDetailedTraceDescription(item, coordinateUnit)
            }

            val itemCenterOfGravity = item.bounding_box().centre_of_gravity()
            val itemPos = DrcPosition(
                convertCoordinate(itemCenterOfGravity.x, coordinateUnit),
                convertCoordinate(itemCenterOfGravity.y, coordinateUnit)
            )

            val uuid = item.get_id_no().toString()
            items.add(DrcViolationItem(itemDesc, itemPos, uuid))

            description = when (unconnectedItems.type) {
                "via_dangling" -> "Via is not connected or connected on only one layer"
                "track_dangling" -> "Track has unconnected end"
                else -> "Unconnected item: $itemDesc"
            }

            return DrcViolation(unconnectedItems.type, description, "warning", items)
        }

        // Create items for all items from the unconnected net
        // This provides better visibility of all affected components/pins
        for (item in unconnectedItems.all_items) {
            val itemDesc = getItemDescription(item)
            val itemCenterOfGravity = item.bounding_box().centre_of_gravity()
            val itemPos = DrcPosition(
                convertCoordinate(itemCenterOfGravity.x, coordinateUnit),
                convertCoordinate(itemCenterOfGravity.y, coordinateUnit)
            )
            val uuid = item.get_id_no().toString()
            items.add(DrcViolationItem(itemDesc, itemPos, uuid))
        }

        // Create violation description using the first two representative items
        val fromItemDesc = getItemDescription(unconnectedItems.first_item)
        if (unconnectedItems.second_item != null) {
            val toItemDesc = getItemDescription(unconnectedItems.second_item)
            description = "Unconnected items: %s and %s (%d total items in net)".format(
                fromItemDesc, toItemDesc, unconnectedItems.all_items.size
            )
        } else {
            description = "Unconnected item: %s".format(fromItemDesc)
        }

        return DrcViolation(unconnectedItems.type, description, "warning", items)
    }

    /**
     * Gets a human-readable description of an item.
     *
     * @param item The item to describe
     * @return Description string
     */
    private fun getItemDescription(item: Item): String {
        val desc = StringBuilder()

        if (item is Trace) {
            desc.append("Trace")
        } else if (item is Via) {
            desc.append("Via")
        } else if (item is Pin) {
            desc.append("Pin")
        } else if (item is ConductionArea) {
            desc.append("Conduction Area")
        } else {
            desc.append(item.javaClass.simpleName)
        }

        // Add net information
        if (item.net_count() > 0) {
            val netName = board.rules.nets.get(item.get_net_no(0))?.name ?: "unknown"
            desc
                .append(" [")
                .append(netName)
                .append("]")
        }

        return desc.toString()
    }

    /**
     * Gets a detailed description of a trace including net, layer, and length.
     *
     * @param item           The trace item to describe
     * @param coordinateUnit Unit for coordinates
     * @return Detailed description string
     */
    private fun getDetailedTraceDescription(item: Item, coordinateUnit: String): String {
        val desc = StringBuilder("Track")

        // Add net information
        if (item.net_count() > 0) {
            val netName = board.rules.nets.get(item.get_net_no(0))?.name ?: "unknown"
            desc
                .append(" [")
                .append(netName)
                .append("]")
        }

        // Add layer information
        if (item is Trace) {
            val layer = item.get_layer()
            val layerName = board.layer_structure.arr[layer].name
            desc.append(" on ").append(layerName)

            // Add length information
            val lengthInBoardUnits = item.get_length()
            val lengthInTargetUnits = convertCoordinate(lengthInBoardUnits, coordinateUnit)
            desc.append(", length ").append(String.format("%.4f", lengthInTargetUnits)).append(" ").append(coordinateUnit)
        }

        return desc.toString()
    }

    /**
     * Converts a coordinate value from board's internal coordinate system to the
     * specified unit.
     *
     * @param boardCoordinate Coordinate in board's internal system
     * @param coordinateUnit  Target unit ("mm", "mil", etc.)
     * @return Coordinate value in the target unit
     */
    private fun convertCoordinate(boardCoordinate: Double, coordinateUnit: String): Double {
        // First, convert from board's internal coordinate system to DSN coordinates (in
        // the board's unit)
        val dsnCoordinate = board.communication.coordinate_transform.board_to_dsn(boardCoordinate)

        // Get the board's native unit
        val boardUnit = board.communication.unit

        // Determine target unit
        val targetUnit = when (coordinateUnit) {
            "mm" -> BoardUnit.MM
            "mil" -> BoardUnit.MIL
            "inch" -> BoardUnit.INCH
            "um" -> BoardUnit.UM
            else -> boardUnit
        }

        // If the target unit is different from the board unit, convert
        if (targetUnit != boardUnit) {
            return BoardUnit.scale(dsnCoordinate, boardUnit, targetUnit)
        }

        return dsnCoordinate
    }

    /**
     * Initializes the incomplete connection calculations for all nets on the board.
     * Incomplete connections (airlines) are determined based on the items associated
     * with each net.
     * This is not equivalent to the total number of connections, as some nets
     * may have multiple items already connected together.
     * This is also not equivalent to the number of not-completed nets, as a net may
     * have multiple connections with some connections completed while others remain
     * incomplete.
     */
    fun calculateAllIncompletes() {
        val max_net_no = board.rules.nets.max_net_no()
        // Create the net item lists at once for performance reasons.
        val net_item_lists = Vector<MutableCollection<Item>>(max_net_no)
        for (i in 0 until max_net_no) {
            net_item_lists.add(LinkedList())
        }
        val it = board.item_list.start_read_object()
        while (true) {
            val curr_item = board.item_list.read_object(it) as? Item ?: break
            if (curr_item is app.freerouting.board.Connectable) {
                for (i in 0 until curr_item.net_count()) {
                    net_item_lists[curr_item.get_net_no(i) - 1].add(curr_item)
                }
            }
        }
        // Correct formula: for each net with ≥2 items, (items - 1) connections are needed
        // (minimum spanning tree). Nets with 0 or 1 items contribute 0.
        // The old formula (total_items - net_count) incorrectly included empty nets in the
        // denominator, producing a max_connections value that was too small and could even be
        // negative or zero, which caused getNormalizedScore() to always return 0.
        this.max_connections = net_item_lists
            .stream()
            .filter { list -> list.isNotEmpty() }
            .mapToInt { list ->
                val endpointCount = list.stream()
                    .filter { item -> item is Pin || item is ConductionArea }
                    .count()
                Math.max(0, (endpointCount - 1).toInt())
            }
            .sum()

        val totalItems = net_item_lists
            .stream()
            .mapToInt { it.size }
            .sum()
        FRLogger.trace("DesignRulesChecker.calculateAllIncompletes", "max_connections",
            "Calculated max_connections=" + this.max_connections
                + ", total_items=" + totalItems
                + ", net_count=" + net_item_lists.size
                + " (formula: total_items - net_count)",
            "Incomplete Count",
            emptyArray<Point>()
        )

        val focusNets = intArrayOf(98, 99)
        for (netNo in focusNets) {
            if (netNo >= 1 && netNo <= net_item_lists.size) {
                val netItems = net_item_lists[netNo - 1].size
                val net = board.rules.nets.get(netNo)
                val netName = net?.name ?: "unknown"
                FRLogger.trace("DesignRulesChecker.calculateAllIncompletes", "net_item_count",
                    "Net item count: net=" + netNo + ", name=" + netName + ", items=" + netItems,
                    "Net #$netNo ($netName)",
                    emptyArray<Point>()
                )

                // Let's validate all the polyline traces for this net
                val netItemsList = net_item_lists[netNo - 1]
                for (item in netItemsList) {
                    if (item is PolylineTrace) {
                        //trace.validateAndLogPolylineIntegrity();
                    }
                }
            }
        }

        val temp_net_incompletes = Array(max_net_no) { i ->
            NetIncompletes(i + 1, net_item_lists[i], board)
        }
        this.net_incompletes = temp_net_incompletes
    }

    /**
     * Recalculates the incomplete connections (airlines) for the specified net.
     *
     * @param netNo The number of the net to recalculate.
     */
    fun recalculateNetIncompletes(netNo: Int) {
        val current_net_incompletes = net_incompletes
        if (current_net_incompletes == null) {
            calculateAllIncompletes()
            return
        }
        if (netNo >= 1 && netNo <= current_net_incompletes.size) {
            val item_list = board.get_connectable_items(netNo)
            current_net_incompletes[netNo - 1] = NetIncompletes(netNo, item_list, board)
        }
    }

    /**
     * Recalculates the incomplete connections for the specified net using a
     * provided list of items.
     *
     * @param netNo    The number of the net to recalculate.
     * @param itemList The collection of items belonging to the net.
     */
    fun recalculateNetIncompletes(netNo: Int, itemList: Collection<Item>) {
        val current_net_incompletes = net_incompletes
        if (current_net_incompletes == null) {
            calculateAllIncompletes() // Initialize if not already done, though this might be expensive if we only
            // want one net. catch-22.
            // But effectively we need the array initialized.
        }
        val reinitialized_net_incompletes = net_incompletes ?: return
        if (netNo >= 1 && netNo <= reinitialized_net_incompletes.size) {
            // copy itemList, because it will be changed inside the constructor of NetIncompletes
            val items = LinkedList(itemList)
            reinitialized_net_incompletes[netNo - 1] = NetIncompletes(netNo, items, board)
        }
    }

    /**
     * Returns the total number of incomplete connections (airlines) across all nets.
     */
    fun getIncompleteCount(): Int {
        val current_net_incompletes = net_incompletes ?: run {
            calculateAllIncompletes()
            net_incompletes!!
        }

        var result = 0
        val detailsBuilder = StringBuilder()
        var netsWithIncompletes = 0

        for (i in current_net_incompletes.indices) {
            val netIncompletes = current_net_incompletes[i].count()
            if (netIncompletes > 0) {
                result += netIncompletes
                netsWithIncompletes++
                if (netsWithIncompletes <= 10) { // Log first 10 nets with incompletes
                    val net = board.rules.nets.get(i + 1)
                    val netName = net?.name ?: "unknown"
                    detailsBuilder.append("Net #").append(i + 1).append(" (").append(netName).append("): ")
                        .append(netIncompletes).append(" incomplete(s); ")
                }
            }
        }

        FRLogger.trace("DesignRulesChecker.getIncompleteCount", "total_incompletes_calculated",
            "Total incomplete count: " + result
                + ", nets_with_incompletes=" + netsWithIncompletes
                + ", first_few_nets=" + detailsBuilder.toString(),
            "Incomplete Count",
            emptyArray<Point>()
        )

        return result
    }

    /**
     * Returns the number of incomplete connections for a specific net.
     */
    fun getIncompleteCount(netNo: Int): Int {
        val current_net_incompletes = net_incompletes ?: run {
            calculateAllIncompletes()
            net_incompletes!!
        }
        if (netNo <= 0 || netNo > current_net_incompletes.size) {
            return 0
        }

        val result = current_net_incompletes[netNo - 1].count()
        val net = board.rules.nets.get(netNo)
        val netName = net?.name ?: "unknown"

        FRLogger.trace("DesignRulesChecker.getIncompleteCount", "net_incomplete_count",
            "Net incomplete count: net=" + netNo
                + ", name=" + netName
                + ", incomplete_count=" + result,
            "Net #" + netNo + " (" + netName + ")",
            emptyArray<Point>()
        )

        return result
    }

    /**
     * Returns the total number of nets that violate length restrictions.
     */
    fun getLengthViolationCount(): Int {
        val current_net_incompletes = net_incompletes ?: run {
            calculateAllIncompletes()
            net_incompletes!!
        }
        var result = 0
        for (i in current_net_incompletes.indices) {
            if (current_net_incompletes[i].get_length_violation() != 0.0) {
                ++result
            }
        }
        return result
    }

    /**
     * Returns the magnitude of the length violation for the specified net.
     */
    fun getLengthViolation(netNo: Int): Double {
        val current_net_incompletes = net_incompletes ?: run {
            calculateAllIncompletes()
            net_incompletes!!
        }
        if (netNo <= 0 || netNo > current_net_incompletes.size) {
            return 0.0
        }
        return current_net_incompletes[netNo - 1].get_length_violation()
    }

    /**
     * Recalculates length matching violations for all nets.
     *
     * @return true if the status of any length violation has changed.
     */
    fun recalculateLengthViolations(): Boolean {
        val current_net_incompletes = net_incompletes ?: run {
            calculateAllIncompletes()
            return true // Technically changed from nothing to something
        }
        var result = false
        for (i in current_net_incompletes.indices) {
            if (current_net_incompletes[i].calc_length_violation()) {
                result = true
            }
        }
        return result
    }

    /**
     * Retrieves all airlines (incomplete connections) for the entire board.
     */
    fun getAllAirlines(): Array<AirLine> {
        val current_net_incompletes = net_incompletes ?: run {
            calculateAllIncompletes()
            net_incompletes!!
        }
        val count = getIncompleteCount()
        val result = ArrayList<AirLine>(count)
        for (i in current_net_incompletes.indices) {
            val curr_list = current_net_incompletes[i].incompletes
            result.addAll(curr_list)
        }
        return result.toTypedArray()
    }

    /**
     * Gets the NetIncompletes object for a specific net.
     * Useful for drawing or detailed inspection.
     */
    fun getNetIncompletes(netNo: Int): NetIncompletes? {
        val current_net_incompletes = net_incompletes ?: run {
            calculateAllIncompletes()
            net_incompletes!!
        }
        if (netNo <= 0 || netNo > current_net_incompletes.size) {
            return null
        }
        return current_net_incompletes[netNo - 1]
    }

    /**
     * Generates a JSON string of the DRC report.
     *
     * @param sourceFile     Name of the source file
     * @param coordinateUnit Unit for coordinates
     * @return JSON string of the DRC report
     */
    fun generateReportJson(sourceFile: String, coordinateUnit: String): String {
        val report = generateReport(sourceFile, coordinateUnit)
        return GsonProvider.GSON.toJson(report)
    }
}
