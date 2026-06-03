package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StatisticsSettings : Serializable {

  @SerializedName("start_time")
  @JvmField
  var startTime: String

  @SerializedName("end_time")
  @JvmField
  var endTime: String? = null

  @SerializedName("sessions_total")
  @JvmField
  var sessionsTotal: Int = 0

  @SerializedName("jobs_started")
  @JvmField
  var jobsStarted: Int = 0

  @SerializedName("jobs_completed")
  @JvmField
  var jobsCompleted: Int = 0

  init {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    startTime = formatter.format(Instant.now())
  }

  private fun setEndTime() {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    endTime = formatter.format(Instant.now())
  }

  fun incrementSessionsTotal() {
    sessionsTotal++
    setEndTime()
  }

  fun incrementJobsStarted() {
    jobsStarted++
    setEndTime()
  }

  fun incrementJobsCompleted() {
    jobsCompleted++
    setEndTime()
  }
}
