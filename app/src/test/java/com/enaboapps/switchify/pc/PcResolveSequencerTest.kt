package com.enaboapps.switchify.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcResolveSequencerTest {
    @Test
    fun firstEnqueueStartsImmediately() {
        val sequencer = PcResolveSequencer<String>()

        assertEquals("a", sequencer.enqueue("a"))
    }

    @Test
    fun enqueueWhileActiveQueuesInsteadOfStarting() {
        val sequencer = PcResolveSequencer<String>()
        sequencer.enqueue("a")

        assertNull(sequencer.enqueue("b"))
        assertNull(sequencer.enqueue("c"))
    }

    @Test
    fun finishStartsNextQueuedItemInOrder() {
        val sequencer = PcResolveSequencer<String>()
        sequencer.enqueue("a")
        sequencer.enqueue("b")
        sequencer.enqueue("c")

        assertEquals("b", sequencer.finish())
        assertEquals("c", sequencer.finish())
        assertNull(sequencer.finish())
    }

    @Test
    fun finishWithEmptyQueueAllowsNextEnqueueToStart() {
        val sequencer = PcResolveSequencer<String>()
        sequencer.enqueue("a")
        assertNull(sequencer.finish())

        assertEquals("b", sequencer.enqueue("b"))
    }

    @Test
    fun retriedItemEnqueuedBeforeFinishIsStartedByFinish() {
        val sequencer = PcResolveSequencer<String>()
        sequencer.enqueue("a")

        assertNull(sequencer.enqueue("a"))
        assertEquals("a", sequencer.finish())
    }

    @Test
    fun clearDropsQueueAndResetsActive() {
        val sequencer = PcResolveSequencer<String>()
        sequencer.enqueue("a")
        sequencer.enqueue("b")

        sequencer.clear()

        assertEquals("c", sequencer.enqueue("c"))
    }
}
