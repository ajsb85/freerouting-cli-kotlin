package app.freerouting.debug

import app.freerouting.Freerouting
import app.freerouting.logger.FRLogger
import java.util.ArrayList
import java.util.Stack
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * Manages the execution flow for debugging purposes.
 * Handles pausing, resuming, stepping, and delays.
 */
class DebugControl private constructor() {

    // Execution state
    private val isPaused = AtomicBoolean(false)
    private val shouldStep = AtomicBoolean(false)

    // Fast Forward / Rewind State
    private val isFastForwarding = AtomicBoolean(false)
    private val stepNetHistory = Stack<Int>()

    // Lock for synchronization
    private val lock = java.lang.Object()

    // Listeners
    private val listeners = ArrayList<DebugStateListener>()

    @Volatile
    private var currentNetNo = -1

    fun addDebugStateListener(listener: DebugStateListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    private fun notifyListeners() {
        val paused = isPaused.get()
        synchronized(listeners) {
            for (listener in listeners) {
                listener.onDebugStateChanged(paused)
            }
        }
    }

    /**
     * Resets the execution state.
     * Starts in PAUSED mode if single stepping is enabled.
     */
    fun reset() {
        if (Freerouting.globalSettings.debugSettings.singleStepExecution) {
            pause()
        } else {
            resume()
        }
    }

    /**
     * Resets the fast forward state and clears history.
     */
    fun resetDebugState() {
        isFastForwarding.set(false)
        currentNetNo = -1
        stepNetHistory.clear()
    }

    /**
     * Sets the Fast Forward mode.
     * Execution will continue until the net number changes.
     */
    fun convertToFastForward() {
        if (isPaused.get()) {
            isFastForwarding.set(true)
            resume()
        }
    }

    /**
     * Checks if we should continue rewinding based on the history.
     *
     * @param targetNetNo The net number we want to rewind back to the beginning of.
     */
    fun shouldContinueRewind(targetNetNo: Int): Boolean {
        if (stepNetHistory.isEmpty()) return false
        return !stepNetHistory.isEmpty() && stepNetHistory.peek() == targetNetNo
    }

    fun popLastStepNet(): Int {
        if (stepNetHistory.isEmpty()) return -1
        return stepNetHistory.pop()
    }

    fun peekLastStepNet(): Int {
        if (stepNetHistory.isEmpty()) return -1
        return stepNetHistory.peek()
    }

    /**
     * Checks if the debug control is interested in the given items based on the
     * filter.
     *
     * @param impactedItems Description of items involved (e.g. "Net #1, Trace...")
     * @return true if the items should be processed/logged, false otherwise.
     */
    fun isInterested(impactedItems: String?): Boolean {
        val netNo = getNetNo(impactedItems)
        return Freerouting.globalSettings.debugSettings.isNetPermitted(netNo, null)
    }

    private fun getNetNo(impactedItems: String?): Int {
        var netNo = -1
        if (impactedItems != null) {
            val matcher = NET_NUMBER_PATTERN.matcher(impactedItems)
            if (matcher.find()) {
                try {
                    netNo = matcher.group(1).toInt()
                } catch (_: NumberFormatException) {
                    // ignore
                }
            }
        }
        return netNo
    }

    /**
     * Called by the logging framework at potential breakpoints.
     * Parses the impactedItems string to extract net numbers for filtering.
     *
     * @param operation The operation name
     * @param impactedItems Description of items involved (e.g. "Net #1, Trace...")
     */
    fun check(operation: String?, impactedItems: String?): Boolean {
        if (Freerouting.globalSettings?.debugSettings == null) {
            return false
        }

        if (!Freerouting.globalSettings.debugSettings.singleStepExecution &&
            Freerouting.globalSettings.debugSettings.traceInsertionDelay == 0
        ) {
            return false
        }

        val netNo = getNetNo(impactedItems)

        if (!Freerouting.globalSettings.debugSettings.isNetPermitted(netNo, null)) {
            return false
        }

        return check(operation, netNo, null)
    }

    /**
     * Called by the engine at potential breakpoints.
     * Handles filtering, delays, and pausing.
     *
     * @param operation The operation being performed (e.g. "insert_trace_segment")
     * @param netNo     The net number currently being processed
     * @param netName   The net name currently being processed (optional, can be null)
     *
     * @return true if the operation should be processed/logged, false otherwise.
     */
    fun check(operation: String?, netNo: Int, netName: String?): Boolean {
        if (!Freerouting.globalSettings.debugSettings.singleStepExecution &&
            Freerouting.globalSettings.debugSettings.traceInsertionDelay == 0
        ) {
            return false
        }

        if (netNo >= 0 && !Freerouting.globalSettings.debugSettings.isNetPermitted(netNo, netName)) {
            return false
        }

        if (operation == null || !isInterestedInOperation(operation)) {
            return false
        }

        // Handle Delay
        if (Freerouting.globalSettings.debugSettings.traceInsertionDelay > 0) {
            try {
                Thread.sleep(Freerouting.globalSettings.debugSettings.traceInsertionDelay.toLong())
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        // Handle Single Stepping
        if (Freerouting.globalSettings.debugSettings.singleStepExecution) {

            // Logic for Fast Forwarding
            if (isFastForwarding.get()) {
                // If the net changed, pause!
                if (currentNetNo != -1 && netNo != -1 && currentNetNo != netNo) {
                    FRLogger.debug("FastForward Stopping: Net changed from $currentNetNo to $netNo")
                    pause() // Explicitly pause execution
                } else {
                    if (netNo != -1) {
                        currentNetNo = netNo
                    }
                    stepNetHistory.push(netNo)
                    return true
                }
            }

            synchronized(lock) {
                // If we were asked to step, we reset the flag now as we are about to "execute" this step
                if (shouldStep.compareAndSet(true, false)) {
                    // We are taking a step
                    currentNetNo = netNo
                    stepNetHistory.push(netNo)
                }

                while (isPaused.get()) {
                    // We are paused.
                    // Check if we have a "step" command pending.
                    if (shouldStep.compareAndSet(true, false)) {
                        // Step command received. Break the wait loop and proceed.
                        // We stay paused for the next time.
                        currentNetNo = netNo
                        stepNetHistory.push(netNo)
                        break
                    }

                    // Check if we switched to fast forward while paused
                    if (isFastForwarding.get()) {
                        currentNetNo = netNo
                        stepNetHistory.push(netNo)
                        break
                    }

                    try {
                        lock.wait()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }

        // Let's indicate that we had an event that we were interested in.
        return true
    }

    private fun isInterestedInOperation(operation: String): Boolean {
        if (operation.isEmpty()) {
            return false
        }

        for (filterOp in Freerouting.globalSettings.debugSettings.operationFilters) {
            if (operation.equals(filterOp, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    /**
     * Pauses the execution.
     */
    fun pause() {
        synchronized(lock) {
            isPaused.set(true)
            isFastForwarding.set(false)
            lock.notifyAll() // Notify to check state (though logic is "wait while paused")
        }
        FRLogger.debug("DebugControl: Execution Paused")
        notifyListeners()
    }

    /**
     * Resumes execution (Play).
     */
    fun resume() {
        synchronized(lock) {
            isPaused.set(false)
            shouldStep.set(false)
            lock.notifyAll() // Wake up waiting threads
        }
        FRLogger.debug("DebugControl: Execution Resumed")
        notifyListeners()
    }

    /**
     * Executes a single step (Next).
     * Must be paused to have effect.
     */
    fun next() {
        synchronized(lock) {
            if (isPaused.get()) {
                shouldStep.set(true)
                lock.notifyAll() // Wake up waiting threads
            }
        }
        FRLogger.debug("DebugControl: Single Step Triggered")
    }

    fun isPaused(): Boolean {
        return isPaused.get()
    }

    fun interface DebugStateListener {
        fun onDebugStateChanged(isPaused: Boolean)
    }

    companion object {
        private val INSTANCE: DebugControl = DebugControl()

        @JvmStatic
        fun getInstance(): DebugControl = INSTANCE

        private val NET_NUMBER_PATTERN = Pattern.compile("Net #(\\d+)")
    }
}
