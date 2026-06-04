package app.freerouting.management.analytics

import app.freerouting.management.analytics.dto.Context
import app.freerouting.management.analytics.dto.Library
import app.freerouting.management.analytics.dto.Payload
import app.freerouting.management.analytics.dto.Properties
import app.freerouting.management.analytics.dto.Traits
import app.freerouting.management.gson.GsonProvider
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.concurrent.thread

/**
 * A client for Segment's HTTP API.
 */
class FreeroutingAnalyticsClient(private val libraryVersion: String, private val writeKey: String) : AnalyticsClient {

    private val libraryName = "freerouting"
    private var enabled = true

    @Throws(IOException::class)
    private fun sendPayloadAsync(endpoint: String, payload: Payload) {
        if (!enabled) {
            return
        }

        thread {
            var connection: HttpURLConnection? = null

            try {
                // Serialize to JSON using GSON
                val jsonPayload = GsonProvider.GSON.toJson(payload)
                val uri = URI(endpoint)

                // Create and configure HTTP connection
                connection = uri.toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Host", uri.host)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(("$writeKey:").toByteArray())
                )
                connection.doOutput = true

                // Write JSON payload to request
                connection.outputStream.use { os ->
                    val input = jsonPayload.toByteArray(StandardCharsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                // Check the HTTP response code *before* touching getInputStream() so that we can
                // read the error-stream body on HTTP 4xx/5xx — getInputStream() would throw and
                // swallow that body.
                val responseCode = connection.responseCode
                if (responseCode >= 400) {
                    // Read the server's error body for diagnostic context and forward it to the
                    // aggregator so it can surface it in the hourly summary log.
                    val errorBody = readErrorBody(connection)
                    AnalyticsErrorAggregator.recordFailure(
                        endpoint,
                        IOException("Server returned HTTP response code: $responseCode for URL: $endpoint"),
                        errorBody
                    )
                } else {
                    // Consume the success body (currently unused but keeps the connection clean).
                    BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { br ->
                        while (br.readLine() != null) { /* discard */ }
                    }
                }
            } catch (e: Exception) {
                // Do not log here directly — connection may be null if the exception was thrown
                // before openConnection() succeeded, and a per-failure log line would flood the
                // output when the analytics endpoint is down.  Delegate to the aggregator, which
                // logs the first failure immediately and then emits a single hourly summary.
                AnalyticsErrorAggregator.recordFailure(endpoint, e)
            }
        }
    }

    @Throws(IOException::class)
    override fun identify(userId: String?, anonymousId: String?, traits: Traits?) {
        val payload = Payload().apply {
            this.userId = userId
            this.anonymousId = anonymousId
            this.context = Context().apply {
                this.library = Library().apply {
                    this.name = libraryName
                    this.version = libraryVersion
                }
            }
            this.traits = traits
        }

        sendPayloadAsync("${FREEROUTING_ANALYTICS_ENDPOINT}analytics/identify", payload)
    }

    @Throws(IOException::class)
    override fun track(userId: String?, anonymousId: String?, event: String?, properties: Properties?) {
        val payload = Payload().apply {
            this.userId = userId
            this.anonymousId = anonymousId
            this.context = Context().apply {
                this.library = Library().apply {
                    this.name = libraryName
                    this.version = libraryVersion
                }
            }
            this.event = event
            this.properties = properties
        }

        sendPayloadAsync("${FREEROUTING_ANALYTICS_ENDPOINT}analytics/track", payload)
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    companion object {
        private const val FREEROUTING_ANALYTICS_ENDPOINT = "https://api.freerouting.app/v1/"

        /**
         * Safely reads the body from [HttpURLConnection.getErrorStream].
         * Returns an empty string if the error stream is null or unreadable.
         */
        private fun readErrorBody(connection: HttpURLConnection): String {
            val errorStream = connection.errorStream ?: return ""
            return try {
                BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { br ->
                    val sb = StringBuilder()
                    var line: String?
                    while (br.readLine().also { line = it } != null) {
                        sb.append(line?.trim())
                    }
                    sb.toString()
                }
            } catch (ignored: Exception) {
                ""
            }
        }
    }
}
