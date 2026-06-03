package app.freerouting.management

import app.freerouting.Freerouting
import app.freerouting.autoroute.BatchAutorouter
import app.freerouting.autoroute.BatchOptimizer
import app.freerouting.core.BoardFileDetails
import app.freerouting.core.RoutingJob
import app.freerouting.core.RoutingJobState
import app.freerouting.core.RoutingStage
import app.freerouting.core.StoppableThread
import app.freerouting.core.FileFormat
import app.freerouting.interactive.HeadlessBoardManager
import app.freerouting.logger.FRLogger
import app.freerouting.settings.RouterSettings
import com.sun.management.ThreadMXBean
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.time.Instant

/**
 * Used for running an action in a separate thread, that can be stopped by the
 * user. This typically represents an action that is triggered by job scheduler
 */
class RoutingJobSchedulerActionThread(private val job: RoutingJob) : StoppableThread() {

    private val MAX_TIMEOUT = 24 * 60 * 60L // 24 hours
    private val GRACE_PERIOD = 30 // 30 seconds

    override fun thread_action() {
        val startedAt = Instant.now()
        job.startedAt = startedAt
        // Use ISO standard time format
        job.logInfo("Job '${job.shortName}' started at ${startedAt}.")

        // check if we need to check for timeout
        val timeout = TextManager.parseTimespanString(job.routerSettings.jobTimeoutString ?: "")
        if (timeout != null) {
            var finalTimeout = timeout
            // maximize the timeout to 24 hours
            if (finalTimeout > MAX_TIMEOUT) {
                finalTimeout = MAX_TIMEOUT
            }

            job.timeoutAt = startedAt.plusSeconds(finalTimeout)
        }

        // Start a new thread that will monitor the job thread
        Thread {
            while (job != null && job.thread != null) {
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                if (job.state == RoutingJobState.RUNNING || job.state == RoutingJobState.STOPPING) {
                    // Get the CPU time and memory usage of the job thread
                    monitorCpuAndMemoryUsage(job)

                    // Check for timeout
                    val timeoutAt = job.timeoutAt
                    if (timeoutAt != null && !Instant.now().isBefore(timeoutAt)) {
                        // signal the job thread to stop, and wait gracefully for up to 30 seconds for it
                        val thread = job.thread
                        if (thread != null) {
                            thread.requestStop()
                            while (job.state == RoutingJobState.RUNNING && Instant.now()
                                    .isBefore(timeoutAt.plusSeconds(GRACE_PERIOD.toLong()))
                            ) {
                                try {
                                    Thread.sleep(1000)
                                } catch (e: InterruptedException) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        job.state = RoutingJobState.TIMED_OUT
                    }
                }
            }
        }.start()

        // start the routing task if needed
        if (job.routerSettings.getRunRouter()) {
            job.stage = RoutingStage.ROUTING

            val algorithm = job.routerSettings.algorithm

            if (RouterSettings.ALGORITHM_CURRENT != algorithm) {
                job.logInfo("Unknown router algorithm '$algorithm', using default (freerouting-router)")
            }
            // Always use standard BatchAutorouter
            val router = BatchAutorouter(job)

            router.addBoardUpdatedEventListener { _ ->
                setJobOutputToSpecctraSes(job)
            }

            // Call runBatchLoop
            router.runBatchLoop()

            // Log session summary
            val sessionStartTime = router.sessionStartTime
            val initialUnroutedCount = router.initialUnroutedCount

            if (sessionStartTime != null) {
                val sessionEndTime = Instant.now()
                val totalSeconds = java.time.Duration.between(sessionStartTime, sessionEndTime).seconds
                val totalTime = totalSeconds + (java.time.Duration.between(sessionStartTime, sessionEndTime).nano / 1000000000.0)

                val board = job.board
                val finalStats = board?.get_statistics()

                var completionStatus = "completed:"
                // Check for timeout explicitly because job.state might not be updated to
                // TIMED_OUT yet due to race conditions
                val isTimedOut = job.state == RoutingJobState.TIMED_OUT ||
                        (job.timeoutAt != null && !Instant.now().isBefore(job.timeoutAt) && this.isStopRequested)

                if (isTimedOut) {
                    completionStatus = "completed with timeout:"
                } else if (this.isStopRequested) {
                    completionStatus = "interrupted:"
                    if (job.isCancelledByUser()) {
                        completionStatus = "cancelled:"
                    }
                }

                if (finalStats != null) {
                    val sessionSummary = String.format(
                        "Auto-router session %s started with %d unrouted nets, completed in %s, final score: %s, using %s total CPU seconds, %s GB total allocated, and %s MB peak heap usage.",
                        completionStatus,
                        initialUnroutedCount,
                        FRLogger.formatDuration(totalTime),
                        FRLogger.formatScore(
                            finalStats.getNormalizedScore(job.routerSettings.scoring),
                            finalStats.connections.incompleteCount, finalStats.clearanceViolations.totalCount
                        ),
                        FRLogger.defaultFloatFormat.format(job.resourceUsage.cpuTimeUsed),
                        FRLogger.defaultFloatFormat.format(job.resourceUsage.maxMemoryUsed / 1024.0f),
                        FRLogger.defaultFloatFormat.format(job.resourceUsage.peakMemoryUsed)
                    )

                    job.logInfo(sessionSummary)
                }
            }

            job.stage = RoutingStage.IDLE
        } else if (job.routerSettings.isFanoutEnabled()) {
            // Headless fanout-only mode: run the fanout pre-pass and skip autorouter passes.
            job.stage = RoutingStage.ROUTING
            val originalMaxPasses = job.routerSettings.maxPasses
            try {
                job.routerSettings.maxPasses = 0
                val batchRouter = BatchAutorouter(job)
                batchRouter.runBatchLoop()
                setJobOutputToSpecctraSes(job)
            } finally {
                job.routerSettings.maxPasses = originalMaxPasses
            }
            job.stage = RoutingStage.IDLE
        }

        if (job.routerSettings.getRunOptimizer()) {
            job.stage = RoutingStage.OPTIMIZATION
            // start the optimizer task
            val optimizer = BatchOptimizer(job)
            optimizer.addBoardUpdatedEventListener { _ ->
                setJobOutputToSpecctraSes(job)
            }
            optimizer.runBatchLoop()
            job.stage = RoutingStage.IDLE
        }

        setJobOutputToSpecctraSes(job)

        job.finishedAt = Instant.now()
        if (job.state == RoutingJobState.RUNNING) {
            job.state = RoutingJobState.COMPLETED
            Freerouting.globalSettings.statistics.incrementJobsCompleted()
        } else if (job.state == RoutingJobState.STOPPING) {
            if (job.isCancelledByUser()) {
                job.state = RoutingJobState.CANCELLED
            } else {
                job.state = RoutingJobState.COMPLETED
            }
        }
    }

    private fun monitorCpuAndMemoryUsage(job: RoutingJob) {
        try {
            // Get the ThreadMXBean instance and cast it to com.sun.management.ThreadMXBean
            val threadMXBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

            // Get all live thread IDs
            val threadIds = threadMXBean.allThreadIds

            // Iterate through the thread IDs and get memory usage
            for (threadId in threadIds) {
                val thread = job.thread
                if (thread != null && threadId == thread.threadId()) {
                    // CPU time and memory usage
                    val cpuTime = threadMXBean.getThreadCpuTime(threadId) / 1000.0f / 1000.0f / 1000.0f

                    // Enable thread memory allocation measurement
                    threadMXBean.isThreadAllocatedMemoryEnabled = true

                    // Get the thread's allocated memory in bytes
                    val allocatedMemory = threadMXBean.getThreadAllocatedBytes(threadId)
                    val allocatedMB = allocatedMemory / (1024.0f * 1024.0f)

                    // Update the job's resource usage
                    // Fix: Use assignment instead of accumulation for total time, as
                    // getThreadCpuTime returns cumulative time
                    // Note: This only tracks the main thread. Worker threads add their stats
                    // separately.
                    job.resourceUsage.cpuTimeUsed = cpuTime
                    // Fix: maxMemoryUsed represents total allocated bytes here, so we accumulate if
                    // we track partials,
                    // but here it tracks the monotonically increasing allocation of the main thread.
                    job.resourceUsage.maxMemoryUsed = allocatedMB
                }
            }

            // Track peak heap memory usage across all threads
            val memoryMXBean = ManagementFactory.getMemoryMXBean()
            val heapUsed = memoryMXBean.heapMemoryUsage.used
            val heapUsedMB = heapUsed / (1024.0f * 1024.0f)

            // Update peak memory if current usage is higher
            if (heapUsedMB > job.resourceUsage.peakMemoryUsed) {
                job.resourceUsage.peakMemoryUsed = heapUsedMB
            }
        } catch (t: Throwable) {
            // java.management or jdk.management module may not be available in minimal JRE builds;
            // resource usage stats will remain at their current values.
        }
    }

    private fun setJobOutputToSpecctraSes(job: RoutingJob) {
        var output = job.output
        val input = job.input
        if (output == null && input != null) {
            val newOutput = BoardFileDetails(job.board)
            newOutput.addUpdatedEventListener { _ -> job.fireOutputUpdatedEvent() }
            newOutput.format = FileFormat.SES
            newOutput.setFilename(input.filenameWithoutExtension + ".ses")
            job.output = newOutput
            output = newOutput
        }

        // save the result to the output field as a Specctra SES file
        if (output != null && output.format == FileFormat.SES) {
            val boardManager = HeadlessBoardManager(job)
            boardManager.replaceRoutingBoard(job.board)

            // Save the SES file after the auto-router has finished
            try {
                ByteArrayOutputStream().use { baos ->
                    if (boardManager.saveAsSpecctraSessionSes(baos, job.name)) {
                        output.setData(baos.toByteArray())
                    }
                }
            } catch (e: Exception) {
                FRLogger.error("Couldn't save the output into the job object.", e)
            }
        }
    }
}
