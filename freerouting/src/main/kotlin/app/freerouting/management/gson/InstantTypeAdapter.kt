package app.freerouting.management.gson

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.time.Instant

class InstantTypeAdapter : TypeAdapter<Instant>() {
    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: Instant?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.value(value.toString())
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): Instant? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return Instant.parse(reader.nextString())
    }
}
