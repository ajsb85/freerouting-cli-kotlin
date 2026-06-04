package app.freerouting.io.specctra.parser

import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.geometry.planar.FloatPoint
import app.freerouting.geometry.planar.IntOctagon
import app.freerouting.geometry.planar.IntPoint
import app.freerouting.geometry.planar.PolygonShape
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * Class for reading and writing path scopes consisting of a polygon from dsn-files.
 */
class PolygonPath(p_layer: Layer, p_width: Double, p_coordinate_arr: DoubleArray) :
    Path(p_layer, p_width, p_coordinate_arr) {

    /**
     * Writes this path as a scope to an output dsn-file.
     */
    @Throws(IOException::class)
    override fun write_scope(p_file: IndentFileWriter, p_identifier: IdentifierType) {
        p_file.start_scope()
        p_file.write("path ")
        p_identifier.write(this.layer.name, p_file)
        p_file.write(" ")
        p_file.write(this.width.toString())
        val cornerCount = coordinate_arr.size / 2
        for (i in 0..<cornerCount) {
            p_file.new_line()
            p_file.write(coordinate_arr[2 * i].toString())
            p_file.write(" ")
            p_file.write(coordinate_arr[2 * i + 1].toString())
        }
        p_file.end_scope()
    }

    @Throws(IOException::class)
    override fun write_scope_int(p_file: IndentFileWriter, p_identifier: IdentifierType) {
        p_file.start_scope()
        p_file.write("path ")
        p_identifier.write(this.layer.name, p_file)
        p_file.write(" ")
        p_file.write(this.width.toString())
        val cornerCount = coordinate_arr.size / 2
        for (i in 0..<cornerCount) {
            p_file.new_line()
            var currCoor = Math.round(coordinate_arr[2 * i]).toInt()
            p_file.write(currCoor.toString())
            p_file.write(" ")
            currCoor = Math.round(coordinate_arr[2 * i + 1]).toInt()
            p_file.write(currCoor.toString())
        }
        p_file.end_scope()
    }

    override fun transform_to_board(p_coordinate_transform: CoordinateTransform): app.freerouting.geometry.planar.Shape? {
        val cornerArr = Array(this.coordinate_arr.size / 2) { i ->
            val currPoint = DoubleArray(2)
            currPoint[0] = this.coordinate_arr[2 * i]
            currPoint[1] = this.coordinate_arr[2 * i + 1]
            p_coordinate_transform.dsn_to_board(currPoint)
        }
        val offset = p_coordinate_transform.dsn_to_board(this.width) / 2
        if (cornerArr.size <= 2) {
            val boundingOct = FloatPoint.bounding_octagon(cornerArr)
            return boundingOct.enlarge(offset)
        }
        val roundedCornerArr: Array<app.freerouting.geometry.planar.Point> = Array(cornerArr.size) { i ->
            cornerArr[i].round()
        }
        var result: app.freerouting.geometry.planar.Shape = PolygonShape(roundedCornerArr)
        if (offset > 0) {
            result = result.bounding_tile().enlarge(offset)
        }
        return result
    }

    override fun transform_to_board_rel(p_coordinate_transform: CoordinateTransform): app.freerouting.geometry.planar.Shape? {
        val cornerArr = Array(this.coordinate_arr.size / 2) { i ->
            val currPoint = DoubleArray(2)
            currPoint[0] = this.coordinate_arr[2 * i]
            currPoint[1] = this.coordinate_arr[2 * i + 1]
            p_coordinate_transform.dsn_to_board_rel(currPoint)
        }
        val offset = p_coordinate_transform.dsn_to_board(this.width) / 2
        if (cornerArr.size <= 2) {
            val boundingOct = FloatPoint.bounding_octagon(cornerArr)
            return boundingOct.enlarge(offset)
        }
        val roundedCornerArr: Array<app.freerouting.geometry.planar.Point> = Array(cornerArr.size) { i ->
            cornerArr[i].round()
        }
        var result: app.freerouting.geometry.planar.Shape = PolygonShape(roundedCornerArr)
        if (offset > 0) {
            result = result.bounding_tile().enlarge(offset)
        }
        return result
    }

    override fun bounding_box(): Rectangle? {
        val offset = this.width / 2
        val bounds = DoubleArray(4)
        bounds[0] = Int.MAX_VALUE.toDouble()
        bounds[1] = Int.MAX_VALUE.toDouble()
        bounds[2] = Int.MIN_VALUE.toDouble()
        bounds[3] = Int.MIN_VALUE.toDouble()
        for (i in coordinate_arr.indices) {
            if (i % 2 == 0) {
                // x coordinate
                bounds[0] = min(bounds[0], coordinate_arr[i] - offset)
                bounds[2] = max(bounds[2], coordinate_arr[i]) + offset
            } else {
                // y coordinate
                bounds[1] = min(bounds[1], coordinate_arr[i] - offset)
                bounds[3] = max(bounds[3], coordinate_arr[i] + offset)
            }
        }
        return Rectangle(layer, bounds)
    }
}
