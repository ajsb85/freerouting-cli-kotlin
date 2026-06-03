package app.freerouting.datastructures

/**
 * Interface to observe changes on objects for synchronisation purposes.
 */
interface Observers<ObjectType> {

    /**
     * Tell the observers the deletion p_object.
     */
    fun notify_deleted(p_object: ObjectType)

    /**
     * Notify the observers, that they can synchronize the changes on p_object.
     */
    fun notify_changed(p_object: ObjectType)

    /**
     * Enable the observers to synchronize the new created item.
     */
    fun notify_new(p_object: ObjectType)

    /**
     * Starts notifying the observers
     */
    fun activate()

    /**
     * Ends notifying the observers
     */
    fun deactivate()

    /**
     * Returns, if the observer is activated.
     */
    fun is_active(): Boolean
}
