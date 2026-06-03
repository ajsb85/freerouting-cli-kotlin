package app.freerouting.datastructures

import app.freerouting.logger.FRLogger
import java.io.Serializable
import java.util.Collection
import java.util.LinkedList
import java.util.Vector
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Database of objects, for which Undo and Redo operations are made possible.
 * The algorithm works only for objects containing no references.
 */
class UndoableObjects : Serializable {

    /**
     * The entries of this map are of type UndoableObjectNode, the keys of type
     * UndoableObjects.Storable.
     */
    private val objects: ConcurrentMap<Storable, UndoableObjectNode> = ConcurrentSkipListMap()

    /**
     * the lists of deleted objects on each undo level, which where already existing
     * before the previous snapshot.
     */
    private val deleted_objects_stack = Vector<MutableCollection<UndoableObjectNode>>()

    /**
     * the current undo level
     */
    private var stack_level = 0
    private var redo_possible = false

    /**
     * Returns an iterator for sequential reading of the object list.
     *
     * @return an iterator for sequential reading of the object list
     */
    fun start_read_object(): MutableIterator<UndoableObjectNode> {
        return objects.values.iterator()
    }

    /**
     * Reads the next object in this list. Returns null, if the list is exhausted.
     * p_it must be created by start_read_object.
     */
    fun read_object(p_it: MutableIterator<UndoableObjectNode>): Storable? {
        while (p_it.hasNext()) {
            val curr_node = p_it.next()
            // skip objects getting alive only by redo
            if (curr_node != null && curr_node.level <= this.stack_level) {
                return curr_node.`object`
            }
        }
        return null
    }

    /**
     * Adds p_object to the UndoableObjectsList.
     */
    fun insert(p_object: Storable) {
        disable_redo()
        val curr_undoable_object = UndoableObjectNode(p_object, stack_level)
        objects[p_object] = curr_undoable_object
    }

    /**
     * Removes p_object from the top level of the UndoableObjectsList. Returns
     * false, if p_object was not found in the list.
     */
    fun delete(p_object: Storable): Boolean {
        disable_redo()
        val curr_delete_list: MutableCollection<UndoableObjectNode>? = if (deleted_objects_stack.isEmpty()) {
            // stack_level 0
            null
        } else {
            deleted_objects_stack.lastElement()
        }
        // search p_object in the list
        val object_node = objects[p_object] ?: return false

        if (p_object is app.freerouting.board.Item) {
            val itemNetNames = p_object.getAllNetNames()
            FRLogger.trace(
                "UndoableObjects.delete", "delete",
                "Deleting item with " + itemNetNames + ": " +
                        "item_type=" + p_object.javaClass.simpleName +
                        ", item=" + p_object,
                itemNetNames,
                null
            )
        }

        if (curr_delete_list != null) {
            if (object_node.level < this.stack_level) {
                // add curr_ob to the current delete list to make Undo possible.
                curr_delete_list.add(object_node)
            } else if (object_node.undo_object != null) {
                // add curr_ob.undo_object to the current delete list to make Undo possible.
                curr_delete_list.add(object_node.undo_object!!)
            }
        }
        objects.remove(p_object)
        return true
    }

    /**
     * Makes the current state of the list restorable by Undo.
     */
    fun generate_snapshot() {
        disable_redo()
        val curr_deleted_objects_list: MutableCollection<UndoableObjectNode> = LinkedList()
        deleted_objects_stack.add(curr_deleted_objects_list)
        ++stack_level
    }

    /**
     * Restores the situation before the last snapshot. Outputs the cancelled and
     * the restored objects (if != null) to enable the calling function to take
     * additional actions needed for these objects.
     * Returns false, if no more undo is possible
     */
    fun undo(
        p_cancelled_objects: MutableCollection<Storable>?,
        p_restored_objects: MutableCollection<Storable>?
    ): Boolean {
        if (stack_level == 0) {
            return false // no more undo possible
        }
        for (curr_node in objects.values) {
            if (curr_node.level == stack_level) {
                if (curr_node.undo_object != null) {
                    // replace the current object by its previous state.
                    curr_node.undo_object!!.redo_object = curr_node
                    objects[curr_node.`object`] = curr_node.undo_object!!
                    p_restored_objects?.add(curr_node.undo_object!!.`object`)
                }
                p_cancelled_objects?.add(curr_node.`object`)
            }
        }
        // restore the deleted objects
        val curr_delete_list = deleted_objects_stack.elementAt(stack_level - 1)
        for (curr_deleted_node in curr_delete_list) {
            this.objects[curr_deleted_node.`object`] = curr_deleted_node
            p_restored_objects?.add(curr_deleted_node.`object`)
        }
        --this.stack_level
        redo_possible = true
        return true
    }

    /**
     * Restores the situation before the last undo. Outputs the cancelled and the
     * restored objects (if != null) to enable the calling function to take
     * additional actions needed for these objects.
     * Returns false, if no more redo is possible.
     */
    fun redo(
        p_cancelled_objects: MutableCollection<Storable>?,
        p_restored_objects: MutableCollection<Storable>?
    ): Boolean {
        if (this.stack_level >= deleted_objects_stack.size) {
            return false // already at the top level
        }
        ++this.stack_level
        for (curr_node in objects.values) {
            if (curr_node.redo_object != null && curr_node.redo_object!!.level == this.stack_level) {
                // Object was created on a lower level and changed on the current level,
                // replace the lower level object by the object on the current layer.
                objects[curr_node.`object`] = curr_node.redo_object!!
                p_cancelled_objects?.add(curr_node.`object`)
                p_restored_objects?.add(curr_node.redo_object!!.`object`)
            } else if (curr_node.level == this.stack_level) {
                // Object was created on the current level, allow it to be restored.
                p_restored_objects?.add(curr_node.`object`)
            }
        }
        // Delete the objects, which were deleted on the current level, again.
        val curr_delete_list = deleted_objects_stack.elementAt(stack_level - 1)
        for (curr_deleted_node in curr_delete_list) {
            var nodeToDeleted = curr_deleted_node
            while (nodeToDeleted.redo_object != null && nodeToDeleted.redo_object!!.level <= this.stack_level) {
                nodeToDeleted = nodeToDeleted.redo_object!!
            }
            if (this.objects.remove(nodeToDeleted.`object`) == null) {
                FRLogger.warn("previous deleted object not found")
            }
            if (p_restored_objects == null || !p_restored_objects.remove(nodeToDeleted.`object`)) {
                // the object needs only be cancelled if it is already in the board
                p_cancelled_objects?.add(nodeToDeleted.`object`)
            }
        }
        return true
    }

    /**
     * Removes the top snapshot from the undo stack, so that its situation cannot be
     * restored anymore. Returns false, if no more snapshot could be popped.
     */
    fun pop_snapshot(): Boolean {
        disable_redo()
        if (stack_level == 0) {
            return false
        }
        for (curr_node in objects.values) {
            if (curr_node.level == stack_level - 1) {
                if (curr_node.redo_object != null && curr_node.redo_object!!.level == stack_level) {
                    curr_node.redo_object!!.undo_object = curr_node.undo_object
                    if (curr_node.undo_object != null) {
                        curr_node.undo_object!!.redo_object = curr_node.redo_object
                    }
                }
            } else if (curr_node.level >= stack_level) {
                --curr_node.level
            }
        }
        val deleted_objects_stack_size = deleted_objects_stack.size
        if (deleted_objects_stack_size >= 2) {
            // join the top delete list with the delete list of the second top level
            val from_delete_list = deleted_objects_stack.elementAt(deleted_objects_stack_size - 1)
            val to_delete_list = deleted_objects_stack.elementAt(deleted_objects_stack_size - 2)
            for (curr_deleted_node in from_delete_list) {
                if (curr_deleted_node.level < this.stack_level - 1) {
                    to_delete_list.add(curr_deleted_node)
                } else if (curr_deleted_node.undo_object != null) {
                    to_delete_list.add(curr_deleted_node.undo_object!!)
                }
            }
        }
        deleted_objects_stack.removeAt(deleted_objects_stack_size - 1)
        --stack_level
        return true
    }

    /**
     * Must be called before p_object will be modified after a snapshot for the
     * first time, if it may have existed before that snapshot.
     */
    fun save_for_undo(p_object: Storable) {
        disable_redo()
        // search p_object in the map
        val curr_node = objects[p_object]
        if (curr_node == null) {
            FRLogger.warn("UndoableObjects.save_for_undo: object node not found")
            return
        }
        if (curr_node.level < this.stack_level) {
            val old_node = UndoableObjectNode(
                p_object.clone() as Storable,
                curr_node.level
            )
            old_node.undo_object = curr_node.undo_object
            old_node.redo_object = curr_node
            curr_node.undo_object = old_node
            curr_node.level = this.stack_level
        }
    }

    /**
     * Must be called, if objects are changed for the first time after undo.
     */
    private fun disable_redo() {
        if (!redo_possible) {
            return
        }
        redo_possible = false
        // shorten the size of the deleted_objects_stack to this.stack_level
        deleted_objects_stack
            .subList(this.stack_level, deleted_objects_stack.size)
            .clear()
        val it = objects.values.iterator()
        while (it.hasNext()) {
            val curr_node = it.next()
            if (curr_node.level > this.stack_level) {
                it.remove()
            } else if (curr_node.level == this.stack_level) {
                curr_node.redo_object = null
            }
        }
    }

    /**
     * Condition for an Object to be stored in an UndoableObjects database. An
     * object of class UndoableObjects.Storable must not contain any references.
     */
    interface Storable : Comparable<Any> {

        /**
         * Creates an exact copy of this object Public overwriting of the protected
         * clone method in java.lang.Object,
         */
        fun clone(): Any
    }

    /**
     * Stores information for correct restoring or cancelling an object in an undo
     * or redo operation. p_level is the level in the Undo stack, where this object
     * was inserted.
     */
    class UndoableObjectNode(
        @JvmField val `object`: Storable,
        @JvmField var level: Int
    ) : Serializable {

        @JvmField var undo_object: UndoableObjectNode? = null
        @JvmField var redo_object: UndoableObjectNode? = null
    }
}
