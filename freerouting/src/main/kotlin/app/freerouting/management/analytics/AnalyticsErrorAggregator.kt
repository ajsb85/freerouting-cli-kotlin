package app.freerouting.management.analytics

import app.freerouting.logger.FRLogger
import java.io.IOException
import java.util.Comparator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Aggregates analytics delivery failures and emits periodic log summaries.
 *
 * <h2>Design goals</h2>
 * <ul>
 *   <li>Never flood the log file. When the analytics endpoint is unreachable every outbound event
 *       fails, which can mean hundreds of failures per minute. Writing a log line per failure would
 *       drown all other output.</li>
 *   <li>Never be completely silent. Operators must be able to tell that analytics delivery is
 *       broken without waiting an indefinitely long time.</li>
 * </ul>
 *
 * <h2>Behaviour</h2>
 * <ol>
 *   <li><b>Immediate first-failure log (WARN):</b> The very first failure in each window is
 *       logged right away, before any aggregation delay. This gives immediate signal on a fresh
 *       deployment with a misconfigured key or on the first failure after a successful
 *       recovery.</li>
 *   <li><b>Silent aggregation:</b> Every subsequent failure in the same window increments an
 *       in-memory counter keyed by a normalised error signature. No further log lines are
 *       produced until the window closes.</li>
 *   <li><b>Hourly flush:</b> A daemon thread runs every {@value #FLUSH_INTERVAL_MINUTES} minutes.
 *       If any failures occurred, it logs a one-line summary per distinct error type, sorted by
 *       frequency. The log level is {@code WARN} when the total is &le; {@value #ERROR_THRESHOLD}
 *       (occasional blip) and {@code ERROR} when it exceeds that threshold (sustained outage that
 *       requires operator attention).</li>
 *   <li><b>Window reset:</b> After each flush the first-failure flag is cleared, so the first
 *       failure in the next window is again logged immediately.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * All mutable state is either {@link java.util.concurrent.atomic atomic} or stored in a
 * {@link ConcurrentHashMap}. The flush uses an atomic read-and-zero ({@link AtomicLong#getAndSet})
 * so counts that arrive between the snapshot and the map cleanup are not lost — they stay in the
 * map and are captured by the next flush.
 */
internal object AnalyticsErrorAggregator {

    /** How often (in minutes) the aggregated error summary is flushed to the log. */
    const val FLUSH_INTERVAL_MINUTES = 60

    /**
     * Total failures in a window above this count → flush at {@code ERROR} level rather than
     * {@code WARN}, signalling a sustained outage that warrants operator action.
     */
    const val ERROR_THRESHOLD = 50

    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()
    private val serverResponseBodies = ConcurrentHashMap<String, String>()
    private const val MAX_BODY_LENGTH = 250
    private val firstFailureLogged = AtomicBoolean(false)
    private val windowTotal = AtomicLong(0)

    init {
        val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            val t = Thread(r, "analytics-error-reporter")
            t.isDaemon = true // must not prevent JVM shutdown
            t
        }
        scheduler.scheduleAtFixedRate(
            { flushErrorSummary() },
            FLUSH_INTERVAL_MINUTES.toLong(),
            FLUSH_INTERVAL_MINUTES.toLong(),
            TimeUnit.MINUTES
        )
    }

    /**
     * Records one analytics delivery failure (network-level: DNS, connect, SSL, reset, …).
     *
     * <p>If this is the first failure in the current window it is logged immediately at {@code WARN}.
     * All subsequent failures in the same window are silently counted until the next hourly flush.
     *
     * @param endpoint the URL that was being called when the failure occurred
     * @param e        the exception that caused the failure
     */
    @JvmStatic
    fun recordFailure(endpoint: String, e: Exception) {
        recordFailure(endpoint, e, null)
    }

    /**
     * Records one analytics delivery failure with an optional server-side error body.
     *
     * <p>Use this overload when the server replied with an HTTP error (4xx/5xx) and you were
     * able to read its response body — it will be included in the hourly summary so that
     * operators can see the exact server-side error message without having to inspect the
     * server logs separately.
     *
     * @param endpoint           the URL that was being called when the failure occurred
     * @param e                  the exception that caused the failure
     * @param serverResponseBody the raw HTTP error body returned by the server, or {@code null}
     */
    @JvmStatic
    fun recordFailure(endpoint: String, e: Exception, serverResponseBody: String?) {
        val key = normaliseKey(endpoint, e)

        // Keep the latest server response body for this key (bounded to MAX_BODY_LENGTH).
        if (!serverResponseBody.isNullOrBlank()) {
            serverResponseBodies[key] = truncate(serverResponseBody, MAX_BODY_LENGTH)
        }

        errorCounts.computeIfAbsent(key) { _ -> AtomicLong(0) }.incrementAndGet()
        windowTotal.incrementAndGet()

        // Log the very first failure in this window immediately so operators do not have
        // to wait up to FLUSH_INTERVAL_MINUTES to discover that analytics delivery is broken.
        if (firstFailureLogged.compareAndSet(false, true)) {
            val body = serverResponseBodies[key]
            val hint = actionableHint(key)
            val msg = StringBuilder("Analytics tracking: first delivery failure in this window - ")
                .append(key)
            if (body != null) {
                msg.append(" | Server response: ").append(body)
            }
            msg.append(". Further failures will be aggregated and reported every ")
                .append(FLUSH_INTERVAL_MINUTES)
                .append(" minutes.")
            if (hint != null) {
                msg.append(' ').append(hint)
            }
            FRLogger.warn(msg.toString())
        }
    }

    /**
     * Snapshots the current window's counters, resets all state, and writes the summary to the log.
     * Called automatically by the scheduled executor; may also be called from tests.
     */
    @JvmStatic
    fun flushErrorSummary() {
        // Reset the first-failure flag for the next window so that a new failure after a
        // recovery is also logged immediately.
        firstFailureLogged.set(false)

        // Atomically drain each counter: read-and-zero, then remove zeroed entries.
        // Failures that arrive between getAndSet(0) and removeIf will leave their counter
        // at > 0, so removeIf leaves the entry in place — those counts are captured next flush.
        val snapshot = HashMap<String, Long>()
        errorCounts.forEach { (key, counter) ->
            val count = counter.getAndSet(0)
            if (count > 0) {
                snapshot[key] = count
            }
        }
        errorCounts.entries.removeIf { entry -> entry.value.get() == 0L }

        val total = windowTotal.getAndSet(0)
        if (total == 0L) {
            return // Clean window — nothing to report.
        }

        val sb = StringBuilder()
        sb.append("Analytics tracking: ")
            .append(total)
            .append(" event(s) failed to deliver in the last ")
            .append(FLUSH_INTERVAL_MINUTES)
            .append(" minutes. Breakdown by error:\n")

        snapshot.entries
            .sortedByDescending { it.value }
            .forEach { entry ->
                val key = entry.key
                sb.append("  • ").append(key).append(": ").append(entry.value).append("×\n")

                // Append the server-side error body if we captured one for this key.
                val body = serverResponseBodies[key]
                if (body != null) {
                    sb.append("      Server response: ").append(body).append('\n')
                }

                // Append a per-error-type actionable hint to guide operators.
                val hint = actionableHint(key)
                if (hint != null) {
                    sb.append("      ").append(hint).append('\n')
                }
            }

        // Drain stale server-response-body entries that no longer have a matching counter.
        serverResponseBodies.keys.retainAll(errorCounts.keys)

        if (total > ERROR_THRESHOLD) {
            // Sustained outage: use ERROR so that log-based alerting rules can fire.
            FRLogger.error(sb.toString(), null)
        } else {
            // Occasional blip: WARN is sufficient.
            FRLogger.warn(sb.toString())
        }
    }

    /**
     * Produces a stable, bounded key from the endpoint URL and exception.
     *
     * <p>Only the {@code /v1/...} path suffix of the endpoint is kept (not the full origin) to avoid
     * environment-specific noise in the key. The exception message is truncated to 100 characters
     * to keep the key bounded regardless of how verbose the underlying runtime message is.
     *
     * @param endpoint the full URL of the call that failed
     * @param e        the exception
     * @return a normalised key suitable for use in a frequency map
     */
    private fun normaliseKey(endpoint: String, e: Exception): String {
        // Keep only the path part starting at "/v1/" to exclude origin (scheme + host + port).
        var path = endpoint
        val v1Index = endpoint.indexOf("/v1/")
        if (v1Index >= 0) {
            path = endpoint.substring(v1Index)
        }

        val exceptionType = e.javaClass.simpleName
        var message = if (e.message != null) e.message!! else "(no message)"
        if (message.length > 200) {
            message = message.substring(0, 200) + "…"
        }

        return "$path - $exceptionType: $message"
    }

    /**
     * Returns a concise, actionable hint for operators based on the normalised error key.
     * Returns {@code null} if no specific guidance is available for this error pattern.
     */
    private fun actionableHint(key: String): String? {
        // ---- Cloudflare proxy errors (never reach the origin application) ----
        // 520–527: generic Cloudflare "origin returned an unexpected response" family.
        // 530 + body "error code: 1033": Cloudflare Argo Tunnel is down between the edge
        //   and the origin server.  The application itself is not involved.
        if (key.contains("IOException") && (key.contains("code: 530") || key.contains(" 530"))) {
            return ("→ Action: HTTP 530 is a Cloudflare proxy error — the request never reached the "
                    + "origin server. Error 1033 in the response body means the Cloudflare Argo Tunnel "
                    + "to the origin is down or misconfigured. Check the Cloudflare dashboard (Zero Trust → "
                    + "Access → Tunnels) and verify that the tunnel connector on the origin host is running "
                    + "(e.g. `cloudflared tunnel run <name>`).")
        }
        if (key.contains("IOException") && (key.contains("code: 52") || key.contains(" 52"))) {
            return ("→ Action: HTTP 52x is a Cloudflare error meaning the origin server returned an "
                    + "unexpected or empty response to Cloudflare. Check origin server health and Cloudflare "
                    + "error logs in the dashboard.")
        }
        // ---- Application-level HTTP errors ----
        if (key.contains("IOException") && (key.contains(" 500") || key.contains("code: 500"))) {
            return ("→ Action: The analytics server returned HTTP 500. Check the server logs and verify that the "
                    + "FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY environment variable "
                    + "is set to a valid service-account JSON on the analytics server.")
        }
        if (key.contains("IOException") && (key.contains(" 401") || key.contains("code: 401"))) {
            return ("→ Action: HTTP 401 Unauthorized. The write key or service-account credentials used by this "
                    + "instance are invalid or expired. Check FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY.")
        }
        // ---- Network / transport errors ----
        if (key.contains("UnknownHostException")) {
            return ("→ Action: DNS resolution failed. Verify that this host has network access and that "
                    + "api.freerouting.app is reachable (try: nslookup api.freerouting.app).")
        }
        if (key.contains("ConnectException")) {
            return ("→ Action: Connection refused or timed out. Check network/firewall rules between this "
                    + "host and the analytics endpoint, or verify that the server is running.")
        }
        if (key.contains("SocketException") && key.contains("reset")) {
            return ("→ Action: Server closed the connection unexpectedly. This may indicate server overload, "
                    + "an intermediate proxy resetting idle connections, or a server-side crash.")
        }
        if (key.contains("SSLHandshakeException")) {
            return ("→ Action: TLS handshake failed. Verify that the JRE trust store contains the server "
                    + "certificate's CA, that the system clock is correct, and that TLS 1.2+ is enabled.")
        }
        return null
    }

    private fun truncate(s: String, maxLen: Int): String {
        return if (s.length <= maxLen) s else s.substring(0, maxLen) + "…"
    }
}
