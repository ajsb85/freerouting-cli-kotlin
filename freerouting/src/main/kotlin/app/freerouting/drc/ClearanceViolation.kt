package app.freerouting.drc

import app.freerouting.board.Item
import app.freerouting.board.ObjectInfoPanel
import app.freerouting.geometry.planar.ConvexShape
import app.freerouting.management.TextManager
import java.util.Locale

/**
 * Information of a clearance violation between 2 items.
 */
class ClearanceViolation(
    @JvmField val first_item: Item,
    @JvmField val second_item: Item,
    @JvmField val shape: ConvexShape,
    @JvmField val layer: Int,
    @JvmField val expected_clearance: Double,
    @JvmField val actual_clearance: Double
) : ObjectInfoPanel.Printable {

    override fun print_info(p_window: ObjectInfoPanel, p_locale: Locale) {
        val tm = TextManager(this::class.java, p_locale)

        p_window.append_bold(tm.getText("clearance_violation_2"))
        p_window.append(" " + tm.getText("at") + " ")
        p_window.append(shape.centre_of_gravity())
        p_window.append(", " + tm.getText("width") + " ")
        p_window.append(2 * this.shape.smallest_radius())
        p_window.append(", " + tm.getText("layer") + " ")
        p_window.append(first_item.board.layer_structure.arr[this.layer].name)
        p_window.append(", " + tm.getText("between"))
        p_window.newline()
        p_window.indent()
        first_item.print_info(p_window, p_locale)
        p_window.indent()
        second_item.print_info(p_window, p_locale)
        p_window.newline()
        p_window.indent()
        val clearance_violation_info_expected_clearance = tm.getText("clearance_violation_info_expected_clearance")
            .formatted(this.expected_clearance / 10000.0, this.actual_clearance / 10000.0)
        p_window.append(clearance_violation_info_expected_clearance)
    }
}
