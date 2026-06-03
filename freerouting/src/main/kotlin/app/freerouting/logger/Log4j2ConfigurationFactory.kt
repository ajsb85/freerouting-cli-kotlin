package app.freerouting.logger

import java.io.File
import java.net.URI
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Order
import org.apache.logging.log4j.core.config.plugins.Plugin

/**
 * Custom Log4j2 ConfigurationFactory that programmatically builds the logging
 * configuration based on system properties set early in the application
 * startup.
 *
 * This eliminates the need for runtime configuration manipulation which causes
 * threading issues and exceptions.
 */
@Plugin(name = "FreeroutingConfigurationFactory", category = ConfigurationFactory.CATEGORY)
@Order(50)
class Log4j2ConfigurationFactory : ConfigurationFactory() {

    override fun getSupportedTypes(): Array<String> {
        return arrayOf("*")
    }

    override fun getConfiguration(loggerContext: LoggerContext?, source: ConfigurationSource?): Configuration? {
        return getConfiguration(loggerContext, source?.toString() ?: "", null as URI?)
    }

    override fun getConfiguration(loggerContext: LoggerContext?, name: String?, configLocation: URI?): Configuration {
        val builder = newConfigurationBuilder()

        // Read configuration from system properties
        val consoleEnabled = getBooleanProperty("freerouting.logging.console.enabled", true)
        val consoleLevel = getProperty("freerouting.logging.console.level", "INFO") ?: "INFO"

        val fileEnabled = getBooleanProperty("freerouting.logging.file.enabled", true)
        val fileLevel = getProperty("freerouting.logging.file.level", "DEBUG") ?: "DEBUG"
        val fileLocation = getProperty("freerouting.logging.file.location", null)
        val filePattern = getProperty("freerouting.logging.file.pattern", PATTERN) ?: PATTERN

        // Set configuration name and status
        builder.setConfigurationName("FreeroutingConfiguration")
        builder.setStatusLevel(Level.WARN)

        // Create Console appender if enabled
        if (consoleEnabled) {
            val consoleAppender = builder.newAppender("Console", "Console")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                .add(builder.newLayout("PatternLayout")
                    .addAttribute("pattern", PATTERN))
            builder.add(consoleAppender)
        }

        // Create File appender if enabled
        if (fileEnabled && !fileLocation.isNullOrBlank()) {
            // Ensure parent directory exists
            val logFile = File(fileLocation)
            val parentDir = logFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            // Keep investigations simple: write to a single growing, uncompressed log file.
            val fileAppender = builder.newAppender("File", "File")
                .addAttribute("fileName", fileLocation)
                .addAttribute("immediateFlush", true)
                .addAttribute("bufferedIO", true)
                .addAttribute("bufferSize", 8192)
                .add(builder.newLayout("PatternLayout")
                    .addAttribute("pattern", filePattern))
            builder.add(fileAppender)
        }

        // Create stderr appender for errors
        val stderrAppender = builder.newAppender("stderr", "Console")
            .addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR)
            .add(builder.newLayout("PatternLayout")
                .addAttribute("pattern", filePattern)) // Use the same pattern for stderr
        builder.add(stderrAppender)

        // Configure root logger
        val rootLogger = builder.newRootLogger(Level.ALL)

        if (consoleEnabled) {
            rootLogger.add(builder.newAppenderRef("Console")
                .addAttribute("level", parseLevel(consoleLevel)))
        }

        if (fileEnabled && !fileLocation.isNullOrBlank()) {
            rootLogger.add(builder.newAppenderRef("File")
                .addAttribute("level", parseLevel(fileLevel)))
        }

        // Always add stderr for ERROR level
        rootLogger.add(builder.newAppenderRef("stderr")
            .addAttribute("level", Level.ERROR))

        builder.add(rootLogger)

        return builder.build()
    }

    private fun getProperty(key: String, defaultValue: String?): String? {
        val value = System.getProperty(key)
        return if (!value.isNullOrBlank()) value else defaultValue
    }

    private fun getBooleanProperty(key: String, defaultValue: Boolean): Boolean {
        val value = System.getProperty(key)
        return if (value != null) value.toBoolean() else defaultValue
    }

    private fun parseLevel(level: String): Level {
        return try {
            Level.valueOf(level.uppercase())
        } catch (e: Exception) {
            Level.INFO
        }
    }

    companion object {
        private const val PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-6level %msg%n"
    }
}
