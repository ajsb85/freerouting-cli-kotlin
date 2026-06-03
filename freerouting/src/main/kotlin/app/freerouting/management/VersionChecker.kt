package app.freerouting.management

import app.freerouting.logger.FRLogger
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * A class to check for new versions of the application.
 */
class VersionChecker(version: String) : Runnable {

    init {
        CURRENT_VERSION = version
    }

    override fun run() {
        try {
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder().uri(URI.create(GITHUB_RELEASES_URL)).build()

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply { it.body() }
                .thenAccept { this.processResponse(it) }
                .exceptionally { e ->
                    FRLogger.warn("Failed to check for new version")
                    null
                }
        } catch (e: Exception) {
            FRLogger.warn("Failed to check for new version")
        }
    }

    private fun processResponse(responseBody: String) {
        try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val latestVersion = json.get("tag_name").asString

            if (CURRENT_VERSION != latestVersion) {
                FRLogger.info("New version available: $latestVersion")
            } else {
                FRLogger.debug("No new version available.")
            }
        } catch (e: Exception) {
            FRLogger.warn("Failed to parse latest version response")
        }
    }

    companion object {
        private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/freerouting/freerouting/releases/latest"
        private var CURRENT_VERSION = "v1.0"
    }
}
