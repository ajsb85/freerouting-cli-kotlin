package app.freerouting.settings.sources

import app.freerouting.logger.FRLogger
import app.freerouting.management.ReflectionUtil
import app.freerouting.settings.RouterSettings
import app.freerouting.settings.SettingsSource
import java.util.HashMap

/**
 * Provides router settings from command-line arguments.
 * Only the settings specified via CLI will be non-null.
 */
class CliSettings(args: Array<String>) : SettingsSource {

    private val settings: RouterSettings
    private val parsedArguments: MutableMap<String, String> = HashMap()

    companion object {
        private const val PRIORITY = 60
    }

    init {
        this.settings = parseArguments(args)
    }

    private fun parseArguments(args: Array<String>): RouterSettings {
        val settings = RouterSettings()
        var hasDesignInputArgument = false
        var hasDesignOutputArgument = false
        var hasExplicitRouterEnabledArgument = false

        // Parse command-line arguments and populate only the specified settings
        // This uses the same logic as GlobalSettings.applyCommandLineArguments
        // but only for router-related settings

        var i = 0
        while (i < args.size) {
            val arg = args[i]

            if (arg.startsWith("--")) {
                // Handle --property=value format
                if (arg.contains("=")) {
                    val parts = arg.substring(2).split("=", limit = 2)
                    val propertyName = parts[0]
                    val value = if (parts.size > 1) parts[1] else ""

                    if ("router.enabled" == propertyName) {
                        hasExplicitRouterEnabledArgument = true
                    }

                    if (propertyName.startsWith("router.")) {
                        applyRouterSetting(settings, propertyName, value)
                    }
                }
            } else if (arg.startsWith("-")) {
                // Handle -flag value format
                val flag = arg.substring(1)
                val value = if (i + 1 < args.size && !args[i + 1].startsWith("-")) args[++i] else ""

                if ("de" == flag) {
                    hasDesignInputArgument = true
                } else if ("do" == flag) {
                    hasDesignOutputArgument = true
                }

                // Map short flags to router settings
                val propertyName = mapFlagToProperty(flag)
                if (propertyName != null && propertyName.startsWith("router.")) {
                    applyRouterSetting(settings, propertyName, value)
                }
            }
            i++
        }

        // Legacy batch invocation (`-de ... -do ...`) is expected to route immediately.
        // Force router enabled unless the caller explicitly set --router.enabled=... .
        if (hasDesignInputArgument && hasDesignOutputArgument && !hasExplicitRouterEnabledArgument) {
            settings.enabled = true
            FRLogger.debug("Applied CLI router setting: router.enabled = true (implicit from -de/-do batch mode)")
        }

        return settings
    }

    private fun applyRouterSetting(settings: RouterSettings, propertyName: String, value: String) {
        try {
            // Remove "router." prefix if present
            val fieldPath = if (propertyName.startsWith("router.")) {
                propertyName.substring(7)
            } else {
                propertyName
            }

            ReflectionUtil.setFieldValue(settings, fieldPath, value)
            parsedArguments[propertyName] = value
            FRLogger.debug("Applied CLI router setting: $propertyName = $value")
        } catch (e: Exception) {
            FRLogger.warn("Failed to apply CLI router setting: $propertyName: ${e.message}")
        }
    }

    private fun mapFlagToProperty(flag: String): String? {
        // Map short flags to full property names
        return when (flag) {
            "mp" -> "router.max_passes"
            "mt" -> "router.max_threads"
            else -> null
        }
    }

    override fun getSettings(): RouterSettings? {
        return settings
    }

    override fun getSourceName(): String {
        return "CLI Arguments"
    }

    override fun getPriority(): Int {
        return PRIORITY
    }

    /**
     * Gets the parsed arguments for debugging/logging.
     *
     * @return Map of property names to values
     */
    fun getParsedArguments(): Map<String, String> {
        return HashMap(parsedArguments)
    }
}
