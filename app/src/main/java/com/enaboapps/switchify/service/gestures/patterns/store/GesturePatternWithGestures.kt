package com.enaboapps.switchify.service.gestures.patterns.store

import androidx.room.Embedded
import androidx.room.Relation
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.patterns.model.GesturePattern

data class GesturePatternWithGestures(
    @Embedded val pattern: GesturePatternEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "patternId"
    )
    val gestures: List<GestureDataEntity>
) {
    fun toGesturePattern(): GesturePattern {
        return pattern.toGesturePattern(gestures.map { it.toGestureData() })
    }
} 