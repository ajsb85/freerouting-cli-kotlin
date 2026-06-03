package app.freerouting.settings.sources

import app.freerouting.settings.RouterSettings
import app.freerouting.settings.SettingsSource

/**
 * Provides router settings from GUI windows (e.g., WindowAutorouteParameter) at priority 50.
 *
 * <p><strong>Important:</strong> in GUI mode, this class acts only as a <em>startup placeholder</em>.
 * As soon as a board is loaded (DSN or binary), {@code GuiBoardManager} replaces this source with
 * the {@link app.freerouting.interactive.InteractiveSettings} singleton — a subclass of
 * {@code GuiSettings} that overrides {@link #getSettings()} to return a <em>live snapshot</em>
 * built from current field values on every invocation. The
 * {@link app.freerouting.settings.SettingsMerger#addOrReplaceSources} method accepts subtype
 * replacements, so the singleton automatically displaces this placeholder.
 *
 * <p>Plain {@code GuiSettings} instances should therefore <strong>not</strong> be added to the
 * merger when a GUI is active and a board has already been loaded; doing so would install a stale
 * static snapshot at priority 50 and shadow the live {@code InteractiveSettings} source.
 *
 * <p>In headless / CLI / API mode no {@code GuiSettings} (or subclass) source is registered at
 * all; the merger falls back to lower-priority defaults for the fields that would otherwise be
 * supplied here.
 *
 * <h2>Override contract for {@link #getSettings()}</h2>
 * Implementations <strong>must</strong> return a freshly constructed {@link RouterSettings} whose
 * fields contain only the values this source explicitly provides.  All other fields must remain
 * {@code null} so the {@link app.freerouting.settings.SettingsMerger} can resolve them from their
 * authoritative sources. {@code InteractiveSettings} honours this contract by populating only
 * {@code trace_pull_tight_accuracy} and {@code automatic_neckdown}.
 */
open class GuiSettings(settings: RouterSettings?) : SettingsSource {

    private val settings: RouterSettings = settings ?: RouterSettings()

    companion object {
        private const val PRIORITY = 50
    }

    override fun getSettings(): RouterSettings? {
        return settings
    }

    override fun getSourceName(): String {
        return "GUI Settings"
    }

    override fun getPriority(): Int {
        return PRIORITY
    }
}
