package app.freerouting.core

import app.freerouting.geometry.planar.Shape
import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.util.Vector

/**
 * Describes a library of component packages.
 */
class Packages(
    @JvmField val padstack_list: Padstacks
) : Serializable {

    /**
     * The array of packages in this object
     */
    private val package_arr = Vector<Package>()

    /**
     * Returns the package with the input name and the input side or null, if no such package exists.
     */
    fun get(p_name: String, p_is_front: Boolean): Package? {
        var other_side_package: Package? = null
        for (curr_package in package_arr) {
            if (curr_package != null && curr_package.name.equals(p_name, ignoreCase = true)) {
                if (curr_package.is_front == p_is_front) {
                    return curr_package
                }
                other_side_package = curr_package
            }
        }
        return other_side_package
    }

    /**
     * Returns the package with index p_package_no. Packages numbers are from 1 to package count.
     */
    fun get(p_package_no: Int): Package? {
        val result = package_arr.elementAt(p_package_no - 1)
        if (result != null && result.no != p_package_no) {
            FRLogger.warn("Padstacks.get: inconsistent padstack number")
        }
        return result
    }

    /**
     * Returns the count of packages in this object.
     */
    fun count(): Int {
        return package_arr.size
    }

    /**
     * Appends a new package with the input data to this object.
     */
    fun add(
        p_name: String,
        p_pin_arr: Array<Package.Pin>,
        p_outline: Array<Shape>?,
        p_keepout_arr: Array<Package.Keepout>,
        p_via_keepout_arr: Array<Package.Keepout>,
        p_place_keepout_arr: Array<Package.Keepout>,
        p_is_front: Boolean
    ): Package {
        val new_package = Package(
            p_name,
            package_arr.size + 1,
            p_pin_arr,
            p_outline,
            p_keepout_arr,
            p_via_keepout_arr,
            p_place_keepout_arr,
            p_is_front,
            this
        )
        package_arr.add(new_package)
        return new_package
    }

    /**
     * Appends a new package with pins p_pin_arr to this object. The package name is generated internally.
     */
    fun add(p_pin_arr: Array<Package.Pin>): Package {
        val package_name = "Package#" + (package_arr.size + 1)
        return add(
            package_name,
            p_pin_arr,
            null,
            emptyArray(),
            emptyArray(),
            emptyArray(),
            p_is_front = true
        )
    }
}
