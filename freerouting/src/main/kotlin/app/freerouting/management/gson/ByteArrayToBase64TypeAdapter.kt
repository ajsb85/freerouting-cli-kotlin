package app.freerouting.management.gson

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.util.Base64

class ByteArrayToBase64TypeAdapter : JsonSerializer<ByteArray>, JsonDeserializer<ByteArray> {
    override fun serialize(src: ByteArray?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(Base64.getEncoder().encodeToString(src ?: ByteArray(0)))
    }

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): ByteArray {
        return Base64.getDecoder().decode(json.asString)
    }
}
