package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.PcKeyboardKey
import org.junit.Assert.assertEquals
import org.junit.Test

class PcKeyboardNavigationClusterTest {
    @Test
    fun navigationKeysUseStableClusterOrder() {
        assertEquals(
            listOf(
                PcKeyboardKey.Escape,
                PcKeyboardKey.ArrowUp,
                PcKeyboardKey.Enter,
                PcKeyboardKey.ArrowLeft,
                PcKeyboardKey.ArrowDown,
                PcKeyboardKey.ArrowRight
            ),
            pcKeyboardNavigationKeys()
        )
    }
}
