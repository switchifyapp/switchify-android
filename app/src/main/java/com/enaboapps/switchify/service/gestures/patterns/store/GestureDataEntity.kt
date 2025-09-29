package com.enaboapps.switchify.service.gestures.patterns.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType

@Entity(
    tableName = "gesture_data",
    foreignKeys = [
        ForeignKey(
            entity = GesturePatternEntity::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["patternId"])
    ]
)
data class GestureDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patternId: String,
    val gestureType: GestureType,
    val startX: Float,
    val startY: Float,
    val endX: Float?,
    val endY: Float?,
    val fingerCount: Int = 1,
    val fingerMode: String? = null
) {
    fun toGestureData(): GestureData {
        return GestureData(
            gestureType = gestureType,
            startPoint = android.graphics.PointF(startX, startY),
            endPoint = if (endX != null && endY != null) android.graphics.PointF(
                endX,
                endY
            ) else null,
            fingerCount = fingerCount,
            fingerMode = fingerMode?.let { 
                try {
                    com.enaboapps.switchify.service.gestures.placement.FingerMode.fromString(it)
                } catch (e: Exception) {
                    com.enaboapps.switchify.service.gestures.placement.FingerMode.ONE
                }
            } ?: com.enaboapps.switchify.service.gestures.placement.FingerMode.ONE
        )
    }

    companion object {
        fun fromGestureData(patternId: String, gestureData: GestureData): GestureDataEntity {
            return GestureDataEntity(
                patternId = patternId,
                gestureType = gestureData.gestureType,
                startX = gestureData.startPoint.x,
                startY = gestureData.startPoint.y,
                endX = gestureData.endPoint?.x,
                endY = gestureData.endPoint?.y,
                fingerCount = gestureData.fingerCount,
                fingerMode = gestureData.fingerMode.name
            )
        }
    }
} 