package com.enaboapps.switchify.service.techniques.nodes.scanners

import android.content.Context
import android.widget.RelativeLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.scanning.ScanInterval
import com.enaboapps.switchify.service.scanning.ScanIntervalEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeScannerUiExclusivityTest {
    @Test
    fun refreshedBoundsKeepCountdownAndCannotReplaceAnotherScanner() {
        runOnMainThread { context ->
            val window = FakeNodeScannerOverlayWindow(context)
            val ui = NodeScannerUI(window, NodeScannerUiDispatcher { it() })
            ui.withScanVisuals("system") { ui.showItemBounds(0, 0, 100, 100) }
            val interval = ScanInterval(100, 1000)
            ui.updateInterval(ScanIntervalEvent("system", 1, interval))
            repeat(20) {
                ui.withScanVisuals("system", preserveInterval = true, refreshOnly = true) {
                    ui.showItemBounds(it, it, 100, 100)
                }
                assertEquals(interval, (window.singleHighlightView() as ScanHighlightView).interval)
            }
            ui.withScanVisuals("menu") { ui.showRowBounds(0, 0, 300, 100) }
            val menuView = window.singleHighlightView()
            ui.withScanVisuals("system", preserveInterval = true, refreshOnly = true) {
                ui.showItemBounds(20, 20, 100, 100)
            }
            assertSame(menuView, window.singleHighlightView())
        }
    }

    @Test
    fun backgroundResetPreservesDisplayedHighlightAndCountdown() {
        runOnMainThread { context ->
            val window = FakeNodeScannerOverlayWindow(context)
            val ui = NodeScannerUI(window, NodeScannerUiDispatcher { it() })
            for (owner in listOf("menu", "keyboard")) {
                ui.withScanVisuals(owner) { ui.showItemBounds(20, 20, 80, 40) }
                val view = window.singleHighlightView() as ScanHighlightView
                val interval = ScanInterval(100, 1000)
                ui.updateInterval(ScanIntervalEvent(owner, 1, interval))
                assertEquals(interval, view.interval)

                repeat(10) {
                    ui.withScanVisuals("system") { ui.hideAll() }
                    ui.updateInterval(ScanIntervalEvent("system", it.toLong(), null))
                    assertSame(view, window.singleHighlightView())
                    assertEquals(interval, view.interval)
                    window.assertExclusive()
                }
                ui.withScanVisuals(owner) { ui.hideAll() }
                window.assertExclusive(expectedCount = 0)
            }
        }
    }

    @Test
    fun backgroundResetDoesNotInvalidateQueuedMenuRender() {
        runOnMainThread { context ->
            for (resetFirst in listOf(true, false)) {
                val window = FakeNodeScannerOverlayWindow(context)
                val pending = mutableListOf<() -> Unit>()
                val ui = NodeScannerUI(window, NodeScannerUiDispatcher { pending += it })
                val reset = { ui.withScanVisuals("system") { ui.hideAll() } }
                if (resetFirst) reset()
                ui.withScanVisuals("menu") { ui.showRowBounds(0, 0, 300, 100) }
                if (!resetFirst) reset()
                pending.forEach { it() }
                window.assertExclusive()
            }
        }
    }

    @Test
    fun nestedBackgroundResetPreservesPendingMenuHighlight() {
        runOnMainThread { context ->
            val window = FakeNodeScannerOverlayWindow(context)
            val ui = NodeScannerUI(window, NodeScannerUiDispatcher { it() })
            ui.withScanVisuals("menu") {
                ui.showRowBounds(0, 0, 300, 100)
                ui.withScanVisuals("system") { ui.hideAll() }
            }
            window.assertExclusive()
        }
    }

    @Test
    fun delayedRoleHidesCannotRemoveAnotherOwnersHighlight() {
        runOnMainThread { context ->
            val window = FakeNodeScannerOverlayWindow(context)
            val pending = mutableListOf<() -> Unit>()
            val ui = NodeScannerUI(window, NodeScannerUiDispatcher { pending += it })
            val shows = listOf<() -> Unit>(
                { ui.showItemBounds(0, 0, 100, 100) },
                { ui.showRowBounds(0, 0, 100, 100) },
                { ui.showEscapeBounds(0, 0, 100, 100) }
            )
            for (show in shows) {
                ui.withScanVisuals("menu", show)
                ui.withScanVisuals("system") {
                    ui.hideItemBounds()
                    ui.hideRowBounds()
                }
                pending.toList().also { pending.clear() }.forEach { it() }
                window.assertExclusive()
                ui.withScanVisuals("menu") {
                    ui.hideItemBounds()
                    ui.hideRowBounds()
                }
                pending.toList().also { pending.clear() }.forEach { it() }
                window.assertExclusive(expectedCount = 0)
            }
        }
    }

    @Test
    fun ownerResetAndReplacementRenderInOneBatch() {
        runOnMainThread { context ->
            val window = FakeNodeScannerOverlayWindow(context)
            val ui = NodeScannerUI(window, NodeScannerUiDispatcher { it() })
            ui.withScanVisuals("menu") { ui.showRowBounds(0, 0, 300, 100) }
            val view = window.singleHighlightView()
            ui.withScanVisuals("menu") {
                ui.hideAll()
                ui.showItemBounds(20, 20, 80, 40)
            }
            assertSame(view, window.singleHighlightView())
            window.assertExclusive()
            ui.hideAll()
            assertTrue(window.roots.isEmpty())
        }
    }

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

            ui.withScanVisuals("menu") { ui.showItemBounds(0, 0, 100, 100) }
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

        override fun canAttach(target: OverlayTarget): Boolean = true

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
