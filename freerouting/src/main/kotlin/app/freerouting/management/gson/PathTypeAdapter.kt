package app.freerouting.management.gson

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.nio.file.Path

class PathTypeAdapter : TypeAdapter<Path>() {
    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: Path?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.value(value.toString())
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): Path? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return Path.of(reader.nextString())
    }
}
