package app.freerouting.management.analytics.dto

class Context {
    @JvmField var library: Library? = null
    @JvmField var anonymousId: String? = null
    @JvmField var event: String? = null
    @JvmField var traits: Traits? = null
    @JvmField var properties: Properties? = null
}
