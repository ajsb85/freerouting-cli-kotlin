package app.freerouting.io.specctra.parser

import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.geometry.planar.FloatPoint
import app.freerouting.geometry.planar.IntBox
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Describes a rectangle in a Specctra dsn file.
 */
class Rectangle(p_layer: Layer, @JvmField val coor: DoubleArray) : Shape(p_layer) {

    override fun bounding_box(): Rectangle? {
        return this
    }

    /**
     * Creates the smallest rectangle containing this rectangle and p_other
     */
    fun union(p_other: Rectangle): Rectangle {
        val resultCoor = DoubleArray(4)
        resultCoor[0] = min(this.coor[0], p_other.coor[0])
        resultCoor[1] = min(this.coor[1], p_other.coor[1])
        resultCoor[2] = max(this.coor[2], p_other.coor[2])
        resultCoor[3] = max(this.coor[3], p_other.coor[3])
        return Rectangle(this.layer, resultCoor)
    }

    override fun transform_to_board_rel(p_coordinate_transform: CoordinateTransform): app.freerouting.geometry.planar.Shape? {
        val boxCoor = IntArray(4)
        for (i in 0..3) {
            boxCoor[i] = p_coordinate_transform.dsn_to_board(this.coor[i]).roundToInt()
        }

        val result: IntBox
        if (boxCoor[1] <= boxCoor[3]) {
            // boxCoor describe lower left and upper right corner
            result = IntBox(boxCoor[0], boxCoor[1], boxCoor[2], boxCoor[3])
        } else {
            // boxCoor describe upper left and lower right corner
            result = IntBox(boxCoor[0], boxCoor[3], boxCoor[2], boxCoor[1])
        }
        return result
    }

    override fun transform_to_board(p_coordinate_transform: CoordinateTransform): app.freerouting.geometry.planar.Shape? {
        val currPoint = DoubleArray(2)
        currPoint[0] = min(coor[0], coor[2])
        currPoint[1] = min(coor[1], coor[3])
        val lowerLeft = p_coordinate_transform.dsn_to_board(currPoint)
        currPoint[0] = max(coor[0], coor[2])
        currPoint[1] = max(coor[1], coor[3])
        val upperRight = p_coordinate_transform.dsn_to_board(currPoint)
        return IntBox(lowerLeft.round(), upperRight.round())
    }

    /**
     * Writes this rectangle as a scope to an output dsn-file.
     */
    @Throws(IOException::class)
    override fun write_scope(p_file: IndentFileWriter, p_identifier: IdentifierType) {
        p_file.new_line()
        p_file.write("(rect ")
        p_identifier.write(this.layer.name, p_file)
        for (i in coor.indices) {
            p_file.write(" ")
            p_file.write(coor[i].toString())
        }
        p_file.write(")")
    }

    @Throws(IOException::class)
    override fun write_scope_int(p_file: IndentFileWriter, p_identifier: IdentifierType) {
        p_file.new_line()
        p_file.write("(rect ")
        p_identifier.write(this.layer.name, p_file)
        for (i in coor.indices) {
            p_file.write(" ")
            val currCoor = Math.round(coor[i]).toInt()
            p_file.write(currCoor.toString())
        }
        p_file.write(")")
    }
}
