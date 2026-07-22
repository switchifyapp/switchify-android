package com.enaboapps.switchify.service.techniques.nodes.scanners

import android.content.Context
import android.widget.RelativeLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeScannerUiExclusivityTest {
    @Test
    fun rapidRoleTransitionsReuseOneHighlightView() {
        runOnMainThread { context ->
            val window = FakeNodeScannerOverlayWindow(context)
            val ui = NodeScannerUI(window, NodeScannerUiDispatcher { it() })

            ui.showRowBounds(0, 0, 300, 100)
            val firstView = window.singleHighlightView()
            window.assertExclusive()

            ui.showItemBounds(20, 20, 80, 40)
            assertSame(firstView, window.singleHighlightView())
            window.assertExclusive()

            ui.showEscapeBounds(0, 0, 300, 100)
            assertSame(firstView, window.singleHighlightView())
            window.assertExclusive()

            ui.showRowBounds(0, 100, 300, 100)
            assertSame(firstView, window.singleHighlightView())
            window.assertExclusive()
        }
    }

    @Test
    fun mismatchedHideDoesNotRemoveReplacementRole() {
        runOnMainThread { context ->
            val window = FakeNodeScannerOverlayWindow(context)
            val ui = NodeScannerUI(window, NodeScannerUiDispatcher { it() })

            ui.showRowBounds(0, 0, 300, 100)
            ui.hideItemBounds()
            window.assertExclusive(expectedCount = 1)

            ui.showItemBounds(20, 20, 80, 40)
            ui.hideRowBounds()
            window.assertExclusive(expectedCount = 1)

            ui.hideItemBounds()
            window.assertExclusive(expectedCount = 0)
        }
    }

    @Test
    fun targetChangeRemovesOldRootBeforeAddingReplacement() {
        runOnMainThread { context ->
            val window = FakeNodeScannerOverlayWindow(context)
            val ui = NodeScannerUI(window, NodeScannerUiDispatcher { it() })
            val windowTarget = OverlayTarget.Window(
                displayId = 0,
                accessibilityWindowId = 9,
                windowType = 1
            )

            ui.showItemBounds(0, 0, 100, 100)
            ui.showRowBounds(10, 10, 200, 100, windowTarget)

            assertEquals(listOf("add:Display", "remove:Display", "add:Window"), window.operations)
            assertEquals(1, window.roots.size)
            window.assertExclusive()
        }
    }

    @Test
    fun hideAllInvalidatesQueuedCommandsAndClearsRoot() {
        runOnMainThread { context ->
            val window = FakeNodeScannerOverlayWindow(context)
            val pending = mutableListOf<() -> Unit>()
            val ui = NodeScannerUI(window, NodeScannerUiDispatcher { pending += it })

            ui.showItemBounds(0, 0, 100, 100)
            ui.hideAll()
            pending.forEach { it() }

            assertTrue(window.roots.isEmpty())
            window.assertExclusive(expectedCount = 0)
        }
    }

    private fun runOnMainThread(block: (Context) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync { block(context) }
    }

    private class FakeNodeScannerOverlayWindow(
        private val context: Context
    ) : NodeScannerOverlayWindow {
        val roots = mutableListOf<RelativeLayout>()
        val operations = mutableListOf<String>()
        private var maximumHighlightCount = 0

        override fun getContext(): Context = context

        override fun getDisplaySize(target: OverlayTarget): Pair<Int, Int> = 1080 to 2400

        override fun addView(
            target: OverlayTarget,
            view: RelativeLayout,
            x: Int,
            y: Int,
            width: Int,
            height: Int
        ) {
            roots += view
            operations += "add:${target::class.simpleName}"
            recordHighlightCount()
        }

        override fun removeView(target: OverlayTarget, view: RelativeLayout) {
            roots.remove(view)
            operations += "remove:${target::class.simpleName}"
            recordHighlightCount()
        }

        fun singleHighlightView() = roots.single().getChildAt(0)

        fun assertExclusive(expectedCount: Int = 1) {
            recordHighlightCount()
            assertTrue(maximumHighlightCount <= 1)
            assertEquals(expectedCount, roots.sumOf { it.childCount })
        }

        private fun recordHighlightCount() {
            maximumHighlightCount = maxOf(
                maximumHighlightCount,
                roots.sumOf { it.childCount }
            )
        }
    }
}
