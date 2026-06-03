package app.freerouting.settings.sources

import app.freerouting.logger.FRLogger
import app.freerouting.management.gson.GsonProvider
import app.freerouting.settings.GlobalSettings
import app.freerouting.settings.RouterSettings
import app.freerouting.settings.SettingsSource
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads router settings from the freerouting.json file in the user data folder.
 * Only the settings present in the JSON file will be non-null.
 */
class JsonFileSettings(private val jsonFilePath: Path) : SettingsSource {

    private val settings: RouterSettings = loadSettings()

    companion object {
        private const val PRIORITY = 10
    }

    /**
     * Creates a JsonFileSettings source using the default configuration file path.
     */
    constructor() : this(GlobalSettings.userDataPath.resolve("freerouting.json"))

    private fun loadSettings(): RouterSettings {
        if (!Files.exists(jsonFilePath)) {
            FRLogger.debug("JSON settings file not found: $jsonFilePath")
            return RouterSettings()
        }

        try {
            Files.newBufferedReader(jsonFilePath, StandardCharsets.UTF_8).use { reader ->
                val root = JsonParser.parseReader(reader).asJsonObject
                val routerElement = root.get("router")
                if (routerElement != null && routerElement.isJsonObject) {
                    val loaded = GsonProvider.GSON.fromJson(routerElement, RouterSettings::class.java)
                    FRLogger.debug("Loaded router settings from: $jsonFilePath")
                    return loaded
                }
            }
        } catch (e: IOException) {
            FRLogger.warn("Failed to load settings from JSON file: $jsonFilePath: ${e.message}")
        } catch (e: Exception) {
            FRLogger.warn("Failed to parse JSON settings file: $jsonFilePath: ${e.message}")
        }

        return RouterSettings()
    }

    override fun getSettings(): RouterSettings? {
        return settings
    }

    override fun getSourceName(): String {
        return "freerouting.json"
    }

    override fun getPriority(): Int {
        return PRIORITY
    }
}
