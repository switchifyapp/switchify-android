package com.enaboapps.switchify.pc.remote.actions

import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.protocol.PcPointerProfile
import com.enaboapps.switchify.pc.remote.FakeRemoteSender
import com.enaboapps.switchify.pc.remote.InMemoryPcRemotePreferences
import com.enaboapps.switchify.pc.remote.PcRemoteSession
import com.enaboapps.switchify.pc.remote.PcText
import com.enaboapps.switchify.pc.remote.PcTypingMode
import com.enaboapps.switchify.pc.remote.allRemoteCommands
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSections
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSurface
import com.enaboapps.switchify.pc.remote.remoteProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcActionRuntimeTest {
    private val runtimeCommands = allRemoteCommands.filterNot { it.startsWith("mouse.repeat.") }

    private class Setup(
        val sender: FakeRemoteSender,
        val session: PcRemoteSession,
        val drafts: InMemoryPcRemotePreferences,
        var profile: PcPointerProfile,
        val context: PcActionContext
    )

    private fun TestScope.setup(
        commands: List<String> = runtimeCommands,
        keyRepeat: Boolean = false
    ): Setup {
        val sender = FakeRemoteSender()
        val drafts = InMemoryPcRemotePreferences()
        val holder = arrayOfNulls<PcPointerProfile>(1)
        holder[0] = remoteProfile(commands, mouseRepeat = false, keyRepeat = keyRepeat)
        val session = PcRemoteSession(sender, { holder[0] }, this)
        val context = PcActionContext(PcLayoutSurface.Mouse, session, PcPlatform.Windows, drafts)
        return Setup(sender, session, drafts, holder[0]!!, context)
    }

    private fun action(id: String) = PcActionCatalog.get(id)!!

    private fun live(submitting: Boolean = false, onSubmit: () -> Unit = {}) =
        PcTypingContext(PcTypingMode.Live, "", submitting) { onSubmit() }

    @Test
    fun sharesMonitorExecutionAcrossSurfaces() = runTest {
        val setup = setup()
        for (surface in PcLayoutSurface.entries) {
            PcActionRuntime.execute(action("monitor.left"), setup.context.copy(surface = surface))
        }
        assertEquals(3, setup.sender.calls.size)
        assertTrue(setup.sender.calls.all { it.type == "pointer.display.move" && it.payload == "{\"direction\":\"left\"}" })
    }

    @Test
    fun executesActionsOnDifferentSurfaceThroughSharedSession() = runTest {
        val cases = listOf(
            Triple("move.2.0", "mouse.move", "{\"dx\":64,\"dy\":-64}"),
            Triple("move.1.1", "mouse.click", "{\"button\":\"left\"}"),
            Triple("click.double", "mouse.doubleClick", "{\"button\":\"left\"}"),
            Triple("click.right", "mouse.rightClick", "{}"),
            Triple("scroll.up", "mouse.scroll", "{\"dx\":0,\"dy\":5}"),
            Triple("speed.faster", "pointer.speed.set", "{\"scalePercent\":105}"),
            Triple("window.maximizeFocused", "window.control", "{\"action\":\"maximizeFocused\"}"),
            Triple("key.Enter", "keyboard.key", "{\"key\":\"Enter\"}")
        )
        for ((id, type, payload) in cases) {
            val setup = setup()
            PcActionRuntime.execute(action(id), setup.context.copy(surface = PcLayoutSurface.Window))
            assertEquals(id, type, setup.sender.calls.single().type)
            assertEquals(id, payload, setup.sender.calls.single().payload)
        }
    }

    @Test
    fun retainsLiveEnterSubmissionAndUsesStreamForOtherLiveKeys() = runTest {
        val setup = setup()
        var submits = 0
        val liveContext = setup.context.copy(surface = PcLayoutSurface.Typing, typing = live { submits += 1 })
        PcActionRuntime.execute(action("key.Enter"), liveContext)
        assertEquals(1, submits)
        assertTrue(setup.sender.calls.isEmpty())

        PcActionRuntime.execute(action("key.Backspace"), liveContext)
        assertEquals(listOf("keyboard.textStream.open", "keyboard.textStream.key"), setup.sender.types)
        assertTrue(setup.sender.calls[1].payload.contains("\"key\":\"Backspace\""))

        PcActionRuntime.execute(action("key.Enter"), liveContext.copy(typing = live(submitting = true) { submits += 1 }))
        assertEquals(1, submits)
    }

    @Test
    fun repeatsRepeatableKeyOutsideLiveTypingOnly() = runTest {
        val setup = setup(
            listOf(
                "keyboard.key", "keyboard.textStream.open", "keyboard.textStream.chunk",
                "keyboard.textStream.key", "keyboard.textStream.close", "mouse.repeat.start", "mouse.repeat.stop"
            ),
            keyRepeat = true
        )
        PcActionRuntime.execute(action("key.ArrowDown"), setup.context)
        assertEquals("mouse.repeat.start", setup.sender.calls.last().type)
        assertEquals("keyboard.key", setup.session.snapshot().repeat)

        PcActionRuntime.execute(action("key.ArrowDown"), setup.context)
        assertNull(setup.session.snapshot().repeat)

        setup.sender.calls.clear()
        PcActionRuntime.execute(action("key.ArrowDown"), setup.context.copy(surface = PcLayoutSurface.Typing, typing = live()))
        assertEquals(listOf("keyboard.textStream.open", "keyboard.textStream.key"), setup.sender.types)
        assertNull(setup.session.snapshot().repeat)
    }

    @Test
    fun preventsDraftActionsOutsideTypingAndRespectsDraftModeAndText() = runTest {
        val setup = setup()
        val typing = PcTypingContext(PcTypingMode.Draft, "fixture", false) {}
        setup.drafts.setDraft("fixture")
        PcActionRuntime.execute(action("draft.send"), setup.context.copy(typing = typing))
        assertTrue(setup.sender.calls.isEmpty())
        PcActionRuntime.execute(action("draft.clear"), setup.context.copy(typing = typing))
        assertEquals("fixture", setup.drafts.draft.value)

        PcActionRuntime.execute(action("draft.send"), setup.context.copy(surface = PcLayoutSurface.Typing, typing = typing))
        assertEquals("keyboard.typeText", setup.sender.calls.single().type)
        assertEquals("{\"text\":\"fixture\"}", setup.sender.calls.single().payload)
        assertEquals("", setup.drafts.draft.value)

        val resolved = PcActionRuntime.resolve(
            setup.context.copy(surface = PcLayoutSurface.Typing, typing = typing.copy(mode = PcTypingMode.Live))
        )
        assertEquals(PcActionUnavailable.DraftModeRequired, resolved.first { it.id == "draft.send" }.unavailable)
    }

    @Test
    fun doesNotClearNewerDraftAfterAsynchronousSend() = runTest {
        val setup = setup()
        val gate = setup.sender.gate("keyboard.typeText")
        setup.drafts.setDraft("fixture one")
        val pending = async {
            PcActionRuntime.execute(
                action("draft.send"),
                setup.context.copy(
                    surface = PcLayoutSurface.Typing,
                    typing = PcTypingContext(PcTypingMode.Draft, "fixture one", false) {}
                )
            )
        }
        runCurrent()
        setup.drafts.setDraft("fixture two")
        gate.complete(true)
        pending.await()
        assertEquals("fixture two", setup.drafts.draft.value)
    }

    @Test
    fun updatesLabelsAndExplanationsWithoutRemovingCatalogOptions() = runTest {
        val setup = setup()
        PcActionRuntime.execute(action("modifier.Meta"), setup.context)
        val mac = PcActionRuntime.resolve(setup.context.copy(platform = PcPlatform.MacOs))
        val meta = mac.first { it.id == "modifier.Meta" }
        assertEquals(PcText.Res(R.string.pc_modifier_command), meta.presentation.label)
        assertEquals(true, meta.presentation.selected)
        assertEquals(PcText.Res(R.string.pc_action_hold_modifier, listOf(PcText.Res(R.string.pc_modifier_command))), meta.name)
        assertEquals(
            PcText.Joined(listOf(PcText.Res(R.string.pc_modifier_command), PcText.Literal("C")), "+"),
            mac.first { it.id == "shortcut.C" }.presentation.label
        )
        assertEquals(PcText.Res(R.string.pc_modifier_start), PcActionRuntime.resolve(setup.context).first { it.id == "modifier.Meta" }.presentation.label)
        assertEquals(PcActionCatalog.actions.count { it.behavior !is PcActionBehavior.Draft }, mac.size)

        val singleMonitor = setup(runtimeCommands)
        val oneDisplay = PcRemoteSession(singleMonitor.sender, { remoteProfile(runtimeCommands, displayCount = 1.0) }, this)
        val monitor = PcActionRuntime.resolve(singleMonitor.context.copy(session = oneDisplay)).first { it.id == "monitor.left" }
        assertFalse(monitor.enabled)
        assertEquals(PcActionUnavailable.NeedsMultipleMonitors, monitor.unavailable)

        val fastest = PcRemoteSession(singleMonitor.sender, { remoteProfile(runtimeCommands, scalePercent = 225.0) }, this)
        assertEquals(
            PcActionUnavailable.SpeedAtMaximum,
            PcActionRuntime.resolve(singleMonitor.context.copy(session = fastest)).first { it.id == "speed.faster" }.unavailable
        )

        setup.session.toggleDrag()
        val drag = PcActionRuntime.resolve(setup.context).first { it.id == "drag.toggle" }
        assertEquals(PcText.Res(R.string.pc_action_end_drag), drag.presentation.label)
        assertEquals(true, drag.presentation.selected)
    }

    @Test
    fun rechecksSupportAtExecution() = runTest {
        val sender = FakeRemoteSender()
        var commands = runtimeCommands
        val session = PcRemoteSession(sender, { remoteProfile(commands, mouseRepeat = false) }, this)
        val context = PcActionContext(PcLayoutSurface.Mouse, session, PcPlatform.Windows, InMemoryPcRemotePreferences())
        val resolved = PcActionRuntime.resolve(context).first { it.id == "key.Enter" }
        assertTrue(resolved.enabled)
        commands = emptyList()
        PcActionRuntime.execute(resolved.definition, context)
        assertTrue(sender.calls.isEmpty())
    }

    @Test
    fun movementUsesBaseDeltaBoundedByMaxDelta() = runTest {
        val setup = setup()
        assertEquals(64.0, PcActionRuntime.movementStep(setup.session), 0.0)
        val none = PcRemoteSession(setup.sender, { null }, this)
        assertEquals(128.0, PcActionRuntime.movementStep(none), 0.0)
    }

    @Test
    fun catalogDefinesEveryDefaultActionExactlyOnce() {
        assertEquals(PcActionCatalog.actions.size, PcActionCatalog.actions.map { it.id }.toSet().size)
        PcLayoutSections.definitions.values.flatten().flatMap { it.groups }.flatMap { it.ids }.forEach { id ->
            assertEquals(id, 1, PcActionCatalog.actions.count { it.id == id })
        }
    }

    @Test
    fun permitsCrossSurfaceActionsButReservesDraftForTyping() {
        assertTrue(PcActionCatalog.canPlace("key.Enter", PcLayoutSurface.Mouse))
        assertTrue(PcActionCatalog.canPlace("move.0.0", PcLayoutSurface.Window))
        assertTrue(PcActionCatalog.canPlace("modifier.Meta", PcLayoutSurface.Typing))
        assertTrue(PcActionCatalog.canPlace("draft.send", PcLayoutSurface.Typing))
        assertFalse(PcActionCatalog.canPlace("draft.clear", PcLayoutSurface.Window))
        assertFalse(PcActionCatalog.canPlace("draft.send", PcLayoutSurface.Mouse))
        assertFalse(PcActionCatalog.canPlace("unrecognized", PcLayoutSurface.Mouse))
    }

    @Test
    fun keepsRemoteActionIdsStable() {
        assertEquals(
            listOf(
                "move.0.0", "move.1.0", "move.2.0", "move.0.1", "move.2.1", "move.0.2", "move.1.2", "move.2.2",
                "move.1.1", "click.double", "click.right", "drag.toggle", "scroll.up", "scroll.down",
                "speed.slower", "speed.faster", "monitor.left", "monitor.up", "monitor.down", "monitor.right",
                "modifier.Ctrl", "modifier.Alt", "modifier.Shift", "modifier.Meta",
                "window.switchNext", "window.switchPrevious", "window.taskView", "window.showDesktop",
                "window.minimizeFocused", "window.maximizeFocused", "window.closeFocused",
                "shortcut.A", "shortcut.C", "shortcut.V", "shortcut.X",
                "key.Backspace", "key.Enter", "key.Escape", "key.Tab",
                "key.ArrowLeft", "key.ArrowUp", "key.ArrowDown", "key.ArrowRight",
                "draft.clear", "draft.send"
            ),
            PcActionCatalog.actions.map { it.id }
        )
    }
}
