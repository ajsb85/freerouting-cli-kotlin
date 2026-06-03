package app.freerouting.datastructures

import app.freerouting.logger.FRLogger
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Handles the indenting of scopes while writing to an output text file.
 */
open class IndentFileWriter(p_stream: OutputStream) : OutputStreamWriter(p_stream) {

    private var current_indent_level = 0

    /**
     * Begins a new scope.
     */
    @JvmOverloads
    fun start_scope(newLine: Boolean = true) {
        if (newLine) {
            new_line()
        }

        try {
            write(BEGIN_SCOPE)
        } catch (e: IOException) {
            FRLogger.error("IndentFileWriter.start_scope: unable to write to file", e)
        }
        ++current_indent_level
    }

    /**
     * Closes the latest open scope.
     */
    fun end_scope() {
        --current_indent_level
        new_line()
        try {
            write(END_SCOPE)
        } catch (e: IOException) {
            FRLogger.error("IndentFileWriter.end_scope: unable to write to file", e)
        }
    }

    /**
     * Starts a new line inside a scope.
     */
    fun new_line() {
        try {
            write("\n")
            for (i in 0 until current_indent_level) {
                write(INDENT_STRING)
            }
        } catch (e: IOException) {
            FRLogger.error("IndentFileWriter.new_line: unable to write to file", e)
        }
    }

    companion object {
        private const val INDENT_STRING = "  "
        private const val BEGIN_SCOPE = "("
        private const val END_SCOPE = ")"
    }
}
