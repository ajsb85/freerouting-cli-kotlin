package app.freerouting.settings.sources

import app.freerouting.logger.FRLogger
import app.freerouting.settings.RouterSettings
import app.freerouting.settings.SettingsSource

/**
 * Extracts router settings from SES (Specctra Session) files.
 * Only the settings present in the SES file will be non-null.
 */
class SesFileSettings(private val fileName: String) : SettingsSource {

    private val settings: RouterSettings = loadSettings()

    companion object {
        private const val PRIORITY = 30
    }

    private fun loadSettings(): RouterSettings {
        try {
            // SES files typically don't contain router settings, but we include this
            // for completeness in case they do in some formats
            // TODO: Implement SES file parsing if needed

            FRLogger.debug("Loaded router settings from SES file: $fileName")
            return RouterSettings()
        } catch (e: Exception) {
            FRLogger.warn("Failed to load settings from SES file: $fileName: ${e.message}")
            return RouterSettings()
        }
    }

    override fun getSettings(): RouterSettings? {
        return settings
    }

    override fun getSourceName(): String {
        return "SES file: $fileName"
    }

    override fun getPriority(): Int {
        return PRIORITY
    }
}
