package com.enaboapps.switchify.service.gestures.patterns.model

import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.patterns.GesturePatternExecutor
import java.util.UUID

data class GesturePattern(
    val id: String = UUID.randomUUID().toString(),
    val gestures: List<GestureData>,
    val name: String
) {
    fun execute() {
        val executor = GesturePatternExecutor(this)
        executor.execute()
    }
}