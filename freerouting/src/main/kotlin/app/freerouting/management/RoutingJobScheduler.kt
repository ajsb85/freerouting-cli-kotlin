package app.freerouting.management

import app.freerouting.Freerouting.globalSettings
import app.freerouting.board.ItemIdentificationNumberGenerator
import app.freerouting.core.RoutingJob
import app.freerouting.core.RoutingJobState
import app.freerouting.core.Session
import app.freerouting.core.StoppableThread
import app.freerouting.core.FileFormat
import app.freerouting.interactive.HeadlessBoardManager
import app.freerouting.logger.FRLogger
import app.freerouting.management.gson.GsonProvider
import app.freerouting.settings.GlobalSettings
import app.freerouting.settings.sources.ApiSettings
import app.freerouting.settings.sources.DsnFileSettings
import app.freerouting.io.specctra.SesReader
import app.freerouting.io.specctra.SesImportSummary
import java.io.IOException
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.LinkedList
import java.util.UUID

/**
 * This singleton class is responsible for managing the jobs that will be
 * processed by the router. The jobs are stored in a priority queue where the
 * jobs with the highest priority are processed first.
 * There is only one instance of this class in the Freerouting process.
 */
class RoutingJobScheduler private constructor() {

    @JvmField
    val jobs = LinkedList<RoutingJob>()
    private val maxParallelJobs = 5

    init {
        // start a loop to process the jobs on another thread
        val loopThread = Thread {
            while (true) {
                try {
                    // loop through jobs with the READY_TO_START state, order them according to
                    // their priority and start them up to the maximum number of parallel jobs
                    while (jobs.size > 0) {
                        val jobsArray: Array<RoutingJob>
                        synchronized(jobs) {
                            // Remove any null entries that could have been introduced by concurrent access
                            jobs.removeIf { j -> j == null }
                            // sort the jobs by priority
                            Collections.sort(jobs)

                            jobsArray = jobs.toTypedArray()
                        }

                        // start the jobs up to the maximum number of parallel jobs (and make a copy of
                        // the list to avoid concurrent modification)
                        for (job in jobsArray) {
                            if (job.state == RoutingJobState.READY_TO_START) {
                                val parallelJobs = jobs.stream()
                                    .filter { j -> j.state == RoutingJobState.RUNNING }
                                    .count()
                                    .toInt()

                                if (parallelJobs < maxParallelJobs) {
                                    val input = job.input
                                    if (input?.data == null) {
                                        FRLogger.warn("RoutingJob input is null, it is skipped.")
                                        job.state = RoutingJobState.INVALID
                                        continue
                                    }

                                    // load the board from the input into a RoutingBoard object
                                    if (input.format == FileFormat.DSN) {
                                        try {
                                            val boardManager = HeadlessBoardManager(job)
                                            boardManager.loadFromSpecctraDsn(
                                                input.data, null,
                                                ItemIdentificationNumberGenerator()
                                            )
                                            val board = boardManager.get_routing_board()
                                            job.board = board

                                            val settingsMerger = globalSettings.settingsMergerProtype.clone()

                                            settingsMerger.addOrReplaceSources(
                                                DsnFileSettings(input.data, input.filename)
                                            )

                                            // Keep per-job overrides (e.g. tests toggling fanout/optimizer) by
                                            // applying the job's current settings as highest-priority API settings.
                                            if (job.routerSettings != null) {
                                                settingsMerger.addOrReplaceSources(ApiSettings(job.routerSettings))
                                            }

                                            // Apply the final merged settings to the job and optimize them for the board
                                            job.routerSettings = settingsMerger.merge()
                                            if (board != null) {
                                                job.routerSettings.applyBoardSpecificOptimizations(board)
                                            }

                                            // Load SES file if specified
                                            if (globalSettings.design_session_filename != null) {
                                                try {
                                                    val sesFile = java.io.File(globalSettings.design_session_filename)
                                                    if (sesFile.exists()) {
                                                        FRLogger.info("Loading SES file: ${globalSettings.design_session_filename}")
                                                        java.io.FileInputStream(sesFile).use { sesStream ->
                                                            val summary = SesReader.read(sesStream, board)
                                                            FRLogger.info(
                                                                "SES file loaded: ${summary.wiresImported()} wires, " +
                                                                        "${summary.viasImported()} vias imported" +
                                                                        if (summary.errorsEncountered() > 0) " (${summary.errorsEncountered()} errors)" else ""
                                                            )
                                                        }
                                                    } else {
                                                        FRLogger.warn("SES file not found: ${globalSettings.design_session_filename}")
                                                    }
                                                } catch (e: Exception) {
                                                    FRLogger.error("Failed to load SES file", e)
                                                }
                                            }

                                            // All pre-checks look fine, start the routing process on a new thread
                                            val routerThread = RoutingJobSchedulerActionThread(job)
                                            job.thread = routerThread
                                            routerThread.start()
                                            job.state = RoutingJobState.RUNNING
                                        } catch (e: Exception) {
                                            FRLogger.error(
                                                "Failed to set up routing job '${job.id}', it will be terminated.",
                                                e
                                            )
                                            job.state = RoutingJobState.TERMINATED
                                        }
                                    } else {
                                        FRLogger.warn("Only DSN format is supported as an input.")
                                        job.state = RoutingJobState.INVALID
                                        continue
                                    }
                                } else {
                                    break
                                }
                            }
                        }
                    }

                    // wait for a short time before checking the queue again
                    Thread.sleep(250)
                } catch (e: InterruptedException) {
                    FRLogger.error("RoutingJobScheduler thread was interrupted.", e)
                } catch (e: Exception) {
                    FRLogger.error(
                        "RoutingJobScheduler thread encountered an unexpected error and will continue.",
                        e
                    )
                }
            }
        }
        loopThread.start()
    }

    private fun UUIDtoShortCode(uuid: UUID): String {
        return uuid.toString()
            .substring(0, 6)
            .uppercase()
    }

    /**
     * Enqueues a job to be processed by the router.
     *
     * @param job The job to enqueue.
     * @return The job that was enqueued.
     */
    fun enqueueJob(job: RoutingJob): RoutingJob {
        // Get the session object from the SessionManager and user ID from the job
        val sessionId = job.sessionId ?: throw IllegalArgumentException("The job must have a session ID.")

        val session = SessionManager.getInstance().getSession(sessionId.toString())
            ?: throw IllegalArgumentException("The session does not exist.")

        val userId = session.userId ?: throw IllegalArgumentException("The session must have a user ID.")

        job.state = RoutingJobState.QUEUED

        synchronized(jobs) {
            this.jobs.add(job)
        }

        globalSettings.statistics.incrementJobsStarted()

        return job
    }

    fun saveJob(job: RoutingJob) {
        if (globalSettings.featureFlags.saveJobs) {
            var sessionIdString = "null"
            var userIdString = "null"

            try {
                val session = SessionManager.getInstance().getSession(job.sessionId.toString())

                if (session == null) {
                    FRLogger.error(
                        "Failed to save job in session '%s' to disk, because the session does not exist."
                            .format(job.sessionId), null
                    )
                } else {
                    sessionIdString = session.id.toString()
                    userIdString = session.userId.toString()

                    saveJob("U-" + UUIDtoShortCode(session.userId), "S-" + UUIDtoShortCode(session.id), job)
                }
            } catch (e: IOException) {
                FRLogger.error(
                    "Failed to save job for user '%s' in session '%s' to disk.".format(userIdString, sessionIdString),
                    e
                )
            }
        }
    }

    @Throws(IOException::class)
    private fun saveJob(userFolder: String, sessionFolder: String, job: RoutingJob) {
        // Create the user's folder if it doesn't exist
        val userDataPath = GlobalSettings.userDataPath ?: throw IOException("UserDataPath is null")
        val userFolderPath = userDataPath
            .resolve("data")
            .resolve(userFolder)

        // Make sure that we have the directory structure in place, and create it if it
        // doesn't exist
        Files.createDirectories(userFolderPath)

        // Check if we already have a directory that has a name with the ending of
        // sessionFolder
        val sessionFolderPath = Files.list(userFolderPath)
            .filter { p -> Files.isDirectory(p) }
            .filter { p -> p.fileName.toString().endsWith(sessionFolder) }
            .findFirst()
            .orElse(null) ?: run {
                // List all directories in the user folder and check if they start with a number
                // If they do, then they are job folders, and we can get the highest number and
                // increment it
                val jobFolderCount = Files.list(userFolderPath)
                    .filter { p -> Files.isDirectory(p) }
                    .map { p -> p.fileName }
                    .map { p -> p.toString() }
                    .map { s -> s.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0] } // Extract the numeric prefix before the underscore
                    .filter { s -> s.matches("\\d+".toRegex()) } // Ensure it is numeric
                    .mapToInt { Integer.parseInt(it) }
                    .max()
                    .orElse(0)

                userFolderPath.resolve("%04d_%s".format(jobFolderCount + 1, sessionFolder))
            }

        // Create the session's folder if it doesn't exist
        Files.createDirectories(sessionFolderPath)

        // Save the job to the session's folder using ISO standard date and time format
        val jobFilename = "FRJ_" + TextManager.convertInstantToString(job.createdAt) + "__J-" + UUIDtoShortCode(job.id) + ".json"
        val jobFilePath = sessionFolderPath.resolve(jobFilename)

        try {
            Files.newBufferedWriter(jobFilePath, StandardCharsets.UTF_8).use { writer ->
                GsonProvider.GSON.toJson(job, writer)
            }
        } catch (e: Exception) {
            FRLogger.error("Failed to save job '%s' to disk.".format(job.id), e)
        }

        // Save the input file if the filename is defined and there is data stored in it
        val jobInput = job.input
        if (jobInput?.filename != null && jobInput.filename.isNotEmpty() && jobInput.data != null) {
            val inputFilePath = sessionFolderPath.resolve(jobInput.filename)
            Files.write(inputFilePath, jobInput.data.readAllBytes())
        }

        // Save the output file if the filename is defined and there is data stored in it
        val jobOutput = job.output
        if (jobOutput?.filename != null && jobOutput.filename.isNotEmpty() && jobOutput.data != null) {
            val outputFilePath = sessionFolderPath.resolve(jobOutput.filename)
            Files.write(outputFilePath, jobOutput.data.readAllBytes())
        }
    }

    /**
     * Returns the position of the job in the queue.
     *
     * @param job The job to get the position of.
     * @return The position of the job in the queue or -1 if the job is not in the
     *         queue. 0 means the job is next in line.
     */
    fun getQueuePosition(job: RoutingJob): Int {
        synchronized(jobs) {
            return this.jobs.indexOf(job)
        }
    }

    fun listJobs(): Array<RoutingJob> {
        synchronized(jobs) {
            return this.jobs.toTypedArray()
        }
    }

    fun listJobs(sessionId: String): Array<RoutingJob> {
        synchronized(jobs) {
            return this.jobs.stream()
                .filter { j -> j.sessionId.toString() == sessionId }
                .toArray { size -> arrayOfNulls<RoutingJob>(size) }
                .filterNotNull()
                .toTypedArray()
        }
    }

    fun listJobs(sessionId: String?, userId: UUID): Array<RoutingJob> {
        val sessionManager = SessionManager.getInstance()

        if (sessionId == null) {
            // Get all sessions that belong to the user
            val sessions = sessionManager.getSessions(null, userId)

            // Iterate through the sessions and list all jobs belonging to them
            val result = LinkedList<RoutingJob>()
            for (session in sessions) {
                // List all jobs belonging to the user in the session
                result.addAll(listJobs(session.id.toString()))
            }

            return result.toTypedArray()
        } else {
            val session = sessionManager.getSession(sessionId, userId)

            if (session != null) {
                // List all jobs belonging to the user in the session
                return listJobs(session.id.toString())
            }
        }

        return emptyArray()
    }

    fun getJob(jobId: String): RoutingJob? {
        synchronized(jobs) {
            return this.jobs.stream()
                .filter { j -> j.id.toString() == jobId }
                .findFirst()
                .orElse(null)
        }
    }

    fun clearJobs(sessionId: String) {
        synchronized(jobs) {
            this.jobs.removeIf { j -> j.sessionId.toString() == sessionId }
        }
    }

    /**
     * Cancels a job.
     *
     * @param job The job to cancel.
     */
    fun cancelJob(job: RoutingJob?) {
        if (job == null) {
            return
        }

        synchronized(jobs) {
            if (job.state == RoutingJobState.QUEUED || job.state == RoutingJobState.READY_TO_START) {
                job.state = RoutingJobState.CANCELLED
                job.setCancelledByUser(true)
                saveJob(job)
            } else if (job.state == RoutingJobState.RUNNING) {
                job.state = RoutingJobState.STOPPING
                job.setCancelledByUser(true)
                val thread = job.thread
                if (thread != null) {
                    thread.requestStop()
                }
                saveJob(job)
            } else if (!job.isCancelledByUser() && job.state != RoutingJobState.COMPLETED &&
                job.state != RoutingJobState.TIMED_OUT && job.state != RoutingJobState.TERMINATED
            ) {
                // If the job is in another state (e.g. PAUSED), we can still cancel it
                job.state = RoutingJobState.CANCELLED
                job.setCancelledByUser(true)
                saveJob(job)
            }
        }
    }

    companion object {
        @JvmField
        val instance = RoutingJobScheduler()

        @JvmStatic
        fun getInstance(): RoutingJobScheduler {
            return instance
        }
    }
}
