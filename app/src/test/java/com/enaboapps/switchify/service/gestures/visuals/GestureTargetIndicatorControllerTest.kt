package com.enaboapps.switchify.service.gestures.visuals

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureTargetIndicatorControllerTest {
    @Test
    fun firstOwnerShowsAndLastOwnerHidesIndicator() {
        val renderer = RecordingRenderer()
        val controller = GestureTargetIndicatorController(renderer)
        val point = GestureTargetPoint(100, 200)

        controller.acquire(GestureTargetIndicatorOwner.MENU, point)
        controller.release(GestureTargetIndicatorOwner.MENU)

        assertEquals(listOf("show:100,200", "hide"), renderer.events)
    }

    @Test
    fun overlappingOwnersReuseOneIndicator() {
        val renderer = RecordingRenderer()
        val controller = GestureTargetIndicatorController(renderer)
        val point = GestureTargetPoint(100, 200)

        controller.acquire(GestureTargetIndicatorOwner.MENU, point)
        controller.acquire(GestureTargetIndicatorOwner.LINEAR_GESTURE, point)
        controller.release(GestureTargetIndicatorOwner.LINEAR_GESTURE)
        controller.release(GestureTargetIndicatorOwner.MENU)

        assertEquals(listOf("show:100,200", "hide"), renderer.events)
    }

    @Test
    fun repeatedOwnershipCallsAreIdempotent() {
        val renderer = RecordingRenderer()
        val controller = GestureTargetIndicatorController(renderer)
        val point = GestureTargetPoint(100, 200)

        controller.acquire(GestureTargetIndicatorOwner.MENU, point)
        controller.acquire(GestureTargetIndicatorOwner.MENU, point)
        controller.resume(GestureTargetIndicatorSuppression.MULTI_FINGER_PREVIEW)

        assertEquals(listOf("show:100,200"), renderer.events)
    }

    @Test
    fun linearGesturePointTakesPriorityAndMenuPointIsRestored() {
        val renderer = RecordingRenderer()
        val controller = GestureTargetIndicatorController(renderer)

        controller.acquire(
            GestureTargetIndicatorOwner.MENU,
            GestureTargetPoint(100, 200)
        )
        controller.acquire(
            GestureTargetIndicatorOwner.LINEAR_GESTURE,
            GestureTargetPoint(300, 400)
        )
        controller.release(GestureTargetIndicatorOwner.LINEAR_GESTURE)

        assertEquals(
            listOf("show:100,200", "show:300,400", "show:100,200"),
            renderer.events
        )
    }

    @Test
    fun multiFingerPreviewSuppressesAndRestoresIndicator() {
        val renderer = RecordingRenderer()
        val controller = GestureTargetIndicatorController(renderer)

        controller.acquire(
            GestureTargetIndicatorOwner.MENU,
            GestureTargetPoint(100, 200)
        )
        controller.suppress(GestureTargetIndicatorSuppression.MULTI_FINGER_PREVIEW)
        controller.acquire(
            GestureTargetIndicatorOwner.LINEAR_GESTURE,
            GestureTargetPoint(300, 400)
        )
        controller.release(GestureTargetIndicatorOwner.LINEAR_GESTURE)
        controller.resume(GestureTargetIndicatorSuppression.MULTI_FINGER_PREVIEW)

        assertEquals(
            listOf("show:100,200", "hide", "show:100,200"),
            renderer.events
        )
    }

    @Test
    fun clearAndReleaseRemoveAllState() {
        val renderer = RecordingRenderer()
        val controller = GestureTargetIndicatorController(renderer)

        controller.acquire(
            GestureTargetIndicatorOwner.MENU,
            GestureTargetPoint(100, 200)
        )
        controller.clear()
        controller.acquire(
            GestureTargetIndicatorOwner.LINEAR_GESTURE,
            GestureTargetPoint(300, 400)
        )
        controller.release()

        assertEquals(
            listOf("show:100,200", "hide", "show:300,400", "release"),
            renderer.events
        )
    }

    private class RecordingRenderer : GestureTargetIndicatorRenderer {
        val events = mutableListOf<String>()

        override fun show(point: GestureTargetPoint) {
            events.add("show:${point.x},${point.y}")
        }

        override fun hide() {
            events.add("hide")
        }

        override fun release() {
            events.add("release")
        }
    }
}
