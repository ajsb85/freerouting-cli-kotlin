package app.freerouting

import app.freerouting.board.BoardLoader
import app.freerouting.constants.Constants
import app.freerouting.core.RoutingJob
import app.freerouting.core.RoutingJobState
import app.freerouting.logger.FRLogger
import app.freerouting.management.SessionManager
import app.freerouting.settings.GlobalSettings
import app.freerouting.settings.sources.DsnFileSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

fun main(args: Array<String>) {
    // 1. Setup user data path
    val userdataPath = Path.of(System.getProperty("java.io.tmpdir"), "freerouting")
    if (!userdataPath.toFile().exists()) {
        userdataPath.toFile().mkdirs()
    }
    GlobalSettings.setUserDataPath(userdataPath)
    GlobalSettings.lockUserDataPath()

    // 2. Configure logging
    System.setProperty("log4j2.disableJndi", "true")
    System.setProperty("log4j2.configurationFactory", "app.freerouting.logger.Log4j2ConfigurationFactory")
    System.setProperty("freerouting.logging.console.enabled", "true")
    System.setProperty("freerouting.logging.console.level", "INFO")
    System.setProperty("freerouting.logging.file.enabled", "true")
    System.setProperty("freerouting.logging.file.level", "DEBUG")
    System.setProperty("freerouting.logging.file.location", userdataPath.resolve("freerouting.log").toString())
    
    // 3. Load global settings
    val globalSettings = (try {
        GlobalSettings.load()
    } catch (e: Exception) {
        null
    }) ?: GlobalSettings()
    Freerouting.globalSettings = globalSettings
    globalSettings.applyCommandLineArguments(args)

    // Ensure we disable GUI and servers since this is a pure CLI tool
    globalSettings.guiSettings.isEnabled = false
    globalSettings.apiServerSettings.isEnabled = false
    globalSettings.mcpServerSettings.isEnabled = false

    val initialInputFile = globalSettings.initialInputFile
    val initialOutputFile = globalSettings.initialOutputFile

    if (initialInputFile == null || initialOutputFile == null) {
        System.err.println("Both an input file (-de) and an output file (-do) must be specified.")
        System.exit(1)
        return
    }

    println("=== Starting Freerouting CLI (Kotlin Migration) ===")
    println("Input DSN: $initialInputFile")
    println("Output SES: $initialOutputFile")

    // Start session and job
    val cliSession = SessionManager.getInstance().createSession(
        UUID.fromString(globalSettings.userProfileSettings.userId),
        "Freerouting-Kotlin-CLI/" + Constants.FREEROUTING_VERSION
    )
    val routingJob = RoutingJob(cliSession.id)

    // Load input
    try {
        routingJob.setInput(initialInputFile)
    } catch (e: Exception) {
        System.err.println("Couldn't load input file: ${e.message}")
        System.exit(1)
        return
    }

    val jobInput = routingJob.input
    if (jobInput == null) {
        System.err.println("Input file is null, aborting.")
        System.exit(1)
        return
    }

    cliSession.addJob(routingJob)

    val desiredOutputFile = File(initialOutputFile)
    if (desiredOutputFile.exists()) {
        desiredOutputFile.delete()
    }
    routingJob.tryToSetOutputFile(desiredOutputFile)

    val settingsMerger = globalSettings.settingsMergerProtype.clone()
    settingsMerger.addOrReplaceSources(
        DsnFileSettings(jobInput.data, jobInput.filename)
    )

    routingJob.routerSettings = settingsMerger.merge()
    routingJob.drcSettings = globalSettings.drcSettings.clone()
    routingJob.state = RoutingJobState.READY_TO_START

    // Wait for the RoutingJobScheduler to complete routing
    while (routingJob.state != RoutingJobState.COMPLETED && routingJob.state != RoutingJobState.TERMINATED) {
        try {
            Thread.sleep(500)
        } catch (e: InterruptedException) {
            routingJob.state = RoutingJobState.CANCELLED
            break
        }
    }

    if (routingJob.state == RoutingJobState.COMPLETED) {
        try {
            val outputFilePath = Path.of(initialOutputFile)
            val jobOutput = routingJob.output
            if (jobOutput != null) {
                Files.write(outputFilePath, jobOutput.data.readAllBytes())
            } else {
                throw IllegalStateException("Output is null")
            }
            println("=== CLI Routing Completed Successfully ===")
            println("Output SES written to: $outputFilePath")
            System.exit(0)
        } catch (e: Exception) {
            System.err.println("Couldn't save output file: ${e.message}")
            System.exit(1)
        }
    } else {
        System.err.println("Routing job completed with state: ${routingJob.state}")
        System.exit(1)
    }
}
