package app.freerouting.io.specctra.parser

import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.geometry.planar.IntPoint
import app.freerouting.geometry.planar.PolygonShape
import app.freerouting.geometry.planar.Simplex
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Describes a polygon in a Specctra dsn file.
 */
class Polygon(p_layer: Layer, @JvmField val coor: DoubleArray) : Shape(p_layer) {

    override fun transform_to_board(p_coordinate_transform: CoordinateTransform): app.freerouting.geometry.planar.Shape? {
        val cornerArr: Array<app.freerouting.geometry.planar.Point> = Array(coor.size / 2) { i ->
            val currPoint = DoubleArray(2)
            currPoint[0] = coor[2 * i]
            currPoint[1] = coor[2 * i + 1]
            p_coordinate_transform.dsn_to_board(currPoint).round()
        }
        return PolygonShape(cornerArr)
    }

    override fun transform_to_board_rel(p_coordinate_transform: CoordinateTransform): app.freerouting.geometry.planar.Shape? {
        if (coor.size < 2) {
            return Simplex.EMPTY
        }
        val cornerArr: Array<app.freerouting.geometry.planar.Point> = Array(coor.size / 2) { i ->
            val currX = p_coordinate_transform.dsn_to_board(coor[2 * i]).roundToInt()
            val currY = p_coordinate_transform.dsn_to_board(coor[2 * i + 1]).roundToInt()
            IntPoint(currX, currY)
        }
        return PolygonShape(cornerArr)
    }

    override fun bounding_box(): Rectangle? {
        val bounds = DoubleArray(4)
        bounds[0] = Int.MAX_VALUE.toDouble()
        bounds[1] = Int.MAX_VALUE.toDouble()
        bounds[2] = Int.MIN_VALUE.toDouble()
        bounds[3] = Int.MIN_VALUE.toDouble()
        for (i in coor.indices) {
            if (i % 2 == 0) {
                // x coordinate
                bounds[0] = min(bounds[0], coor[i])
                bounds[2] = max(bounds[2], coor[i])
            } else {
                // y coordinate
                bounds[1] = min(bounds[1], coor[i])
                bounds[3] = max(bounds[3], coor[i])
            }
        }
        return Rectangle(layer, bounds)
    }

    /**
     * Writes this polygon as a scope to an output dsn-file.
     */
    @Throws(IOException::class)
    override fun write_scope(p_file: IndentFileWriter, p_identifier: IdentifierType) {
        p_file.start_scope()
        p_file.write("polygon ")
        p_identifier.write(this.layer.name, p_file)
        p_file.write(" ")
        p_file.write("0")
        val cornerCount = coor.size / 2
        for (i in 0..<cornerCount) {
            p_file.new_line()
            p_file.write(coor[2 * i].toString())
            p_file.write(" ")
            p_file.write(coor[2 * i + 1].toString())
        }
        p_file.end_scope()
    }

    @Throws(IOException::class)
    override fun write_scope_int(p_file: IndentFileWriter, p_identifier: IdentifierType) {
        p_file.start_scope()
        p_file.write("polygon ")
        p_identifier.write(this.layer.name, p_file)
        p_file.write(" ")
        p_file.write("0")
        val cornerCount = coor.size / 2
        for (i in 0..<cornerCount) {
            p_file.new_line()
            var currCoor = Math.round(coor[2 * i]).toInt()
            p_file.write(currCoor.toString())
            p_file.write(" ")
            currCoor = Math.round(coor[2 * i + 1]).toInt()
            p_file.write(currCoor.toString())
        }
        p_file.end_scope()
    }
}
