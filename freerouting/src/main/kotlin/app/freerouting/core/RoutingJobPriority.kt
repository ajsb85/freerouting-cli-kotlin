package app.freerouting.core

enum class RoutingJobPriority(value: Float) {
    LOWEST(0.0f),
    LOW(2.0f),
    BELOWNORMAL(4.0f),
    NORMAL(5.0f),
    ABOVENORMAL(6.0f),
    HIGH(8.0f),
    HIGHEST(10.0f);

    @JvmField
    val value: Float = Math.max(0.0f, Math.min(value, 10.0f))
}
