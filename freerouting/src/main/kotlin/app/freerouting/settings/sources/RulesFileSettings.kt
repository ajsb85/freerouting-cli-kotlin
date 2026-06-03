package app.freerouting.settings.sources

import app.freerouting.logger.FRLogger
import app.freerouting.settings.RouterSettings
import app.freerouting.settings.SettingsSource

/**
 * Extracts router settings from RULES files.
 * Only the settings present in the RULES file will be non-null.
 */
class RulesFileSettings(private val fileName: String) : SettingsSource {

    private val settings: RouterSettings = loadSettings()

    companion object {
        private const val PRIORITY = 40
    }

    private fun loadSettings(): RouterSettings {
        try {
            // RULES files contain router settings that should override DSN settings
            // TODO: Integrate with existing RulesFile.read logic

            FRLogger.debug("Loaded router settings from RULES file: $fileName")
            return RouterSettings()
        } catch (e: Exception) {
            FRLogger.warn("Failed to load settings from RULES file: $fileName: ${e.message}")
            return RouterSettings()
        }
    }

    override fun getSettings(): RouterSettings? {
        return settings
    }

    override fun getSourceName(): String {
        return "RULES file: $fileName"
    }

    override fun getPriority(): Int {
        return PRIORITY
    }
}
