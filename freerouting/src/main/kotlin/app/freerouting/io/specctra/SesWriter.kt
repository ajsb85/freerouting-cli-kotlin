package app.freerouting.io.specctra

import app.freerouting.board.BasicBoard
import app.freerouting.board.ConductionArea
import app.freerouting.board.FixedState
import app.freerouting.board.Item
import app.freerouting.board.Pin
import app.freerouting.board.PolylineTrace
import app.freerouting.board.Via
import app.freerouting.core.Package
import app.freerouting.core.Padstack
import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.io.specctra.parser.CoordinateTransform
import app.freerouting.io.specctra.parser.Layer
import app.freerouting.io.specctra.parser.Parser
import app.freerouting.io.specctra.parser.Resolution
import app.freerouting.io.specctra.parser.Shape
import app.freerouting.geometry.planar.Area
import app.freerouting.geometry.planar.FloatPoint
import app.freerouting.geometry.planar.Point
import app.freerouting.logger.FRLogger
import app.freerouting.rules.Net

import java.io.IOException
import java.io.OutputStream
import java.util.Arrays
import kotlin.math.roundToLong
import kotlin.math.roundToInt

/**
 * Writes a Specctra session (.ses) file from a [BasicBoard].
 *
 * <p>This class has no dependency on {@code BoardManager}, {@code RoutingJob}, or any GUI class.
 * It operates purely on the board's data model.
 *
 * <p>Replaces the write path previously found in
 * {@link app.freerouting.io.specctra.parser.SpecctraSesFileWriter}
 * (now {@link Deprecated}).
 */
class SesWriter private constructor() {

    companion object {
        /**
         * Serialises the routing result from [board] to Specctra SES format on the given stream.
         *
         * <p>The stream is *flushed* after writing but is **not closed** — the
         * caller retains ownership of the stream lifecycle.
         *
         * @param board      the board whose routing data is serialised (must not be `null`)
         * @param out        target stream (caller owns lifecycle; must not be `null`)
         * @param designName the PCB name written into the `(session ...)` scope header
         * @throws IOException if an I/O error occurs during writing
         */
        @JvmStatic
        @Throws(IOException::class)
        fun write(board: BasicBoard?, out: OutputStream?, designName: String) {
            if (board == null) {
                throw IOException("SesWriter: board must not be null")
            }
            if (out == null) {
                throw IOException("SesWriter: output stream must not be null")
            }
            val outputFile = IndentFileWriter(out)
            val sessionName = designName.replace(".dsn", ".ses")
            val reservedChars = arrayOf("(", ")", " ", ";", "-", "_", "/", "~", "{", "}")
            val identifierType = IdentifierType(
                reservedChars,
                board.communication.specctra_parser_info.string_quote
            )
            writeSessionScope(board, identifierType, outputFile, sessionName, designName)
            outputFile.flush()
        }

        @Throws(IOException::class)
        private fun writeSessionScope(
            board: BasicBoard,
            identifierType: IdentifierType,
            file: IndentFileWriter,
            sessionName: String,
            designName: String
        ) {
            val scaleFactor = board.communication.coordinate_transform.dsn_to_board(1.0) /
                    board.communication.resolution
            val coordinateTransform = CoordinateTransform(scaleFactor, 0.0, 0.0)
            file.start_scope(false)
            file.write("session ")
            identifierType.write(sessionName, file)
            file.new_line()
            file.write("(base_design ")
            identifierType.write(designName, file)
            file.write(")")
            writePlacement(board, identifierType, coordinateTransform, file)
            writeWasIs(board, identifierType, file)
            writeRoutes(board, identifierType, coordinateTransform, file)
            file.end_scope()
        }

        @Throws(IOException::class)
        private fun writePlacement(
            board: BasicBoard,
            identifierType: IdentifierType,
            coordinateTransform: CoordinateTransform,
            file: IndentFileWriter
        ) {
            file.start_scope()
            file.write("placement")
            Resolution.write_scope(file, board.communication)

            val packages = board.library.packages
            if (packages != null) {
                for (i in 1..packages.count()) {
                    val pkg = packages.get(i)
                    if (pkg != null) {
                        writeComponents(
                            board,
                            identifierType,
                            coordinateTransform,
                            file,
                            pkg
                        )
                    }
                }
            }

            file.end_scope()
        }

        /** Writes all components with the given package to the session file. */
        @Throws(IOException::class)
        private fun writeComponents(
            board: BasicBoard,
            identifierType: IdentifierType,
            coordinateTransform: CoordinateTransform,
            file: IndentFileWriter,
            pkg: Package
        ) {
            val boardItems = board.get_items()
            var componentFound = false
            for (i in 1..board.components.count()) {
                val currComponent = board.components.get(i)
                if (currComponent.get_package() == pkg) {
                    // check that not all items of the component are deleted
                    var undeletedItemFound = false
                    for (currItem in boardItems) {
                        if (currItem.get_component_no() == currComponent.no) {
                            undeletedItemFound = true
                            break
                        }
                    }
                    if (undeletedItemFound) {
                        if (!componentFound) {
                            file.start_scope()
                            file.write("component ")
                            identifierType.write(pkg.name, file)
                            componentFound = true
                        }
                        writeComponent(board, identifierType, coordinateTransform, file, currComponent)
                    }
                }
            }
            if (componentFound) {
                file.end_scope()
            }
        }

        @Throws(IOException::class)
        private fun writeComponent(
            board: BasicBoard,
            identifierType: IdentifierType,
            coordinateTransform: CoordinateTransform,
            file: IndentFileWriter,
            component: app.freerouting.board.Component
        ) {
            file.new_line()
            file.write("(place ")
            identifierType.write(component.name, file)
            val location = coordinateTransform.board_to_dsn(component.get_location().to_float())
            val xCoor = location[0].roundToInt()
            val yCoor = location[1].roundToInt()
            file.write(" ")
            file.write(xCoor.toString())
            file.write(" ")
            file.write(yCoor.toString())
            if (component.placed_on_front()) {
                file.write(" front ")
            } else {
                file.write(" back ")
            }
            val rotation = component.get_rotation_in_degree().roundToInt()
            file.write(rotation.toString())
            if (component.position_fixed) {
                file.new_line()
                file.write(" (lock_type position)")
            }
            file.write(")")
        }

        @Throws(IOException::class)
        private fun writeWasIs(
            board: BasicBoard,
            identifierType: IdentifierType,
            file: IndentFileWriter
        ) {
            file.start_scope()
            file.write("was_is")
            val boardPins = board.get_pins()
            for (currPin in boardPins) {
                val swappedWith = currPin.get_changed_to()
                if (currPin.get_changed_to() != currPin) {
                    file.new_line()
                    file.write("(pins ")
                    val currCmp = board.components.get(currPin.get_component_no())
                    if (currCmp != null) {
                        identifierType.write(currCmp.name, file)
                        file.write("-")
                        val packagePin = currCmp.get_package().get_pin(currPin.get_index_in_package())
                        identifierType.write(packagePin?.name ?: "", file)
                    } else {
                        FRLogger.warn("SesWriter.writeWasIs: component not found")
                    }
                    file.write(" ")
                    val swapCmp = board.components.get(swappedWith.get_component_no())
                    if (swapCmp != null) {
                        identifierType.write(swapCmp.name, file)
                        file.write("-")
                        val packagePin = swapCmp.get_package().get_pin(swappedWith.get_index_in_package())
                        identifierType.write(packagePin?.name ?: "", file)
                    } else {
                        FRLogger.warn("SesWriter.writeWasIs: component not found")
                    }
                    file.write(")")
                }
            }
            file.end_scope()
        }

        @Throws(IOException::class)
        private fun writeRoutes(
            board: BasicBoard,
            identifierType: IdentifierType,
            coordinateTransform: CoordinateTransform,
            file: IndentFileWriter
        ) {
            file.start_scope()
            file.write("routes ")
            Resolution.write_scope(file, board.communication)
            Parser.write_scope(file, board.communication.specctra_parser_info, identifierType, true)
            writeLibrary(board, identifierType, coordinateTransform, file)
            writeNetwork(board, identifierType, coordinateTransform, file)
            file.end_scope()
        }

        @Throws(IOException::class)
        private fun writeLibrary(
            board: BasicBoard,
            identifierType: IdentifierType,
            coordinateTransform: CoordinateTransform,
            file: IndentFileWriter
        ) {
            file.start_scope()
            file.write("library_out ")
            for (i in 0 until board.library.via_padstack_count()) {
                writePadstack(
                    board.library.get_via_padstack(i),
                    board,
                    identifierType,
                    coordinateTransform,
                    file
                )
            }
            file.end_scope()
        }

        @Throws(IOException::class)
        private fun writePadstack(
            padstack: Padstack?,
            board: BasicBoard,
            identifierType: IdentifierType,
            coordinateTransform: CoordinateTransform,
            file: IndentFileWriter
        ) {
            if (padstack == null) return
            // determine the layer range covered by this padstack
            var firstLayerNo = 0
            while (firstLayerNo < board.get_layer_count() && padstack.get_shape(firstLayerNo) == null) {
                ++firstLayerNo
            }
            var lastLayerNo = board.get_layer_count() - 1
            while (lastLayerNo >= 0 && padstack.get_shape(lastLayerNo) == null) {
                --lastLayerNo
            }
            if (firstLayerNo >= board.get_layer_count() || lastLayerNo < 0) {
                FRLogger.warn("SesWriter.writePadstack: padstack shape not found")
                return
            }

            file.start_scope()
            file.write("padstack ")
            identifierType.write(padstack.name, file)
            for (i in firstLayerNo..lastLayerNo) {
                val currBoardShape = padstack.get_shape(i) ?: continue
                val boardLayer = board.layer_structure.arr[i]
                val currLayer = Layer(boardLayer.name, i, boardLayer.is_signal)
                val currShape = coordinateTransform.board_to_dsn_rel(currBoardShape, currLayer) ?: continue
                file.start_scope()
                file.write("shape")
                currShape.write_scope_int(file, identifierType)
                file.end_scope()
            }
            if (!padstack.attach_allowed) {
                file.new_line()
                file.write("(attach off)")
            }
            file.end_scope()
        }

        @Throws(IOException::class)
        private fun writeNetwork(
            board: BasicBoard,
            identifierType: IdentifierType,
            coordinateTransform: CoordinateTransform,
            file: IndentFileWriter
        ) {
            file.start_scope()
            file.write("network_out ")
            for (i in 1..board.rules.nets.max_net_no()) {
                writeNet(i, board, identifierType, coordinateTransform, file)
            }
            file.end_scope()
        }

        @Throws(IOException::class)
        private fun writeNet(
            netNo: Int,
            board: BasicBoard,
            identifierType: IdentifierType,
            coordinateTransform: CoordinateTransform,
            file: IndentFileWriter
        ) {
            val netItems = board.get_connectable_items(netNo)
            var headerWritten = false
            for (currItem in netItems) {
                if (currItem.get_fixed_state() == FixedState.SYSTEM_FIXED) {
                    continue
                }
                val isWire = currItem is PolylineTrace
                val isVia = currItem is Via
                val isConductionArea = currItem is ConductionArea &&
                        board.layer_structure.arr[currItem.first_layer()].is_signal
                if (!headerWritten && (isWire || isVia || isConductionArea)) {
                    file.start_scope()
                    file.write("net ")
                    val currNet = board.rules.nets.get(netNo)
                    if (currNet == null) {
                        FRLogger.warn("SesWriter.writeNet: net not found")
                    } else {
                        identifierType.write(currNet.name, file)
                    }
                    headerWritten = true
                }
                if (isWire) {
                    writeWire(currItem as PolylineTrace, board, identifierType, coordinateTransform, file)
                } else if (isVia) {
                    writeVia(currItem as Via, board, identifierType, coordinateTransform, file)
                } else if (isConductionArea) {
                    writeConductionArea(
                        currItem as ConductionArea,
                        board,
                        identifierType,
                        coordinateTransform,
                        file
                    )
                }
            }
            if (headerWritten) {
                file.end_scope()
            }
        }

        @Throws(IOException::class)
        private fun writeWire(
            wire: PolylineTrace,
            board: BasicBoard,
            identifierType: IdentifierType,
            coordinateTransform: CoordinateTransform,
            file: IndentFileWriter
        ) {
            val layerNo = wire.get_layer()
            val boardLayer = board.layer_structure.arr[layerNo]
            val wireWidth = coordinateTransform.board_to_dsn(2.0 * wire.get_half_width()).roundToInt()
            file.start_scope()
            file.write("wire")
            val cornerArr = wire.polyline().corner_arr()
            var coors = IntArray(2 * cornerArr.size)
            var cornerIndex = 0
            var prevCoors: IntArray? = null
            for (i in cornerArr.indices) {
                val currFloatCoors = coordinateTransform.board_to_dsn(cornerArr[i].to_float())
                val currCoors = intArrayOf(
                    currFloatCoors[0].roundToInt(),
                    currFloatCoors[1].roundToInt()
                )
                if (i == 0 || currCoors[0] != prevCoors!![0] || currCoors[1] != prevCoors[1]) {
                    coors[cornerIndex] = currCoors[0]
                    ++cornerIndex
                    coors[cornerIndex] = currCoors[1]
                    ++cornerIndex
                    prevCoors = currCoors
                }
            }
            if (cornerIndex < coors.size) {
                coors = coors.copyOf(cornerIndex)
            }
            writePath(boardLayer.name, wireWidth, coors, identifierType, file)
            writeFixedState(file, wire.get_fixed_state())
            file.end_scope()
        }

        @Throws(IOException::class)
        private fun writeVia(
            via: Via,
            board: BasicBoard,
            identifierType: IdentifierType,
            coordinateTransform: CoordinateTransform,
            file: IndentFileWriter
        ) {
            val viaPadstack = via.get_padstack()
            val viaLocation = via.get_center().to_float()
            file.start_scope()
            file.write("via ")
            identifierType.write(viaPadstack.name, file)
            file.write(" ")
            val location = coordinateTransform.board_to_dsn(viaLocation)
            val xCoor = location[0].roundToInt()
            file.write(xCoor.toString())
            file.write(" ")
            val yCoor = location[1].roundToInt()
            file.write(yCoor.toString())
            writeFixedState(file, via.get_fixed_state())
            file.end_scope()
        }

        @Throws(IOException::class)
        private fun writeFixedState(file: IndentFileWriter, fixedState: FixedState) {
            if (fixedState.ordinal <= FixedState.SHOVE_FIXED.ordinal) {
                return
            }
            file.new_line()
            file.write("(type ")
            if (fixedState == FixedState.SYSTEM_FIXED) {
                file.write("fix)")
            } else {
                file.write("protect)")
            }
        }

        @Throws(IOException::class)
        private fun writePath(
            layerName: String,
            width: Int,
            coors: IntArray,
            identifierType: IdentifierType,
            file: IndentFileWriter
        ) {
            file.start_scope()
            file.write("path ")
            identifierType.write(layerName, file)
            file.write(" ")
            file.write(width.toString())
            val cornerCount = coors.size / 2
            for (i in 0 until cornerCount) {
                file.new_line()
                file.write(coors[2 * i].toString())
                file.write(" ")
                file.write(coors[2 * i + 1].toString())
            }
            file.end_scope()
        }

        @Throws(IOException::class)
        private fun writeConductionArea(
            conductionArea: ConductionArea,
            board: BasicBoard,
            identifierType: IdentifierType,
            coordinateTransform: CoordinateTransform,
            file: IndentFileWriter
        ) {
            val netCount = conductionArea.net_count()
            if (netCount != 1) {
                FRLogger.warn("SesWriter.writeConductionArea: unexpected net count")
                return
            }
            val currArea = conductionArea.get_area()
            val layerNo = conductionArea.get_layer()
            val boardLayer = board.layer_structure.arr[layerNo]
            val conductionLayer = Layer(boardLayer.name, layerNo, boardLayer.is_signal)
            val boundaryShape: app.freerouting.geometry.planar.Shape
            val holes: Array<out app.freerouting.geometry.planar.Shape>
            if (currArea is app.freerouting.geometry.planar.Shape) {
                boundaryShape = currArea
                holes = emptyArray()
            } else {
                boundaryShape = currArea.get_border()
                holes = currArea.get_holes()
            }
            file.start_scope()
            file.write("wire ")
            val dsnShape = coordinateTransform.board_to_dsn(boundaryShape, conductionLayer)
            dsnShape?.write_scope_int(file, identifierType)
            for (i in holes.indices) {
                val dsnHole = coordinateTransform.board_to_dsn(holes[i], conductionLayer)
                dsnHole?.write_hole_scope(file, identifierType)
            }
            file.end_scope()
        }
    }
}
