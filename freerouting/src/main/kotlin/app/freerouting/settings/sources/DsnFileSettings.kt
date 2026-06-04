package app.freerouting.settings.sources

import app.freerouting.io.specctra.DsnReadResult
import app.freerouting.io.specctra.DsnReader
import app.freerouting.logger.FRLogger
import app.freerouting.settings.RouterSettings
import app.freerouting.settings.SettingsSource
import java.io.InputStream

/**
 * Extracts router settings from DSN (Specctra Design) files.
 * Only the settings present in the DSN file will be non-null.
 */
class DsnFileSettings(inputStream: InputStream, private val filename: String) : SettingsSource {

    private val settings: RouterSettings

    init {
        val result = DsnReader.readMetadata(inputStream)

        var extracted: RouterSettings? = null
        var layerCount = 0

        if (result is DsnReadResult.Success && result.metadata != null) {
            extracted = result.metadata.routerSettings
            layerCount = result.metadata.layerCount
        }

        // Start with whatever the DSN's autoroute block provided (or a blank slate).
        val rs = extracted ?: RouterSettings()

        // Always seed the layer arrays from the actual DSN layer count so that any board
        // with more or fewer than 2 layers gets correctly-sized arrays in the merged result –
        // well before applyBoardSpecificOptimizations() is called.
        if (layerCount > 0 && rs.getLayerCount() == 0) {
            rs.setLayerCount(layerCount)
        }

        this.settings = rs
        FRLogger.debug("Loaded router settings from DSN file: $filename ($layerCount layers)")
    }

    override fun getSettings(): RouterSettings {
        return settings
    }

    override fun getSourceName(): String {
        return "DSN file: $filename"
    }

    override fun getPriority(): Int {
        return PRIORITY
    }

    companion object {
        private const val PRIORITY = 20
    }
}
