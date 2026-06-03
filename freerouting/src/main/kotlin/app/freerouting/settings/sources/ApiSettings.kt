package app.freerouting.settings.sources

import app.freerouting.settings.RouterSettings
import app.freerouting.settings.SettingsSource

/**
 * Provides router settings from API endpoints.
 * Only the settings specified via API will be non-null.
 * This has the highest priority and overrides all other settings.
 */
class ApiSettings(settings: RouterSettings?) : SettingsSource {

    private val settings: RouterSettings = settings ?: RouterSettings()

    companion object {
        private const val PRIORITY = 70
    }

    override fun getSettings(): RouterSettings? {
        return settings
    }

    override fun getSourceName(): String {
        return "API Settings"
    }

    override fun getPriority(): Int {
        return PRIORITY
    }
}
