package app.freerouting.settings.sources

import app.freerouting.logger.FRLogger
import app.freerouting.management.ReflectionUtil
import app.freerouting.settings.RouterSettings
import app.freerouting.settings.SettingsSource
import java.util.HashMap

/**
 * Provides router settings from environment variables.
 *
 * Environment variables must start with "FREEROUTING__ROUTER__" prefix.
 * Double underscores are converted to dots for nested properties.
 *
 * Examples:
 * - FREEROUTING__ROUTER__MAX_PASSES=100 → router.max_passes = 100
 * - FREEROUTING__ROUTER__OPTIMIZER__MAX_THREADS=4 → router.optimizer.max_threads = 4
 * - FREEROUTING__ROUTER__VIAS_ALLOWED=false → router.vias_allowed = false
 *
 * Priority: 55 (between GUI and CLI)
 * - Higher than GUI (50): Environment variables override interactive GUI settings
 * - Lower than CLI (60): Command-line arguments override environment variables
 * - Lower than API (70): API calls have highest priority
 */
class EnvironmentVariablesSource(environment: Map<String, String>) : SettingsSource {

    private val settings: RouterSettings
    private val parsedVariables: MutableMap<String, String> = HashMap()

    companion object {
        private const val PRIORITY = 55
        private const val ENV_PREFIX = "FREEROUTING__"
        private const val ROUTER_PREFIX = "ROUTER__"
    }

    /**
     * Creates an EnvironmentVariablesSource by parsing system environment variables.
     */
    constructor() : this(System.getenv())

    init {
        this.settings = parseEnvironmentVariables(environment)
    }

    private fun parseEnvironmentVariables(environment: Map<String, String>): RouterSettings {
        val settings = RouterSettings()
        var parsedCount = 0

        for ((key, value) in environment) {
            // Only process variables starting with FREEROUTING__ROUTER__
            if (!key.startsWith(ENV_PREFIX + ROUTER_PREFIX)) {
                continue
            }

            // Remove the FREEROUTING__ROUTER__ prefix and convert to property path
            val propertyPath = key
                .substring((ENV_PREFIX + ROUTER_PREFIX).length)
                .lowercase()
                .replace("__", ".")

            // Try to set the value using reflection
            try {
                ReflectionUtil.setFieldValue(settings, propertyPath, value)
                parsedVariables[key] = value
                parsedCount++
                FRLogger.debug("Parsed environment variable: $key → $propertyPath = $value")
            } catch (e: NoSuchFieldException) {
                FRLogger.warn("Unknown router setting in environment variable: $key (property: $propertyPath)")
            } catch (e: Exception) {
                FRLogger.warn("Failed to parse environment variable: $key = $value: ${e.message}")
            }
        }

        if (parsedCount > 0) {
            FRLogger.info("Parsed $parsedCount router setting(s) from environment variables")
        } else {
            FRLogger.debug("No router settings found in environment variables")
        }

        return settings
    }

    override fun getSettings(): RouterSettings? {
        return settings
    }

    override fun getSourceName(): String {
        return "Environment Variables"
    }

    override fun getPriority(): Int {
        return PRIORITY
    }

    /**
     * Gets the parsed environment variables for debugging/logging.
     *
     * @return Map of environment variable names to values
     */
    fun getParsedVariables(): Map<String, String> {
        return HashMap(parsedVariables)
    }

    /**
     * Gets the number of environment variables that were successfully parsed.
     *
     * @return Count of parsed variables
     */
    fun getParsedCount(): Int {
        return parsedVariables.size
    }
}
