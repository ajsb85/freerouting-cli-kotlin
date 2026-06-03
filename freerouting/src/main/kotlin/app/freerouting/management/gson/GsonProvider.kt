package app.freerouting.management.gson

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import java.nio.file.Path
import java.time.Instant

object GsonProvider {
    @JvmField
    val GSON: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .registerTypeAdapter(ByteArray::class.java, ByteArrayToBase64TypeAdapter())
        .registerTypeAdapter(Path::class.java, PathTypeAdapter())
        .setStrictness(Strictness.LENIENT)
        .create()
}
