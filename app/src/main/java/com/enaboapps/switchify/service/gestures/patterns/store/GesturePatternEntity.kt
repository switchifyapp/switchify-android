package com.enaboapps.switchify.service.gestures.patterns.store

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.patterns.model.GesturePattern

@Entity(tableName = "gesture_patterns")
data class GesturePatternEntity(
    @PrimaryKey
    val id: String,
    val name: String
) {
    fun toGesturePattern(gestures: List<GestureData>): GesturePattern {
        return GesturePattern(id = id, name = name, gestures = gestures)
    }

    companion object {
        fun fromGesturePattern(pattern: GesturePattern): GesturePatternEntity {
            return GesturePatternEntity(
                id = pattern.id,
                name = pattern.name
            )
        }
    }
} 