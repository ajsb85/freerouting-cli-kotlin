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
class SegmentClient(private val libraryVersion: String, private val writeKey: String) : AnalyticsClient {

    private val libraryName = "freerouting"
    private var enabled = true

    @Throws(IOException::class)
    private fun sendPayloadAsync(endpoint: String, payload: Payload) {
        if (!enabled) {
            return
        }

        thread {
            try {
                // Serialize to JSON using GSON
                val jsonPayload = GsonProvider.GSON.toJson(payload)

                // Create and configure HTTP connection
                val url = URI(endpoint).toURL()

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; utf-8")
                connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(("$writeKey:").toByteArray()))
                connection.doOutput = true

                // Write JSON payload to request
                connection.outputStream.use { os ->
                    val input = jsonPayload.toByteArray(StandardCharsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                // Read the response
                connection.inputStream.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { br ->
                        val response = StringBuilder()
                        var responseLine: String?
                        while (br.readLine().also { responseLine = it } != null) {
                            response.append(responseLine?.trim())
                        }
                    }
                }
            } catch (ignored: Exception) {
                //FRLogger.error("Exception in SegmentClient.send_payload_async: " + e.getMessage(), e);
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

        sendPayloadAsync("${SEGMENT_ENDPOINT}identify", payload)
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

        sendPayloadAsync("${SEGMENT_ENDPOINT}track", payload)
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    companion object {
        private const val SEGMENT_ENDPOINT = "https://api.segment.io/v1/"
    }
}
