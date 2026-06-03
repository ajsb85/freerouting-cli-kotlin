package app.freerouting.datastructures

/**
 * Implementation of a stack as an array
 */
@Suppress("UNCHECKED_CAST")
class ArrayStack<P_ELEMENT_TYPE>(p_max_stack_depth: Int) {

    private var level = -1
    private var node_arr: Array<Any?> = arrayOfNulls(p_max_stack_depth)

    /**
     * Sets the stack to empty.
     */
    fun reset() {
        level = -1
    }

    /**
     * Pushed p_element onto the stack.
     */
    fun push(p_element: P_ELEMENT_TYPE) {
        ++level
        if (level >= node_arr.size) {
            reallocate()
        }
        node_arr[level] = p_element
    }

    /**
     * Pops the next element from the top of the stack. Returns null, if the stack is exhausted.
     */
    fun pop(): P_ELEMENT_TYPE? {
        if (level < 0) {
            return null
        }
        val result = node_arr[level] as P_ELEMENT_TYPE
        --level
        return result
    }

    private fun reallocate() {
        val new_arr = arrayOfNulls<Any?>(4 * this.node_arr.size)
        System.arraycopy(node_arr, 0, new_arr, 0, node_arr.size)
        this.node_arr = new_arr
    }
}
