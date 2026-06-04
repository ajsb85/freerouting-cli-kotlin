package app.freerouting.io.specctra.parser

import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.geometry.planar.IntPoint
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Class for reading and writing circle scopes from dsn-files.
 */
class Circle : Shape {

    @JvmField val coor: DoubleArray

    /**
     * Creates a new circle from the input parameters. p_coor is an array of dimension 3. p_coor[0] is the radius of the circle, p_coor[1] is the x coordinate of the circle, p_coor[2] is the y
     * coordinate of the circle.
     */
    constructor(p_layer: Layer, p_coor: DoubleArray) : super(p_layer) {
        coor = p_coor
    }

    constructor(p_layer: Layer, p_radius: Double, p_center_x: Double, p_center_y: Double) : super(p_layer) {
        coor = DoubleArray(3)
        coor[0] = p_radius
        coor[1] = p_center_x
        coor[2] = p_center_y
    }

    override fun transform_to_board(p_coordinate_transform: CoordinateTransform): app.freerouting.geometry.planar.Shape? {
        val location = DoubleArray(2)
        location[0] = coor[1]
        location[1] = coor[2]
        val center = p_coordinate_transform.dsn_to_board(location).round()
        val radius = Math.round(p_coordinate_transform.dsn_to_board(coor[0]) / 2).toInt()
        return app.freerouting.geometry.planar.Circle(center, radius)
    }

    override fun transform_to_board_rel(p_coordinate_transform: CoordinateTransform): app.freerouting.geometry.planar.Shape? {
        val newCoor = IntArray(3)
        newCoor[0] = Math.round(p_coordinate_transform.dsn_to_board(coor[0]) / 2).toInt()
        for (i in 1..2) {
            newCoor[i] = p_coordinate_transform.dsn_to_board(coor[i]).roundToInt()
        }
        return app.freerouting.geometry.planar.Circle(IntPoint(newCoor[1], newCoor[2]), newCoor[0])
    }

    override fun bounding_box(): Rectangle? {
        val bounds = DoubleArray(4)
        bounds[0] = coor[1] - coor[0]
        bounds[1] = coor[2] - coor[0]
        bounds[2] = coor[1] + coor[0]
        bounds[3] = coor[2] + coor[0]
        return Rectangle(layer, bounds)
    }

    @Throws(IOException::class)
    override fun write_scope(p_file: IndentFileWriter, p_identifier: IdentifierType) {
        p_file.new_line()
        p_file.write("(circle ")
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
        p_file.write("(circle ")
        p_identifier.write(this.layer.name, p_file)
        for (i in coor.indices) {
            p_file.write(" ")
            val currCoor = Math.round(coor[i]).toInt()
            p_file.write(currCoor.toString())
        }
        p_file.write(")")
    }
}
