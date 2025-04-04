package com.enaboapps.switchify.service.gestures.data

import android.graphics.PointF
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

class PointFTypeAdapter : TypeAdapter<PointF>() {
    override fun write(out: JsonWriter, value: PointF?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        out.name("x").value(value.x.toDouble())
        out.name("y").value(value.y.toDouble())
        out.endObject()
    }

    override fun read(reader: JsonReader): PointF? {
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        var x = 0f
        var y = 0f
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "x" -> x = reader.nextDouble().toFloat()
                "y" -> y = reader.nextDouble().toFloat()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return PointF(x, y)
    }
} 