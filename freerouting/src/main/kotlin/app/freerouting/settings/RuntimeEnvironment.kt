package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.time.Instant

/**
 * Stores runtime environment information about the application execution
 * context.
 * This is NOT for configuration from environment variables - use
 * EnvironmentVariablesSource for that.
 *
 * This class captures system information like Java version, CPU cores, RAM,
 * etc.
 * that are determined at runtime and cannot be configured.
 */
class RuntimeEnvironment : Serializable {

  @SerializedName("freerouting_version")
  @JvmField
  var freeroutingVersion: String? = null

  @SerializedName("app_started_at")
  @JvmField
  var appStartedAt: Instant? = null

  @SerializedName("command_line_arguments")
  @JvmField
  var commandLineArguments: String? = null

  @SerializedName("architecture")
  @JvmField
  var architecture: String? = null

  @SerializedName("java")
  @JvmField
  var java: String? = null

  @SerializedName("system_language")
  @JvmField
  var systemLanguage: String? = null

  @SerializedName("cpu_cores")
  @JvmField
  var cpuCores: Int = 0

  @SerializedName("ram")
  @JvmField
  var ram: Int = 0

  @SerializedName("host")
  @Transient
  @JvmField
  var host: String = "N/A"
}
