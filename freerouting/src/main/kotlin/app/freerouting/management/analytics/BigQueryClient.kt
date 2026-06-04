package app.freerouting.management.analytics

import app.freerouting.logger.FRLogger
import app.freerouting.management.TextManager
import app.freerouting.management.analytics.dto.Context
import app.freerouting.management.analytics.dto.Library
import app.freerouting.management.analytics.dto.Payload
import app.freerouting.management.analytics.dto.Properties
import app.freerouting.management.analytics.dto.Traits
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.InsertAllResponse
import com.google.cloud.bigquery.TableId
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import kotlin.concurrent.thread

/**
 * A client for Google BigQuery's API.
 *
 * <p>Please note that {@code identify}, {@code track}, and {@code users} tables are NOT updated in
 * BigQuery (unlike Segment).
 *
 * <h2>Singleton lifecycle</h2>
 * Creating a BigQuery service involves network I/O (credential refresh against Google's token
 * endpoint) and is expensive enough to avoid doing on every analytics call. Use
 * {@link #getInstance(String, String)} to obtain the shared instance. The singleton is recreated
 * transparently if the service-account key changes (e.g. key rotation), so callers do not need
 * to manage lifecycle themselves.
 */
class BigQueryClient(private val libraryVersion: String, serviceAccountKey: String) : AnalyticsClient {

    private val libraryName = "freerouting"
    private val bigQuery: BigQuery
    private var enabled = true

    init {
        // Enable TLS protocols
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3")
        bigQuery = createBigQueryService(serviceAccountKey.toByteArray())
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

        // NOTE: we ignore the identify event in BigQuery (because we have the tracked
        // "application started" event instead).
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

        sendPayloadAsync(payload)
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    private fun sendPayloadAsync(payload: Payload) {
        if (!enabled) {
            return
        }

        // Snapshot the fields on the calling thread so the background thread doesn't race
        // on mutable payload state.
        val fields = generateFieldsFromPayload(payload)

        thread {
            try {
                val event = payload.event ?: return@thread
                // Table name is the event name with some formatting.
                val tableName = event
                    .lowercase()
                    .replace(" ", "_")
                    .replace("-", "_")

                // Apply a text transformation to the event and event_text fields.
                fields["event_text"] = fields["event"] ?: ""
                fields.remove("event")
                fields["event"] = tableName

                val tableId = TableId.of(BIGQUERY_PROJECT_ID, BIGQUERY_DATASET_ID, tableName)
                val request = InsertAllRequest.newBuilder(tableId)
                    .addRow(InsertAllRequest.RowToInsert.of(fields))
                    .build()

                val response = bigQuery.insertAll(request)
                if (response.hasErrors()) {
                    response.insertErrors.forEach { (_, errors) ->
                        FRLogger.error("Error in BigQueryClient.sendPayloadAsync: ($tableName) $errors", null)
                    }
                }
            } catch (e: Exception) {
                FRLogger.error("Exception in BigQueryClient.sendPayloadAsync: " + e.message, e)
            }
        }
    }

    private fun generateFieldsFromPayload(payload: Payload): MutableMap<String, String> {
        val fields = HashMap<String, String>()

        fields["id"] = "frg-2o0" + TextManager.generateRandomAlphanumericString(25)
        val eventHappenedAt = Instant.now()
        fields["received_at"] = TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC"
        fields["sent_at"] = TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss") + " UTC"
        fields["original_timestamp"] = "<nil>"
        fields["timestamp"] = TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC"

        payload.userId?.let { fields["user_id"] = it }
        payload.anonymousId?.let { fields["anonymous_id"] = it }
        payload.event?.let { fields["event"] = it }
        payload.context?.library?.name?.let { fields["context_library_name"] = it }
        payload.context?.library?.version?.let { fields["context_library_version"] = it }

        val payloadUploadedAt = Instant.now()
        fields["loaded_at"] = TextManager.convertInstantToString(payloadUploadedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC"
        fields["uuid_ts"] = TextManager.convertInstantToString(payloadUploadedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC"

        payload.traits?.let { traits ->
            if (traits.isNotEmpty()) {
                fields.putAll(traits)
            }
        }
        payload.properties?.let { properties ->
            if (properties.isNotEmpty()) {
                fields.putAll(properties)
            }
        }

        return fields
    }

    companion object {
        private const val BIGQUERY_PROJECT_ID = "freerouting-analytics"
        private const val BIGQUERY_DATASET_ID = "freerouting_application"

        // -------------------------------------------------------------------------
        // Singleton state — guarded by the class monitor
        // -------------------------------------------------------------------------

        /** The single shared instance, replaced only when the service-account key changes. */
        @Volatile
        private var singletonInstance: BigQueryClient? = null

        /**
         * The service-account key string that was used to build [singletonInstance]. Compared
         * with the key passed to [getInstance] to detect key rotation.
         */
        @Volatile
        private var singletonKey: String? = null

        /**
         * Returns the shared [BigQueryClient] for the given service-account key, creating (or
         * recreating) it if necessary.
         *
         * <p>This method is thread-safe. The underlying GCP credential refresh and
         * [BigQuery] construction happen at most once per distinct key value, not on every
         * analytics event.
         *
         * @param libraryVersion   the Freerouting version string embedded in every event payload
         * @param serviceAccountKey the full JSON content of the GCP service-account key file
         * @return the shared instance, never null
         */
        @JvmStatic
        fun getInstance(libraryVersion: String, serviceAccountKey: String): BigQueryClient {
            // Fast path — no synchronisation needed if the singleton is already warm and the key
            // hasn't changed.
            val instance = singletonInstance
            if (instance != null && serviceAccountKey == singletonKey) {
                return instance
            }

            synchronized(BigQueryClient::class.java) {
                if (singletonInstance == null || serviceAccountKey != singletonKey) {
                    singletonInstance = BigQueryClient(libraryVersion, serviceAccountKey)
                    singletonKey = serviceAccountKey
                    FRLogger.debug("BigQueryClient: created new singleton instance (library version: $libraryVersion)")
                }
            }

            return singletonInstance!!
        }

        private fun createBigQueryService(serviceAccountKeyBytes: ByteArray): BigQuery {
            return try {
                val keyStream: InputStream = ByteArrayInputStream(serviceAccountKeyBytes)
                val credentials = ServiceAccountCredentials
                    .fromStream(keyStream)
                    .createScoped("https://www.googleapis.com/auth/bigquery")
                credentials.refreshIfExpired()
                BigQueryOptions.newBuilder()
                    .setCredentials(credentials)
                    .build()
                    .service
            } catch (e: IOException) {
                throw RuntimeException("Failed to create BigQuery client", e)
            }
        }
    }
}
