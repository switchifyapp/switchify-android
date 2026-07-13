package com.enaboapps.switchify.service.gestures.execution

data class GesturePathPoint(val x: Float, val y: Float)

internal object GesturePathMath {
    fun interpolate(
        start: GesturePathPoint,
        end: GesturePathPoint,
        progress: Float
    ): GesturePathPoint {
        val boundedProgress = progress.coerceIn(0f, 1f)
        return GesturePathPoint(
            start.x + (end.x - start.x) * boundedProgress,
            start.y + (end.y - start.y) * boundedProgress
        )
    }
}

data class GestureTouchPath(
    val start: GesturePathPoint,
    val end: GesturePathPoint
)

data class PinchGestureGeometry(
    val first: GestureTouchPath,
    val second: GestureTouchPath
) {
    companion object {
        fun calculate(centerX: Float, centerY: Float, expands: Boolean): PinchGestureGeometry {
            val startSeparation = if (expands) 50f else 200f
            val endSeparation = if (expands) 200f else 50f
            return PinchGestureGeometry(
                first = GestureTouchPath(
                    GesturePathPoint(centerX - startSeparation / 2f, centerY),
                    GesturePathPoint(centerX - endSeparation / 2f, centerY)
                ),
                second = GestureTouchPath(
                    GesturePathPoint(centerX + startSeparation / 2f, centerY),
                    GesturePathPoint(centerX + endSeparation / 2f, centerY)
                )
            )
        }
    }
}
