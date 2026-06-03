package app.freerouting.datastructures

import java.io.File

/**
 * Used in the file chooser to filter all files which do not have an extension from the input array.
 */
class FileFilter(private val extensions: Array<String>) : javax.swing.filechooser.FileFilter() {

    override fun getDescription(): String {
        val message = StringBuilder("Files with the extensions")
        for (i in extensions.indices) {
            message.append(" .").append(extensions[i])
            if (i == extensions.size - 2) {
                message.append(" or")
            } else if (i < extensions.size - 2) {
                message.append(",")
            }
        }
        return message.toString()
    }

    override fun accept(p_file: File): Boolean {
        if (p_file.isDirectory) {
            return true
        }
        val file_name = p_file.name
        val name_parts = file_name.split("\\.".toRegex()).toTypedArray()
        if (name_parts.size < 2) {
            return false
        }
        val found_extension = name_parts[name_parts.size - 1]
        for (extension in extensions) {
            if (found_extension.equals(extension, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
