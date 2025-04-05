package com.enaboapps.switchify.service.gestures.data.store

import android.graphics.PointF
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.PointFTypeAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.util.UUID

data class GesturePattern(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerializedName("gestures")
    val gestures: List<GestureData>,
    @SerializedName("name")
    val name: String
) {
    companion object {
        private val gson: Gson = GsonBuilder()
            .registerTypeAdapter(PointF::class.java, PointFTypeAdapter())
            .create()

        fun toJson(pattern: GesturePattern): String {
            return gson.toJson(pattern)
        }

        fun fromJson(json: String): GesturePattern {
            return gson.fromJson(json, GesturePattern::class.java)
        }
    }

    fun execute(): Boolean {
        // TODO: Implement the logic to execute the gesture pattern
        return true
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "gestures" to gestures,
            "name" to name
        )
    }
}