package app.freerouting.core

import com.google.gson.annotations.SerializedName

class RouterJobResourceUsage {
    // Total CPU time used in seconds
    @SerializedName("cpu_time")
    @JvmField
    var cpuTimeUsed: Float = 0.0f

    // The total amount of memory allocated in MB (this is not the currently used memory)
    @SerializedName("max_memory")
    @JvmField
    var maxMemoryUsed: Float = 0.0f

    // Peak memory usage across all threads in MB
    @SerializedName("peak_memory")
    @JvmField
    var peakMemoryUsed: Float = 0.0f

    // Total IO read in MB, including input uploads
    @SerializedName("io_read")
    @JvmField
    var ioRead: Float = 0.0f

    // Total IO write in MB, including output and snapshot downloads
    @SerializedName("io_written")
    @JvmField
    var ioWrite: Float = 0.0f
}
