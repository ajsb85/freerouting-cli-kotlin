package app.freerouting.settings

import app.freerouting.autoroute.BoardUpdateStrategy
import app.freerouting.autoroute.ItemSelectionStrategy
import app.freerouting.constants.Constants.FREEROUTING_VERSION
import app.freerouting.core.BoardFileDetails
import app.freerouting.core.FileFormat
import app.freerouting.logger.FRLogger
import app.freerouting.management.ReflectionUtil
import app.freerouting.management.gson.GsonProvider
import app.freerouting.settings.sources.DefaultSettings
import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class GlobalSettings : Serializable {

    @SerializedName("profile")
    @JvmField
    val userProfileSettings = UserProfileSettings()

    @SerializedName("gui")
    @JvmField
    val guiSettings = GuiSettings()

    @Deprecated("Use settingsMergerPrototype to obtain merged RouterSettings.")
    @SerializedName("router")
    @JvmField
    val routerSettings = RouterSettings()

    @SerializedName("drc")
    @JvmField
    val drcSettings = DesignRulesCheckerSettings()

    @SerializedName("usage_and_diagnostic_data")
    @JvmField
    val usageAndDiagnosticData = UsageAndDiagnosticDataSettings()

    @SerializedName("feature_flags")
    @JvmField
    val featureFlags = FeatureFlagsSettings()

    @SerializedName("api_server")
    @JvmField
    val apiServerSettings = ApiServerSettings()

    @SerializedName("mcp_server")
    @JvmField
    val mcpServerSettings = McpServerSettings()

    @SerializedName("statistics")
    @JvmField
    val statistics = StatisticsSettings()

    @SerializedName("logging")
    @JvmField
    val logging = LoggingSettings()

    @SerializedName("debug")
    @Transient
    @JvmField
    val debugSettings = DebugSettings()

    @SerializedName("version")
    @JvmField
    var version: String? = null

    @Transient
    @JvmField
    var show_help_option: Boolean = false

    @Transient
    @JvmField
    var drc_report_file: BoardFileDetails? = null

    @Transient
    @JvmField
    var initialInputFile: String? = null

    @Transient
    @JvmField
    var initialOutputFile: String? = null

    @Transient
    @JvmField
    var initialRulesFile: String? = null

    @Transient
    @JvmField
    var design_session_filename: String? = null

    @Transient
    @JvmField
    var currentLocale: Locale = Locale.getDefault()

    @Transient
    @JvmField
    var settingsMergerProtype: SettingsMerger

    @Transient
    @JvmField
    val runtimeEnvironment = RuntimeEnvironment()

    private val supportedLanguages = arrayOf(
        "en", "de", "zh", "zh_TW", "hi", "es", "it", "fr", "ar", "bn", "ru", "pt", "ja", "ko"
    )

    init {
        // validate and set the current locale
        if (!supportedLanguages.contains(currentLocale.language)) {
            // the fallback language is English
            currentLocale = Locale.ENGLISH
        }
        settingsMergerProtype = SettingsMerger(DefaultSettings())
    }

    fun applyNonRouterEnvironmentVariables() {
        for ((key, value) in System.getenv()) {
            if (key.startsWith("FREEROUTING__")) {
                val propertyName = key
                    .substring("FREEROUTING__".length)
                    .lowercase()
                    .replace("__", ".")

                // Skip router settings - they're handled by EnvironmentVariablesSource
                // to prevent conflicts with the SettingsMerger
                if (propertyName.startsWith("router.")) {
                    continue
                }

                setValue(propertyName, value)
            }
        }
    }

    fun setValue(propertyName: String, newValue: String): Boolean {
        return try {
            ReflectionUtil.setFieldValue(this, propertyName, newValue)
            true
        } catch (e: NoSuchFieldException) {
            FRLogger.warn("Unknown settings property: $propertyName")
            false
        } catch (e: Exception) {
            FRLogger.error("Failed to set property value for: $propertyName", e)
            false
        }
    }

    fun getCurrentLocale(): Locale {
        return currentLocale
    }

    fun applyCommandLineArguments(p_args: Array<String>) {
        var i = 0
        while (i < p_args.size) {
            try {
                if (p_args[i].startsWith("--")) {
                    val parts = p_args[i].substring(2).split("=", limit = 2)
                    if (parts.size == 2 && parts[0] != "user_data_path") {
                        if (parts[0].startsWith("debug.")) {
                            if (parts[0] == "debug.enable_detailed_logging") {
                                debugSettings.enableDetailedLogging = parts[1].toBoolean()
                            } else if (parts[0] == "debug.single_step_execution") {
                                debugSettings.singleStepExecution = parts[1].toBoolean()
                            } else if (parts[0] == "debug.trace_insertion_delay") {
                                debugSettings.traceInsertionDelay = parts[1].toInt()
                            } else if (parts[0] == "debug.filter_by_net") {
                                val nets = parts[1].split(",")
                                for (net in nets) {
                                    debugSettings.filterByNet.add(net.trim().lowercase())
                                }
                            }
                        } else {
                            setValue(parts[0], parts[1])
                        }
                    } else if (parts[0] != "user_data_path") {
                        FRLogger.warn("Unknown command line argument: ${p_args[i]}")
                    }
                } else if (p_args[i].startsWith("-de")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        val filesBuilder = StringBuilder()
                        var j = i + 1
                        while (j < p_args.size && !p_args[j].startsWith("-")) {
                            if (filesBuilder.isNotEmpty()) {
                                filesBuilder.append(" ")
                            }
                            filesBuilder.append(p_args[j])
                            j++
                        }

                        val filesString = filesBuilder.toString()
                        val files = filesString.split(Regex("[+\\s]+"))

                        var hasDsn = false
                        var hasSes = false
                        var hasRules = false

                        for (file in files) {
                            val trimmedFile = file.trim()
                            if (trimmedFile.isEmpty()) {
                                continue
                            }

                            val lowerFile = trimmedFile.lowercase()
                            if (lowerFile.endsWith(".dsn")) {
                                if (hasDsn) {
                                    FRLogger.warn("Multiple DSN files provided in -de argument. Only the last one will be used.")
                                }
                                initialInputFile = trimmedFile
                                hasDsn = true
                            } else if (lowerFile.endsWith(".ses")) {
                                if (hasSes) {
                                    FRLogger.warn("Multiple SES files provided in -de argument. Only the last one will be used.")
                                }
                                design_session_filename = trimmedFile
                                hasSes = true
                            } else if (lowerFile.endsWith(".rules")) {
                                if (hasRules) {
                                    FRLogger.warn("Multiple RULES files provided in -de argument. Only the last one will be used.")
                                }
                                initialRulesFile = trimmedFile
                                hasRules = true
                            } else {
                                FRLogger.warn("Unknown file type in -de argument: $trimmedFile. Expected .dsn, .ses, or .rules")
                            }
                        }
                        i = j - 1
                    }
                } else if (p_args[i].startsWith("-di")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        guiSettings.inputDirectory = p_args[i + 1]
                        i++
                    }
                } else if (p_args[i].startsWith("-do")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        initialOutputFile = p_args[i + 1]
                        i++
                    }
                } else if (p_args[i].startsWith("-drc")) {
                    routerSettings.enabled = false
                    drcSettings.enabled = true
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        drc_report_file = BoardFileDetails().apply {
                            format = FileFormat.DRC_JSON
                            setFilename(p_args[i + 1])
                        }
                        i++
                    }
                } else if (p_args[i].startsWith("-dr")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        initialRulesFile = p_args[i + 1]
                        i++
                    }
                } else if (p_args[i].startsWith("-mp")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        var passes = Integer.decode(p_args[i + 1])
                        if (passes < 0) {
                            passes = 1
                        }
                        if (passes > 9999) {
                            passes = 9999
                        }
                        routerSettings.maxPasses = passes
                        i++
                    }
                } else if (p_args[i].startsWith("-mt")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        val opt = routerSettings.optimizer ?: RouterOptimizerSettings().also { routerSettings.optimizer = it }
                        var threads = Integer.decode(p_args[i + 1])
                        if (threads < 0) {
                            threads = 0
                        }
                        if (threads > 1024) {
                            threads = 1024
                        }
                        opt.maxThreads = threads
                        i++
                    }
                } else if (p_args[i].startsWith("-oit")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        val opt = routerSettings.optimizer ?: RouterOptimizerSettings().also { routerSettings.optimizer = it }
                        var threshold = p_args[i + 1].toFloat() / 100
                        if (threshold <= 0) {
                            threshold = 0.0f
                        }
                        opt.optimizationImprovementThreshold = threshold
                        i++
                    }
                } else if (p_args[i].startsWith("-us")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        val opt = routerSettings.optimizer ?: RouterOptimizerSettings().also { routerSettings.optimizer = it }
                        val op = p_args[i + 1].lowercase().trim()
                        opt.boardUpdateStrategy = if (op == "global") {
                            BoardUpdateStrategy.GLOBAL_OPTIMAL
                        } else if (op == "hybrid") {
                            BoardUpdateStrategy.HYBRID
                        } else {
                            BoardUpdateStrategy.GREEDY
                        }
                        i++
                    }
                } else if (p_args[i].startsWith("-is")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        val opt = routerSettings.optimizer ?: RouterOptimizerSettings().also { routerSettings.optimizer = it }
                        val op = p_args[i + 1].lowercase().trim()
                        opt.itemSelectionStrategy = if (op.startsWith("seq")) {
                            ItemSelectionStrategy.SEQUENTIAL
                        } else if (op.startsWith("rand")) {
                            ItemSelectionStrategy.RANDOM
                        } else {
                            ItemSelectionStrategy.PRIORITIZED
                        }
                        i++
                    }
                } else if (p_args[i].startsWith("-hr")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        val opt = routerSettings.optimizer ?: RouterOptimizerSettings().also { routerSettings.optimizer = it }
                        opt.hybridRatio = p_args[i + 1].trim()
                        i++
                    }
                } else if (p_args[i] == "-l") {
                    var localeString = ""
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        localeString = p_args[i + 1].lowercase().replace("-", "_")
                        i++
                    }

                    if (localeString.startsWith("en")) {
                        currentLocale = Locale.ENGLISH
                    } else if (localeString.startsWith("de")) {
                        currentLocale = Locale.GERMAN
                    } else if (localeString.startsWith("zh_tw")) {
                        currentLocale = Locale.TRADITIONAL_CHINESE
                    } else if (localeString.startsWith("zh")) {
                        currentLocale = Locale.SIMPLIFIED_CHINESE
                    } else if (localeString.startsWith("hi")) {
                        currentLocale = Locale.forLanguageTag("hi-IN")
                    } else if (localeString.startsWith("es")) {
                        currentLocale = Locale.forLanguageTag("es-ES")
                    } else if (localeString.startsWith("it")) {
                        currentLocale = Locale.forLanguageTag("it-IT")
                    } else if (localeString.startsWith("fr")) {
                        currentLocale = Locale.FRENCH
                    } else if (localeString.startsWith("ar")) {
                        currentLocale = Locale.forLanguageTag("ar-EG")
                    } else if (localeString.startsWith("bn")) {
                        currentLocale = Locale.forLanguageTag("bn-BD")
                    } else if (localeString.startsWith("ru")) {
                        currentLocale = Locale.forLanguageTag("ru-RU")
                    } else if (localeString.startsWith("pt")) {
                        currentLocale = Locale.forLanguageTag("pt-PT")
                    } else if (localeString.startsWith("ja")) {
                        currentLocale = Locale.JAPANESE
                    } else if (localeString.startsWith("ko")) {
                        currentLocale = Locale.KOREAN
                    }
                } else if (p_args[i].startsWith("-dl")) {
                    logging.file.enabled = false
                } else if (p_args[i].startsWith("-da")) {
                    usageAndDiagnosticData.disableAnalytics = true
                } else if (p_args[i].startsWith("-host")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        runtimeEnvironment.host = p_args[i + 1].trim()
                        i++
                    }
                } else if (p_args[i].startsWith("-help")) {
                    show_help_option = true
                } else if (p_args[i].startsWith("-inc")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        routerSettings.ignoreNetClasses = p_args[i + 1].split(",").toTypedArray()
                        i++
                    }
                } else if (p_args[i].startsWith("-dct")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        guiSettings.dialogConfirmationTimeout = p_args[i + 1].toInt()
                        if (guiSettings.dialogConfirmationTimeout <= 0) {
                            guiSettings.dialogConfirmationTimeout = 0
                        }
                        i++
                    }
                } else if (p_args[i].startsWith("-ll")) {
                    if (i + 1 < p_args.size && !p_args[i + 1].startsWith("-")) {
                        logging.console.level = p_args[i + 1].uppercase()
                        i++
                    }
                } else {
                    FRLogger.warn("Unknown command line argument: ${p_args[i]}")
                }
            } catch (e: Exception) {
                FRLogger.error("There was a problem parsing the '${p_args[i]}' parameter", e)
            }
            i++
        }
    }

    fun getDesignDir(): String? {
        return guiSettings.inputDirectory
    }

    fun getMaxPasses(): Int {
        return routerSettings.maxPasses ?: 9999
    }

    fun getNumThreads(): Int {
        return routerSettings.optimizer?.maxThreads ?: 1
    }

    fun getHybridRatio(): String? {
        return routerSettings.optimizer?.hybridRatio
    }

    fun getBoardUpdateStrategy(): BoardUpdateStrategy {
        return routerSettings.optimizer?.boardUpdateStrategy ?: BoardUpdateStrategy.GREEDY
    }

    fun getItemSelectionStrategy(): ItemSelectionStrategy {
        return routerSettings.optimizer?.itemSelectionStrategy ?: ItemSelectionStrategy.PRIORITIZED
    }

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        @get:JvmName("getUserDataPath")
        @set:JvmName("setUserDataPath")
        var userDataPath: Path = Path.of(System.getProperty("java.io.tmpdir"), "freerouting")
            private set

        @JvmStatic
        @get:JvmName("getConfigurationFilePath")
        @set:JvmName("setConfigurationFilePath")
        var configurationFilePath: Path = userDataPath.resolve("freerouting.json")
            private set

        private var isUserDataPathLocked = false

        @JvmStatic
        fun lockUserDataPath() {
            isUserDataPathLocked = true
        }

        @JvmStatic
        fun setUserDataPath(newPath: Path) {
            if (!isUserDataPathLocked) {
                userDataPath = newPath
                configurationFilePath = newPath.resolve("freerouting.json")
            }
        }

        @JvmStatic
        fun resetForTesting() {
            isUserDataPathLocked = false
            userDataPath = Path.of(System.getProperty("java.io.tmpdir"), "freerouting")
            configurationFilePath = userDataPath.resolve("freerouting.json")
        }

        @JvmStatic
        fun getReleaseSafeVersion(): String {
            val v = FREEROUTING_VERSION
            val snapshotIdx = v.indexOf("-SNAPSHOT")
            return if (snapshotIdx >= 0) v.substring(0, snapshotIdx) else v
        }

        @JvmStatic
        fun compareVersionStrings(v1: String?, v2: String?): Int {
            if (v1 == null && v2 == null) return 0
            if (v1 == null) return -1
            if (v2 == null) return 1

            val parts1 = v1.split(".")
            val parts2 = v2.split(".")
            val len = maxOf(parts1.size, parts2.size)

            for (i in 0 until len) {
                try {
                    val n1 = if (i < parts1.size) parts1[i].toInt() else 0
                    val n2 = if (i < parts2.size) parts2[i].toInt() else 0
                    if (n1 != n2) {
                        return n1.compareTo(n2)
                    }
                } catch (e: NumberFormatException) {
                    val s1 = if (i < parts1.size) parts1[i] else ""
                    val s2 = if (i < parts2.size) parts2[i] else ""
                    val cmp = s1.compareTo(s2)
                    if (cmp != 0) {
                        return cmp
                    }
                }
            }
            return 0
        }

        @JvmStatic
        @Throws(IOException::class)
        fun load(): GlobalSettings? {
            var loadedSettings: GlobalSettings? = null
            try {
                Files.newBufferedReader(configurationFilePath, StandardCharsets.UTF_8).use { reader ->
                    loadedSettings = GsonProvider.GSON.fromJson(reader, GlobalSettings::class.java)
                }
            } catch (e: Exception) {
                if (e is com.google.gson.JsonSyntaxException || e is com.google.gson.JsonIOException) {
                    FRLogger.warn(
                        "freerouting.json at '$configurationFilePath' is corrupt or cannot be parsed — starting with default settings. " +
                        "Delete the file or fix its JSON content manually to suppress this message. " +
                        "Parse error: ${e.message}"
                    )
                    return null
                }
                throw e
            }

            val defaultSettings = GlobalSettings()
            val currentSettings = loadedSettings
            if (currentSettings != null) {
                val fileVersion = currentSettings.version
                val currentVersion = getReleaseSafeVersion()

                if (fileVersion == null) {
                    FRLogger.warn(
                        "freerouting.json at '$configurationFilePath' has no version field (very old config file). " +
                        "Some settings may not be available and have been reset to their defaults. " +
                        "The file will be re-saved with the current version ($currentVersion)."
                    )
                } else {
                    val cmp = compareVersionStrings(fileVersion, currentVersion)
                    if (cmp < 0) {
                        FRLogger.warn(
                            "freerouting.json at '$configurationFilePath' was written by an older version of Freerouting (file: $fileVersion, current: $currentVersion). " +
                            "No migration logic is implemented for this version transition, so some settings may have been reset to their defaults. " +
                            "The file will be re-saved with the updated version string."
                        )
                    } else if (cmp > 0) {
                        FRLogger.warn(
                            "freerouting.json at '$configurationFilePath' was written by a newer version of Freerouting (file: $fileVersion, current: $currentVersion). " +
                            "Some settings from the newer version may not be understood or may be ignored. " +
                            "Consider upgrading Freerouting to the version that originally wrote this file."
                        )
                    }
                }

                val isSaveNeeded = currentVersion != fileVersion

                ReflectionUtil.copyFields(defaultSettings, currentSettings)
                currentSettings.version = currentVersion

                if (isSaveNeeded) {
                    FRLogger.info("freerouting.json config version changed from '$fileVersion' to '$currentVersion' – re-saving configuration.")
                    saveAsJson(currentSettings)
                }
            }

            return currentSettings
        }

        @JvmStatic
        @Throws(IOException::class)
        fun saveAsJson(globalSettings: GlobalSettings) {
            try {
                Files.createDirectories(configurationFilePath.parent)
            } catch (e: AccessDeniedException) {
                throw AccessDeniedException(
                    configurationFilePath.parent.toString(), null,
                    "Cannot create the user-data directory '${configurationFilePath.parent}' — permission denied. freerouting.json cannot be saved. " +
                    "Check that the process has write permission on the parent directory. " +
                    "In Docker deployments, verify that the volume is mounted with write access."
                )
            } catch (e: IOException) {
                throw IOException(
                    "Failed to create the user-data directory '${configurationFilePath.parent}': ${e.message}. freerouting.json cannot be saved.",
                    e
                )
            }

            globalSettings.version = getReleaseSafeVersion()

            try {
                Files.newBufferedWriter(configurationFilePath, StandardCharsets.UTF_8).use { writer ->
                    GsonProvider.GSON.toJson(globalSettings, writer)
                }
            } catch (e: AccessDeniedException) {
                throw AccessDeniedException(
                    configurationFilePath.toString(), null,
                    "Cannot write freerouting.json to '$configurationFilePath' — permission denied. Settings won't be persisted. " +
                    "Check that the process has write permission on the file and its parent directory. " +
                    "In Docker deployments, verify that the volume is mounted with write access."
                )
            } catch (e: IOException) {
                throw IOException(
                    "Failed to write freerouting.json to '$configurationFilePath': ${e.message}. Settings won't be persisted.",
                    e
                )
            }
        }

        @JvmStatic
        fun setDefaultValue(propertyName: String, newValue: String): Boolean {
            return try {
                val gs = load()
                if (gs != null) {
                    gs.setValue(propertyName, newValue)
                    saveAsJson(gs)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                FRLogger.error("Failed to save property value for: $propertyName", e)
                false
            }
        }
    }
}
