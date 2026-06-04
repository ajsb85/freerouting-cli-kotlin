package app.freerouting.core

import app.freerouting.board.BasicBoard
import app.freerouting.core.events.BoardFileDetailsUpdatedEvent
import app.freerouting.core.events.BoardFileDetailsUpdatedEventListener
import app.freerouting.core.scoring.BoardStatistics
import app.freerouting.logger.FRLogger
import app.freerouting.management.gson.GsonProvider
import com.google.gson.annotations.SerializedName
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.zip.CRC32

open class BoardFileDetails : Serializable {

    @Transient
    protected val updatedEventListeners: MutableList<BoardFileDetailsUpdatedEventListener> = ArrayList()

    // The size of the file in bytes
    @SerializedName("size")
    @JvmField
    var size: Long = 0

    // The CRC32 checksum of the data
    @SerializedName("crc32")
    @JvmField
    var crc32: Long = 0

    // The format of the file
    @SerializedName("format")
    @JvmField
    var format: FileFormat = FileFormat.UNKNOWN

    @SerializedName("statistics")
    @JvmField
    var statistics: BoardStatistics = BoardStatistics()

    // The filename only without the path
    @SerializedName("filename")
    @JvmField
    var filename: String = ""

    // The absolute path to the directory of the file
    @SerializedName("path")
    @JvmField
    var directoryPath: String = ""

    @Transient
    @JvmField
    protected var dataBytes: ByteArray = ByteArray(0)

    val absolutePath: String
        get() = Path.of(this.directoryPath, this.filename).toString()

    val data: ByteArrayInputStream
        get() = ByteArrayInputStream(this.dataBytes)

    val file: File?
        get() {
            if (this.filename.isNotEmpty()) {
                return File(Path.of(this.directoryPath, this.filename).toString())
            }
            return null
        }

    val filenameWithoutExtension: String
        get() {
            if (this.filename.contains(".")) {
                return this.filename.substring(0, this.filename.lastIndexOf('.'))
            }
            return this.filename
        }

    constructor()

    /**
     * Creates a new BoardDetails object from a file.
     */
    constructor(file: File) {
        this.setFilename(file.absolutePath)

        try {
            FileInputStream(file).use { fis ->
                this.setData(fis.readAllBytes())
            }
        } catch (_: IOException) {
            // Ignore the exception and continue with the default values
        }
    }

    /**
     * Creates a new BoardDetails object from a RoutingBoard object.
     */
    constructor(board: BasicBoard) {
        this.statistics = BoardStatistics(board)
    }

    /**
     * Saves this object to a UTF-8 JSON file.
     */
    @Throws(IOException::class)
    fun saveAs(filename: String) {
        Files.newBufferedWriter(Path.of(filename), StandardCharsets.UTF_8).use { writer ->
            writer.write(this.toString())
        }
    }

    fun setData(data: ByteArray) {
        this.dataBytes = data
        this.size = data.size.toLong()
        val inputStream = ByteArrayInputStream(this.dataBytes)
        this.crc32 = calculateCrc32(inputStream).value

        // read the file contents to determine the file format
        this.format = RoutingJob.getFileFormat(this.dataBytes)

        // set the statistical data based on the file content
        this.statistics = BoardStatistics(this.dataBytes, this.format)

        fireUpdatedEvent()
    }

    /**
     * Returns a JSON representation of this object.
     */
    override fun toString(): String {
        return GsonProvider.GSON.toJson(this)
    }

    fun getDirectoryPath(): String {
        return this.directoryPath
    }

    fun getFilename(): String {
        return this.filename
    }

    /**
     * Sets both the filename and the path.
     *
     * @param filename The filename to set, optionally with its path.
     */
    fun setFilename(filename: String?) {
        if (filename == null) {
            this.directoryPath = ""
            this.filename = ""
            return
        }

        val path = Path.of(filename).toAbsolutePath()

        if (filename.contains(File.separator)) {
            // separate the filename into its absolute path and its filename only
            this.directoryPath = path.parent.toString()
            // replace the redundant "\.\" with a simple "\"
            this.directoryPath = this.directoryPath.replace("\\.\\", "\\")
            // remove the "/", "\" from the end of the directory path
            this.directoryPath = this.directoryPath.replace(Regex("[/\\\\]+$"), "")
            // remove the "\." from the end of the directory path
            this.directoryPath = this.directoryPath.replace(Regex("\\\\.$"), "")
        } else {
            this.directoryPath = ""
        }

        // set the filename only
        this.filename = path.fileName.toString()

        if (this.format == FileFormat.UNKNOWN) {
            // try to read the file contents to determine the file format
            this.format = RoutingJob.getFileFormat(Path.of(this.filename))
        }

        // add the default file extension if it is missing
        if (this.format != FileFormat.UNKNOWN && !this.filename.contains(".")) {
            var extension = ""
            when (this.format) {
                FileFormat.SES -> extension = "ses"
                FileFormat.DSN -> extension = "dsn"
                FileFormat.FRB -> extension = "frb"
                FileFormat.RULES -> extension = "rules"
                FileFormat.SCR -> extension = "scr"
                else -> {}
            }

            if (extension.isNotEmpty()) {
                this.filename = this.filename + "." + extension
            }
        }

        fireUpdatedEvent()
    }

    fun addUpdatedEventListener(listener: BoardFileDetailsUpdatedEventListener) {
        updatedEventListeners.add(listener)
    }

    fun fireUpdatedEvent() {
        val event = BoardFileDetailsUpdatedEvent(this, this)
        for (listener in updatedEventListeners) {
            listener.onBoardFileDetailsUpdated(event)
        }
    }

    companion object {
        @JvmStatic
        fun calculateCrc32(inputStream: InputStream): CRC32 {
            val crc = CRC32()
            try {
                var cnt: Int
                while (inputStream.read().also { cnt = it } != -1) {
                    crc.update(cnt)
                }
            } catch (e: IOException) {
                FRLogger.error(e.localizedMessage, e)
            }
            return crc
        }
    }
}
