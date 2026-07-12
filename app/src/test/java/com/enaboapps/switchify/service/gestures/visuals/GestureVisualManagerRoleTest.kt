package com.enaboapps.switchify.service.gestures.visuals

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureVisualManagerRoleTest {
    @Test
    fun everyRoleHasADistinctStableListenerId() {
        val roles = GestureVisualManagerRole.entries

        assertEquals(roles.size, roles.map { it.listenerId }.toSet().size)
        assertEquals(
            "visual_manager:linear_gesture_performer",
            GestureVisualManagerRole.LINEAR_GESTURE_PERFORMER.listenerId
        )
    }
}
