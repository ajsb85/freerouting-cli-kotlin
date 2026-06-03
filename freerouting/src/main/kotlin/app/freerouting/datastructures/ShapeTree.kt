package app.freerouting.datastructures

import app.freerouting.geometry.planar.RegularTileShape
import app.freerouting.geometry.planar.Shape
import app.freerouting.geometry.planar.ShapeBoundingDirections
import app.freerouting.geometry.planar.TileShape
import app.freerouting.logger.FRLogger
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Abstract binary search tree for shapes in the plane. The shapes are stored in the leafs of the tree.
 * Objects to be stored in the tree must implement the interface ShapeTree.Storable.
 */
abstract class ShapeTree(@JvmField protected val bounding_directions: ShapeBoundingDirections) {

    /**
     * Root node - initially null
     */
    @JvmField
    protected var root: TreeNode? = null

    /**
     * The number of entries stored in the tree
     */
    @JvmField
    protected var leaf_count: Int = 0

    /**
     * Inserts all shapes of p_obj into the tree
     */
    fun insert(p_obj: Storable) {
        val shape_count = p_obj.tree_shape_count(this)
        if (shape_count <= 0) {
            return
        }
        val leaf_arr = arrayOfNulls<Leaf>(shape_count)
        for (i in 0 until shape_count) {
            leaf_arr[i] = insert(p_obj, i)
        }
        p_obj.set_search_tree_entries(leaf_arr, this)
    }

    /**
     * Insert a shape - creates a new node with a bounding shape
     */
    protected fun insert(p_object: Storable, p_index: Int): Leaf? {
        val object_shape = p_object.get_tree_shape(this, p_index) ?: return null

        val bounding_shape = object_shape.bounding_shape(bounding_directions)
        if (bounding_shape == null) {
            FRLogger.warn("ShapeTree.insert: bounding shape of TreeObject is null")
            return null
        }
        // Construct a new KdLeaf and set it up
        val new_leaf = Leaf(p_object, p_index, null, bounding_shape)
        this.insert(new_leaf)
        return new_leaf
    }

    /**
     * Inserts the leaves of this tree into an array.
     */
    fun to_array(): Array<Leaf?> {
        val result = arrayOfNulls<Leaf>(this.leaf_count)
        if (result.isEmpty()) {
            return result
        }
        var curr_node = this.root
        var curr_index = 0
        while (true) {
            // go down from curr_node to the left most leaf
            while (curr_node is InnerNode) {
                curr_node = curr_node.first_child
            }
            result[curr_index] = curr_node as Leaf?

            ++curr_index
            // go up until parent.second_child != curr_node, which means we came from first_child
            var curr_parent = curr_node?.parent
            while (curr_parent != null && curr_parent.second_child == curr_node) {
                curr_node = curr_parent
                curr_parent = curr_node.parent
            }
            if (curr_parent == null) {
                break
            }
            curr_node = curr_parent.second_child
        }
        return result
    }

    abstract fun insert(p_leaf: Leaf)

    abstract fun remove_leaf(p_leaf: Leaf)

    /**
     * removes all entries of p_obj in the tree.
     */
    fun remove(p_entries: Array<Leaf?>?) {
        if (p_entries == null) {
            return
        }
        for (i in p_entries.indices) {
            val entry = p_entries[i]
            if (entry != null) {
                remove_leaf(entry)
            }
        }
    }

    /**
     * Returns the number of entries stored in the tree.
     */
    fun size(): Int {
        return leaf_count
    }

    /**
     * Outputs some statistic information about the tree.
     */
    fun statistics(p_message: String) {
        val leaf_arr = this.to_array()
        var cumulative_depth = 0.0
        var maximum_depth = 0
        for (i in leaf_arr.indices) {
            val leaf = leaf_arr[i]
            if (leaf != null) {
                val distance_to_root = leaf.distance_to_root()
                cumulative_depth += distance_to_root.toDouble()
                maximum_depth = max(maximum_depth, distance_to_root)
            }
        }
        val average_depth = cumulative_depth / leaf_arr.size
        FRLogger.info(
            "MinAreaTree: Entry count: ${leaf_arr.size} log: ${Math.log(leaf_arr.size.toDouble()).roundToLong()} Average depth: ${average_depth.roundToLong()}  Maximum depth: $maximum_depth $p_message"
        )
    }

    /**
     * Interface, which must be implemented by objects to be stored in a ShapeTree.
     */
    interface Storable : Comparable<Any> {
        /**
         * Number of shapes of an object to store in p_shape_tree
         */
        fun tree_shape_count(p_shape_tree: ShapeTree): Int

        /**
         * Get the Shape of this object with index p_index stored in the ShapeTree with index identification number p_tree_id_no
         */
        fun get_tree_shape(p_tree: ShapeTree, p_index: Int): TileShape?

        /**
         * Stores the entries in the ShapeTrees of this object for better performance while for example deleting tree entries. Called only by insert methods of class ShapeTree.
         */
        fun set_search_tree_entries(p_entries: Array<Leaf?>, p_tree: ShapeTree)
    }

    /**
     * Information of a single object stored in a tree
     */
    class TreeEntry(
        @JvmField val `object`: Storable,
        @JvmField val shape_index_in_object: Int
    )

    //////////////////////////////////////////////////////////

    /**
     * Common functionality of inner nodes and leaf nodes.
     */
    open class TreeNode {
        @JvmField
        var bounding_shape: RegularTileShape? = null
        @JvmField
        var parent: InnerNode? = null
    }

    //////////////////////////////////////////////////////////

    /**
     * Description of an inner node of the tree, which implements a fork to its two children.
     */
    class InnerNode(p_bounding_shape: RegularTileShape?, p_parent: InnerNode?) : TreeNode() {
        @JvmField
        var first_child: TreeNode? = null
        @JvmField
        var second_child: TreeNode? = null

        init {
            bounding_shape = p_bounding_shape
            parent = p_parent
        }
    }

    //////////////////////////////////////////////////////////

    /**
     * Description of a leaf of the Tree, where the geometric information is stored.
     */
    class Leaf(
        @JvmField var `object`: Storable?,
        @JvmField var shape_index_in_object: Int,
        p_parent: InnerNode?,
        p_bounding_shape: RegularTileShape?
    ) : TreeNode(), Comparable<Leaf> {

        init {
            bounding_shape = p_bounding_shape
            parent = p_parent
        }

        override fun compareTo(other: Leaf): Int {
            val obj = this.`object` ?: return if (other.`object` == null) 0 else -1
            val otherObj = other.`object` ?: return 1
            var result = obj.compareTo(otherObj)
            if (result == 0) {
                result = shape_index_in_object - other.shape_index_in_object
            }
            return result
        }

        /**
         * Returns the number of nodes between this leaf and the croot of the tree.
         */
        fun distance_to_root(): Int {
            var result = 1
            var curr_parent = this.parent
            while (curr_parent?.parent != null) {
                curr_parent = curr_parent.parent
                ++result
            }
            return result
        }
    }
}
