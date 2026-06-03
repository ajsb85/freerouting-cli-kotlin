package app.freerouting.core

import app.freerouting.board.RoutingBoard
import app.freerouting.core.events.RoutingJobLogEntryAddedEvent
import app.freerouting.core.events.RoutingJobLogEntryAddedEventListener
import app.freerouting.core.events.RoutingJobUpdatedEvent
import app.freerouting.core.events.RoutingJobUpdatedEventListener
import app.freerouting.io.specctra.RulesReader
import app.freerouting.logger.FRLogger
import app.freerouting.logger.LogEntry
import app.freerouting.settings.DesignRulesCheckerSettings
import app.freerouting.settings.RouterSettings
import com.google.gson.annotations.SerializedName
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.ArrayList
import java.util.UUID

/**
 * Represents a job that needs to be processed by the router.
 */
class RoutingJob : Serializable, Comparable<RoutingJob> {

    @SerializedName("id")
    @JvmField
    val id: UUID = UUID.randomUUID()

    @SerializedName("created_at")
    @JvmField
    val createdAt: Instant = Instant.now()

    // events to signal input and output updates
    @Transient
    @JvmField
    protected val settingsUpdatedEventListeners: MutableList<RoutingJobUpdatedEventListener> = ArrayList()

    @Transient
    @JvmField
    protected val inputUpdatedEventListeners: MutableList<RoutingJobUpdatedEventListener> = ArrayList()

    @Transient
    @JvmField
    protected val outputUpdatedEventListeners: MutableList<RoutingJobUpdatedEventListener> = ArrayList()

    @Transient
    @JvmField
    protected val logEntryAddedEventListeners: MutableList<RoutingJobLogEntryAddedEventListener> = ArrayList()

    @SerializedName("short_name")
    @JvmField
    var shortName: String = "N/A"

    @SerializedName("name")
    @JvmField
    var name: String? = null

    @SerializedName("started_at")
    @JvmField
    var startedAt: Instant? = null

    @SerializedName("finished_at")
    @JvmField
    var finishedAt: Instant? = null

    @SerializedName("state")
    @JvmField
    var state: RoutingJobState = RoutingJobState.INVALID

    @SerializedName("stage")
    @JvmField
    var stage: RoutingStage = RoutingStage.IDLE

    @SerializedName("priority")
    @JvmField
    var priority: RoutingJobPriority = RoutingJobPriority.NORMAL

    @SerializedName("session_id")
    @JvmField
    var sessionId: UUID? = null

    @SerializedName("input")
    @JvmField
    var input: BoardFileDetails? = null

    @SerializedName("output")
    @JvmField
    var output: BoardFileDetails? = null

    @SerializedName("drc")
    @JvmField
    var drc: BoardFileDetails? = null

    @SerializedName("router_settings")
    @JvmField
    var routerSettings: RouterSettings = RouterSettings()

    @SerializedName("drc_settings")
    @JvmField
    var drcSettings: DesignRulesCheckerSettings = DesignRulesCheckerSettings()

    @SerializedName("resource_usage")
    @JvmField
    var resourceUsage: RouterJobResourceUsage = RouterJobResourceUsage()

    @Transient
    @JvmField
    var thread: StoppableThread? = null

    @Transient
    @JvmField
    var board: RoutingBoard? = null

    @Transient
    @JvmField
    var timeoutAt: Instant? = null

    private var isCancelledByUser = false

    fun isCancelledByUser(): Boolean {
        return isCancelledByUser
    }

    fun setCancelledByUser(cancelledByUser: Boolean) {
        isCancelledByUser = cancelledByUser
    }

    @SerializedName("current_pass")
    private var currentPass = 0

    /**
     * We need a parameterless constructor for the serialization.
     */
    constructor() {
        this.name = "J-" + this.id
            .toString()
            .substring(0, 6)
            .uppercase()
        this.shortName = this.id
            .toString()
            .substring(0, 6)
            .uppercase()
    }

    /**
     * Creates a new instance of DesignFile and prepares the intermediate file
     * handling.
     */
    constructor(sessionId: UUID) : this() {
        this.sessionId = sessionId
        this.shortName = (this.sessionId
            .toString()
            .substring(0, 6)
            .uppercase() + "\\"
            + this.id
                .toString()
                .substring(0, 6)
                .uppercase())
    }

    fun getCurrentPass(): Int {
        return currentPass
    }

    fun setCurrentPass(currentPass: Int) {
        this.currentPass = currentPass
    }

    fun getDuration(): Duration? {
        val start = startedAt ?: return null
        val finish = finishedAt
        return if (finish != null) {
            Duration.between(start, finish)
        } else {
            Duration.between(start, Instant.now())
        }
    }

    fun setInput(inputFileContent: ByteArray?): Boolean {
        this.input = BoardFileDetails()
        this.input!!.addUpdatedEventListener { _ -> this.fireInputUpdatedEvent() }
        return this.tryToSetInput(inputFileContent)
    }

    fun getRulesFile(): File {
        return File(changeFileExtension(this.output!!.absolutePath, RULES_FILE_EXTENSION))
    }

    fun getEagleScriptFile(): File {
        return File(changeFileExtension(this.output!!.absolutePath, EAGLE_SCRIPT_FILE_EXTENSION))
    }

    fun setDummyInputFile(filename: String?) {
        val inp = BoardFileDetails()
        this.input = inp
        this.output = BoardFileDetails()

        if (filename != null && filename.lowercase().endsWith(DSN_FILE_EXTENSION)) {
            inp.format = FileFormat.DSN
            inp.filename = filename
        }
    }

    private fun tryToSetInput(fileContent: ByteArray?): Boolean {
        if (fileContent == null) {
            return false
        }

        val inp = this.input ?: return false
        inp.format = getFileFormat(fileContent)

        if (inp.format != FileFormat.UNKNOWN) {
            inp.setData(fileContent)
            fireInputUpdatedEvent()
            return true
        }

        return false
    }

    // Changes the file extension of the selected file
    private fun changeFileExtension(filename: String, newFileExtension: String): String {
        val filePath = Path.of(filename)

        // Get the filename and split it into parts
        val originalFullPathWithoutFilename = filePath
            .parent
            .toAbsolutePath()
            .toString()
        val originalFilename = filePath
            .fileName
            .toString()
        val nameParts = originalFilename.split('.')
        if (nameParts.size > 1) {
            val extension = nameParts[nameParts.size - 1].lowercase()
            if (extension == newFileExtension) {
                return filePath.toString()
            }
            val newFilename = originalFilename.substring(0, originalFilename.length - extension.length - 1) + "." + newFileExtension

            return Path
                .of(originalFullPathWithoutFilename, newFilename)
                .toString()
        }

        return Path
            .of(originalFullPathWithoutFilename, "$originalFilename.$newFileExtension")
            .toString()
    }

    fun tryToSetOutputFile(outputFile: File?): Boolean {
        if (outputFile == null) {
            return false
        }

        val ff = getFileFormat(outputFile.toPath())

        if (ff == FileFormat.DSN || ff == FileFormat.FRB || ff == FileFormat.SES || ff == FileFormat.SCR) {
            val out = BoardFileDetails(outputFile)
            this.output = out
            out.addUpdatedEventListener { _ -> this.fireInputUpdatedEvent() }
            out.format = ff
            fireOutputUpdatedEvent()
            return true
        } else {
            return false
        }
    }

    fun getInputFileDetails(): String {
        return BoardFileDetails(this.input!!.file).toString()
    }

    fun getOutputFileDetails(): String {
        return BoardFileDetails(this.output!!.file).toString()
    }

    override fun compareTo(other: RoutingJob): Int {
        return this.priority.ordinal.compareTo(other.priority.ordinal)
    }

    fun getInput(): BoardFileDetails? {
        return input
    }

    @Throws(IOException::class)
    fun setInput(inputFilePath: String) {
        setInput(File(inputFilePath))
    }

    @Throws(IOException::class)
    fun setInput(inputFile: File) {
        // Read the file contents into a byte array and initialize the RoutingJob object
        // with it
        FileInputStream(inputFile).use { fileInputStream ->
            val content = fileInputStream.readAllBytes()

            setInput(content)
            val inp = this.input!!
            inp.filename = inputFile.absolutePath
            if (inp.format == FileFormat.UNKNOWN) {
                // As a fallback method, set the file format based on its extension
                inp.format = getFileFormat(Path.of(inp.absolutePath))
            }

            if (this.input!!.format == FileFormat.FRB) {
                val out = BoardFileDetails()
                this.output = out
                out.addUpdatedEventListener { _ -> this.fireOutputUpdatedEvent() }
                out.filename = changeFileExtension(inp.absolutePath, BINARY_FILE_EXTENSION)
            }

            if (this.input!!.format == FileFormat.DSN) {
                val out = BoardFileDetails()
                this.output = out
                out.addUpdatedEventListener { _ -> this.fireOutputUpdatedEvent() }
                out.filename = changeFileExtension(inp.absolutePath, SES_FILE_EXTENSION)
            }

            if (this.input!!.format != FileFormat.UNKNOWN) {
                val newInput = BoardFileDetails(inputFile)
                this.input = newInput
                newInput.addUpdatedEventListener { _ -> this.fireInputUpdatedEvent() }
                this.name = newInput.filenameWithoutExtension
            }

            fireInputUpdatedEvent()
        }
    }

    fun setSettings(settings: RouterSettings): Boolean {
        // Update the router settings that are defined in the settings parameter. All
        // other settings should remain the same.
        val wereSettingsChanged = this.routerSettings.applyNewValuesFrom(settings) > 0
        fireSettingsUpdatedEvent()

        return wereSettingsChanged
    }

    fun addSettingsUpdatedEventListener(listener: RoutingJobUpdatedEventListener) {
        settingsUpdatedEventListeners.add(listener)
    }

    fun fireSettingsUpdatedEvent() {
        val event = RoutingJobUpdatedEvent(this, this)
        for (listener in settingsUpdatedEventListeners) {
            listener.onRoutingJobUpdated(event)
        }
    }

    fun addInputUpdatedEventListener(listener: RoutingJobUpdatedEventListener) {
        inputUpdatedEventListeners.add(listener)
    }

    fun fireInputUpdatedEvent() {
        val event = RoutingJobUpdatedEvent(this, this)
        for (listener in inputUpdatedEventListeners) {
            listener.onRoutingJobUpdated(event)
        }
    }

    fun addOutputUpdatedEventListener(listener: RoutingJobUpdatedEventListener) {
        outputUpdatedEventListeners.add(listener)
    }

    fun fireOutputUpdatedEvent() {
        val event = RoutingJobUpdatedEvent(this, this)
        for (listener in outputUpdatedEventListeners) {
            listener.onRoutingJobUpdated(event)
        }
    }

    fun addLogEntryAddedEventListener(listener: RoutingJobLogEntryAddedEventListener) {
        logEntryAddedEventListeners.add(listener)
    }

    fun fireLogEntryAddedEvent(logEntry: LogEntry) {
        val event = RoutingJobLogEntryAddedEvent(this, this, logEntry)
        for (listener in logEntryAddedEventListeners) {
            listener.onLogEntryAdded(event)
        }
    }

    fun logInfo(message: String) {
        val logEntry = FRLogger.info("[" + this.shortName + "] " + message, this.id)
        if (logEntry != null) {
            fireLogEntryAddedEvent(logEntry)
        }
    }

    fun logWarning(message: String) {
        val logEntry = FRLogger.warn("[" + this.shortName + "] " + message, this.id)
        if (logEntry != null) {
            fireLogEntryAddedEvent(logEntry)
        }
    }

    fun logError(message: String, ex: Throwable?) {
        val logEntry = FRLogger.error("[" + this.shortName + "] " + message, this.id, ex)
        if (logEntry != null) {
            fireLogEntryAddedEvent(logEntry)
        }
    }

    fun logDebug(message: String) {
        val logEntry = FRLogger.debug("[" + this.shortName + "] " + message, this.id)
        if (logEntry != null) {
            fireLogEntryAddedEvent(logEntry)
        }
    }

    companion object {
        const val DSN_FILE_EXTENSION: String = "dsn"
        const val BINARY_FILE_EXTENSION: String = "frb"
        private const val RULES_FILE_EXTENSION: String = "rules"
        private const val SES_FILE_EXTENSION: String = "ses"
        private const val EAGLE_SCRIPT_FILE_EXTENSION: String = "scr"

        @JvmStatic
        fun getFileFormat(content: ByteArray?): FileFormat {
            if (content == null) {
                return FileFormat.UNKNOWN
            }
            // Open the file as a binary file and read the first 6 bytes to determine the
            // file format
            try {
                ByteArrayInputStream(content).use { fileInputStream ->
                    val buffer = ByteArray(6)
                    val bytesRead = fileInputStream.read(buffer, 0, 6)
                    if (bytesRead == 6) {
                        // Check if the file is a binary file
                        if (buffer[0] == 0xAC.toByte() && buffer[1] == 0xED.toByte() && buffer[2] == 0x00.toByte() && buffer[3] == 0x05.toByte()) {
                            return FileFormat.FRB
                        }

                        // If the first few bytes are 0x0A or 0x13, ignore them
                        while (buffer[0] == 0x0A.toByte() || buffer[0] == 0x0D.toByte()) {
                            buffer[0] = buffer[1]
                            buffer[1] = buffer[2]
                            buffer[2] = buffer[3]
                            buffer[3] = buffer[4]
                            buffer[4] = buffer[5]
                        }

                        // Check if the file is a DSN file (it starts with "(pcb" or "(PCB")
                        if ((buffer[0] == 0x28.toByte() && buffer[1] == 0x70.toByte() && buffer[2] == 0x63.toByte() && buffer[3] == 0x62.toByte())
                            || (buffer[0] == 0x28.toByte() && buffer[1] == 0x50.toByte() && buffer[2] == 0x43.toByte() && buffer[3] == 0x42.toByte())
                        ) {
                            return FileFormat.DSN
                        }

                        // Check if the file is a SES file (it starts with "(ses" or "(SES")
                        if ((buffer[0] == 0x28.toByte() && buffer[1] == 0x73.toByte() && buffer[2] == 0x65.toByte() && buffer[3] == 0x73.toByte())
                            || (buffer[0] == 0x28.toByte() && buffer[1] == 0x53.toByte() && buffer[2] == 0x45.toByte() && buffer[3] == 0x53.toByte())
                        ) {
                            return FileFormat.SES
                        }
                    }
                }
            } catch (_: IOException) {
                // Ignore the exception, it can happen with the build-in template or if the user
                // doesn't choose any file in the file dialog
            }

            return FileFormat.UNKNOWN
        }

        @JvmStatic
        fun getFileFormat(path: Path): FileFormat {
            val filename = path
                .toString()
                .lowercase()
            val parts = filename.split('.')
            if (parts.size > 1) {
                val extension = parts[parts.size - 1].lowercase()
                return when (extension) {
                    DSN_FILE_EXTENSION -> FileFormat.DSN
                    BINARY_FILE_EXTENSION -> FileFormat.FRB
                    "ses" -> FileFormat.SES
                    "scr" -> FileFormat.SCR
                    else -> FileFormat.UNKNOWN
                }
            }

            return FileFormat.UNKNOWN
        }
    }
}
