package app.freerouting.datastructures

import app.freerouting.geometry.planar.RegularTileShape
import app.freerouting.geometry.planar.ShapeBoundingDirections
import app.freerouting.logger.FRLogger
import java.util.TreeSet

/**
 * Binary search tree for shapes in the plane. The shapes are stored in the leaves of the tree. The algorithm for storing a new shape is as following. Starting from the root go to the child, so that
 * the increase of the bounding shape of that child is minimal after adding the new shape, until you reach a leaf. The use of ShapeDirections to calculate the bounding shape is for historical reasons
 * (coming from a Kd-Tree). Instead, any algorithm to calculate a bounding shape of two input shapes can be used. The algorithm would of course also work for higher dimensions.
 */
open class MinAreaTree(p_directions: ShapeBoundingDirections) : ShapeTree(p_directions) {

    @JvmField
    protected val node_stack = ArrayStack<TreeNode>(10000)

    /**
     * Calculates the objects in this tree, which overlap with p_shape
     */
    fun overlaps(p_shape: RegularTileShape): MutableSet<Leaf> {
        val found_overlaps = TreeSet<Leaf>()
        val rootNode = this.root ?: return found_overlaps
        this.node_stack.reset()
        this.node_stack.push(rootNode)
        var curr_node: TreeNode?
        while (true) {
            curr_node = this.node_stack.pop()
            if (curr_node == null) {
                break
            }
            if (curr_node.bounding_shape?.intersects(p_shape) == true) {
                if (curr_node is Leaf) {
                    found_overlaps.add(curr_node)
                } else {
                    val first = (curr_node as InnerNode).first_child
                    val second = curr_node.second_child
                    if (first != null) {
                        this.node_stack.push(first)
                    }
                    if (second != null) {
                        this.node_stack.push(second)
                    }
                }
            }
        }
        return found_overlaps
    }

    override fun insert(p_leaf: Leaf) {
        ++this.leaf_count

        // Tree is empty - just insert the new leaf
        val rootNode = root
        if (rootNode == null) {
            root = p_leaf
            return
        }

        // Non-empty tree - do a recursive location for leaf replacement
        val leaf_to_replace = position_locate(rootNode, p_leaf)

        // Construct a new node - whenever a leaf is added so is a new node
        val new_bounds = p_leaf.bounding_shape!!.union(leaf_to_replace.bounding_shape!!)
        val curr_parent = leaf_to_replace.parent
        val new_node = InnerNode(new_bounds, curr_parent)

        if (curr_parent != null) {
            // Replace the pointer from the parent to the leaf with our new node
            if (leaf_to_replace === curr_parent.first_child) {
                curr_parent.first_child = new_node
            } else {
                curr_parent.second_child = new_node
            }
        }
        // Update the parent pointers of the old leaf and new leaf to point to new node
        leaf_to_replace.parent = new_node
        p_leaf.parent = new_node

        // Insert the children in any order.
        new_node.first_child = leaf_to_replace
        new_node.second_child = p_leaf

        if (root === leaf_to_replace) {
            root = new_node
        }
    }

    private fun position_locate(p_curr_node: TreeNode, p_leaf_to_insert: Leaf): Leaf {
        var curr_node = p_curr_node

        while (curr_node !is Leaf) {
            val curr_inner_node = curr_node as InnerNode
            curr_inner_node.bounding_shape = p_leaf_to_insert.bounding_shape!!.union(curr_inner_node.bounding_shape!!)

            // Choose the child, so that the area increase of that child after taking the union
            // with the shape of p_leaf_to_insert is minimal.

            val first_child_shape = curr_inner_node.first_child!!.bounding_shape!!
            val union_with_first_child_shape = p_leaf_to_insert.bounding_shape!!.union(first_child_shape)
            val first_area_increase = union_with_first_child_shape.area() - first_child_shape.area()

            val second_child_shape = curr_inner_node.second_child!!.bounding_shape!!
            val union_with_second_child_shape = p_leaf_to_insert.bounding_shape!!.union(second_child_shape)
            val second_area_increase = union_with_second_child_shape.area() - second_child_shape.area()

            if (first_area_increase <= second_area_increase) {
                curr_node = curr_inner_node.first_child!!
            } else {
                curr_node = curr_inner_node.second_child!!
            }
        }
        return curr_node
    }

    /**
     * removes an entry from this tree
     */
    override fun remove_leaf(p_leaf: Leaf) {
        // remove the leaf node
        val parent = p_leaf.parent
        p_leaf.bounding_shape = null
        p_leaf.parent = null
        p_leaf.`object` = null
        --this.leaf_count
        if (parent == null) {
            // tree gets empty
            root = null
            return
        }
        // find the other leaf of the parent
        var other_leaf: TreeNode?
        if (parent.second_child === p_leaf) {
            other_leaf = parent.first_child
        } else if (parent.first_child === p_leaf) {
            other_leaf = parent.second_child
        } else {
            FRLogger.warn("MinAreaTree.remove_leaf: parent inconsistent")
            other_leaf = null
        }
        // link the other leaf to the grand_parent and remove the parent node
        val grand_parent = parent.parent
        if (other_leaf != null) {
            other_leaf.parent = grand_parent
        }
        if (grand_parent == null) {
            // only one leaf left in the tree
            root = other_leaf
        } else {
            if (grand_parent.second_child === parent) {
                grand_parent.second_child = other_leaf
            } else if (grand_parent.first_child === parent) {
                grand_parent.first_child = other_leaf
            } else {
                FRLogger.warn("MinAreaTree.remove_leaf: grand_parent inconsistent")
            }
        }
        parent.parent = null
        parent.first_child = null
        parent.second_child = null
        parent.bounding_shape = null

        // recalculate the bounding shapes of the ancestors
        // as long as it gets smaller after removing p_leaf
        var node_to_recalculate = grand_parent
        while (node_to_recalculate != null) {
            val new_bounds = node_to_recalculate.second_child!!.bounding_shape!!.union(node_to_recalculate.first_child!!.bounding_shape!!)
            if (new_bounds.contains(node_to_recalculate.bounding_shape!!)) {
                // the new bounds are not smaller, no further recalculate necessary
                break
            }
            node_to_recalculate.bounding_shape = new_bounds
            node_to_recalculate = node_to_recalculate.parent
        }
    }
}
