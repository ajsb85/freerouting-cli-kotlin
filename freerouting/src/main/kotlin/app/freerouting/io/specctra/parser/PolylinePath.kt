package app.freerouting.io.specctra.parser

import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.geometry.planar.Shape as PlanarShape
import app.freerouting.logger.FRLogger
import java.io.IOException

/**
 * Describes a path defined by a sequence of lines instead of a sequence of corners.
 */
class PolylinePath(p_layer: Layer, p_width: Double, p_corner_arr: DoubleArray) :
    Path(p_layer, p_width, p_corner_arr) {

    /**
     * Writes this path as a scope to an output dsn-file.
     */
    @Throws(IOException::class)
    override fun write_scope(p_file: IndentFileWriter, p_identifier: IdentifierType) {
        p_file.start_scope()
        p_file.write("polyline_path ")
        p_identifier.write(this.layer.name, p_file)
        p_file.write(" ")
        p_file.write(this.width.toString())
        val lineCount = coordinate_arr.size / 4
        for (i in 0..<lineCount) {
            p_file.new_line()
            for (j in 0..3) {
                p_file.write(coordinate_arr[4 * i + j].toString())
                p_file.write(" ")
            }
        }
        p_file.end_scope()
    }

    @Throws(IOException::class)
    override fun write_scope_int(p_file: IndentFileWriter, p_identifier: IdentifierType) {
        p_file.start_scope()
        p_file.write("polyline_path ")
        p_identifier.write(this.layer.name, p_file)
        p_file.write(" ")
        p_file.write(this.width.toString())
        val lineCount = coordinate_arr.size / 4
        for (i in 0..<lineCount) {
            p_file.new_line()
            for (j in 0..3) {
                val currCoor = Math.round(coordinate_arr[4 * i + j]).toInt()
                p_file.write(currCoor.toString())
                p_file.write(" ")
            }
        }
        p_file.end_scope()
    }

    override fun transform_to_board_rel(p_coordinate_transform: CoordinateTransform): PlanarShape? {
        FRLogger.warn("PolylinePath.transform_to_board_rel not implemented")
        return null
    }

    override fun transform_to_board(p_coordinate_transform: CoordinateTransform): PlanarShape? {
        FRLogger.warn("PolylinePath.transform_to_board not implemented")
        return null
    }

    override fun bounding_box(): Rectangle? {
        FRLogger.warn("PolylinePath.bounding_box not implemented")
        return null
    }
}
