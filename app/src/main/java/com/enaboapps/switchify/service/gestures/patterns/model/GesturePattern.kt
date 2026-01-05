package com.enaboapps.switchify.service.gestures.patterns.model

import android.content.Context
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.patterns.GesturePatternExecutor
import java.util.UUID

data class GesturePattern(
    val id: String = UUID.randomUUID().toString(),
    val gestures: List<GestureData>,
    val name: String,
    @Transient var context: Context? = null
) {
    fun execute() {
        context?.let { ctx ->
            val executor = GesturePatternExecutor(this, ctx)
            executor.execute()
        }
    }
}