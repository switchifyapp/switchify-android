package com.enaboapps.switchify.service.scanning.tree

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.scanners.BaseNodeScanner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LiveScanTreeTest {
    private class TestContext(base: Context, manual: Boolean) : ContextWrapper(base) {
        private val prefs = base.getSharedPreferences("live_scan_test_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        init {
            prefs.edit().putString("scan_mode", if (manual) "manual" else "auto")
                .putLong("scan_rate", 100).putString("scan_cycles", "1000")
                .putBoolean("row_column_scan", false).putBoolean("group_scan", false).commit()
        }
        override fun createDeviceProtectedStorageContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    private class TestNode(val id: String, val revision: Int, val highlighted: (TestNode) -> Unit) :
        ScanNodeInterface, ScanNodeIdentityProvider {
        override val scanIdentity = ScanNodeIdentity("app:0:1", id, null, "button", emptyList())
        var selected = false
        override fun getLeft() = id.first().code * 2
        override fun getTop() = 10
        override fun getMidX() = getLeft() + 5
        override fun getMidY() = 15
        override fun getWidth() = 10
        override fun getHeight() = 10
        override fun getContentDescription() = "$id:$revision"
        override fun highlight() { highlighted(this) }
        override fun unhighlight() = Unit
        override fun select() { selected = true }
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Test fun livelySnapshotsKeepAutoScanningAndRefreshSelectionReferences() {
        lateinit var tree: ScanTree
        var current: TestNode? = null
        var stops = 0
        val visited = mutableSetOf<String>()
        fun nodes(revision: Int) = listOf("a", "b", "c").map { id ->
            TestNode(id, revision) { current = it; visited.add(it.id) }
        }
        onMain {
            tree = ScanTree(TestContext(instrumentation.targetContext, false), callback = object : ScanTreeCallback {
                override fun onScanTreeStopped() { stops++ }
            })
            tree.reconcileNodes(nodes(0))
            tree.startAutoScanning()
        }
        try {
            repeat(50) { revision ->
                Thread.sleep(20)
                onMain {
                    val id = current!!.id
                    tree.reconcileNodes(nodes(revision + 1))
                    assertEquals(id, current!!.id)
                    assertEquals(revision + 1, current!!.revision)
                    assertTrue(tree.isAutoScanning())
                    assertEquals(0, stops)
                }
            }
            onMain {
                assertEquals(setOf("a", "b", "c"), visited)
                val fresh = current!!
                tree.performSelectionAction()
                assertTrue(fresh.selected)
            }
        } finally { onMain { tree.cleanup() } }
    }

    @Test fun manualRemovalChoosesSuccessorAndEmptySnapshotRecoversWithoutAutoStart() {
        lateinit var tree: ScanTree
        var current: TestNode? = null
        fun node(id: String, revision: Int) = TestNode(id, revision) { current = it }
        onMain {
            tree = ScanTree(TestContext(instrumentation.targetContext, true))
            tree.reconcileNodes(listOf(node("a", 0), node("b", 0), node("c", 0)))
            tree.stepScanningForward()
            tree.stepScanningForward()
            assertEquals("b", current!!.id)
        }
        try {
            onMain {
                tree.reconcileNodes(listOf(node("c", 1), node("a", 1)))
                assertEquals("c", current!!.id)
                assertFalse(tree.isAutoScanning())
                tree.reconcileNodes(emptyList())
                assertTrue(tree.isEmpty())
                tree.performSelectionAction()
                tree.reconcileNodes(listOf(node("a", 2), node("c", 2)))
                assertEquals("c", current!!.id)
                assertFalse(tree.isAutoScanning())
                tree.performSelectionAction()
                assertTrue(current!!.selected)
            }
        } finally { onMain { tree.cleanup() } }
    }

    @Test fun emptySnapshotResumesRunningScannerButExplicitPausePreventsResume() {
        lateinit var tree: ScanTree
        fun nodes() = listOf(TestNode("a", 0) {}, TestNode("b", 0) {})
        onMain {
            tree = ScanTree(TestContext(instrumentation.targetContext, false))
            tree.reconcileNodes(nodes())
            tree.startAutoScanning()
        }
        try {
            onMain {
                tree.reconcileNodes(emptyList())
                assertFalse(tree.isAutoScanning())
                tree.reconcileNodes(nodes())
                assertTrue(tree.isAutoScanning())
                tree.reconcileNodes(emptyList())
                tree.pauseAutoScanning()
                tree.reconcileNodes(nodes())
                assertFalse(tree.isAutoScanning())
                tree.stopScanningAndReset()
                tree.reconcileNodes(nodes())
                assertFalse(tree.isAutoScanning())
            }
        } finally { onMain { tree.cleanup() } }
    }

    @Test fun sourceChangeResetsPositionWithoutStartingPausedOrStoppedScanning() {
        lateinit var tree: ScanTree
        var current: TestNode? = null
        fun nodes() = listOf("a", "b").map { TestNode(it, 0) { current = it } }
        onMain {
            tree = ScanTree(TestContext(instrumentation.targetContext, false))
            tree.reconcileNodes(nodes())
            tree.startAutoScanning()
            tree.pauseAutoScanning()
            tree.stepScanningForward()
            assertEquals("b", current!!.id)
        }
        try {
            onMain {
                tree.reconcileNodes(nodes(), sourceChanged = true)
                assertEquals("a", current!!.id)
                assertFalse(tree.isAutoScanning())
                tree.resumeAutoScanning()
                assertTrue(tree.isAutoScanning())
                tree.stopScanningAndReset()
                tree.reconcileNodes(nodes(), sourceChanged = true)
                assertFalse(tree.isAutoScanning())
            }
        } finally { onMain { tree.cleanup() } }
    }

    @Test fun scannerAcceptsSustainedAccessibilitySnapshotsWithoutStopping() {
        lateinit var scanner: BaseNodeScanner
        var current: String? = null
        val visited = mutableSetOf<String>()
        var stops = 0
        fun nodes(revision: Int) = listOf("a", "b", "c").mapIndexed { index, id ->
            val info = AccessibilityNodeInfo.obtain().apply {
                packageName = "com.example.live"
                className = "android.widget.Button"
                viewIdResourceName = "com.example.live:id/$id"
                contentDescription = "$id:$revision"
                setBoundsInScreen(Rect(10 + index * 100, 50, 90 + index * 100, 100))
            }
            AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(info, true)
            Node.fromAccessibilityNodeInfo(info, listOf(index)).apply {
                onHighlight = { current = it.getContentDescription(); visited.add(id) }
            }
        }
        onMain {
            scanner = object : BaseNodeScanner() {
                override fun onScanTreeStopped() { stops++ }
            }
            scanner.start(TestContext(instrumentation.targetContext, false))
            scanner.updateNodes(nodes(0))
            scanner.scanTree.startAutoScanning()
        }
        try {
            repeat(260) { revision ->
                Thread.sleep(20)
                onMain {
                    val id = current!!.substringBefore(':')
                    scanner.updateNodes(nodes(revision + 1))
                    assertEquals("$id:${revision + 1}", current)
                    assertTrue(scanner.scanTree.isAutoScanning())
                    assertEquals(0, stops)
                }
            }
            onMain { assertEquals(setOf("a", "b", "c"), visited) }
        } finally { onMain { scanner.cleanup() } }
    }
}
