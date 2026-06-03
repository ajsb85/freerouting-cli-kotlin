package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable

class FeatureFlagsSettings : Serializable {

  @SerializedName("multi_threading")
  @JvmField
  var multiThreading: Boolean = true

  @SerializedName("inspection_mode")
  @JvmField
  var inspectionMode: Boolean = false

  @SerializedName("other_menu")
  @JvmField
  var otherMenu: Boolean = false

  @SerializedName("save_jobs")
  @JvmField
  var saveJobs: Boolean = false
}
