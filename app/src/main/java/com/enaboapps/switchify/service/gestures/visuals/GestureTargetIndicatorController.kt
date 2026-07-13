package com.enaboapps.switchify.service.gestures.visuals

data class GestureTargetPoint(val x: Int, val y: Int)

enum class GestureTargetIndicatorOwner {
    MENU,
    LINEAR_GESTURE
}

enum class GestureTargetIndicatorSuppression {
    MULTI_FINGER_PREVIEW
}

interface GestureTargetIndicatorRenderer {
    fun show(point: GestureTargetPoint)
    fun hide()
    fun release()
}

class GestureTargetIndicatorController(
    private val renderer: GestureTargetIndicatorRenderer
) {
    private val ownerPoints = mutableMapOf<GestureTargetIndicatorOwner, GestureTargetPoint>()
    private val suppressions = mutableSetOf<GestureTargetIndicatorSuppression>()
    private var renderedPoint: GestureTargetPoint? = null

    fun acquire(owner: GestureTargetIndicatorOwner, point: GestureTargetPoint) {
        ownerPoints[owner] = point
        reconcile()
    }

    fun release(owner: GestureTargetIndicatorOwner) {
        ownerPoints.remove(owner)
        reconcile()
    }

    fun suppress(reason: GestureTargetIndicatorSuppression) {
        suppressions.add(reason)
        reconcile()
    }

    fun resume(reason: GestureTargetIndicatorSuppression) {
        suppressions.remove(reason)
        reconcile()
    }

    fun clear() {
        ownerPoints.clear()
        suppressions.clear()
        reconcile()
    }

    fun release() {
        ownerPoints.clear()
        suppressions.clear()
        renderedPoint = null
        renderer.release()
    }

    private fun reconcile() {
        val desiredPoint = if (suppressions.isEmpty()) {
            ownerPoints[GestureTargetIndicatorOwner.LINEAR_GESTURE]
                ?: ownerPoints[GestureTargetIndicatorOwner.MENU]
        } else {
            null
        }

        if (desiredPoint == renderedPoint) return
        renderedPoint = desiredPoint
        if (desiredPoint == null) {
            renderer.hide()
        } else {
            renderer.show(desiredPoint)
        }
    }
}
