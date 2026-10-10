package com.enaboapps.switchify.pc.remote

import java.util.concurrent.atomic.AtomicReference

fun interface PcSwitchStopHandle {
    fun release()
}

interface PcRepeatSwitchStop {
    fun arm(onStop: () -> Unit): PcSwitchStopHandle

    fun isAvailable(): Boolean

    companion object {
        val None = object : PcRepeatSwitchStop {
            override fun arm(onStop: () -> Unit) = PcSwitchStopHandle {}
            override fun isAvailable() = false
        }
    }
}

class PcSwitchRepeatStopHook(private val availability: () -> Boolean = { false }) : PcRepeatSwitchStop {
    private class Armed(val onStop: () -> Unit)

    private val armed = AtomicReference<Armed?>(null)

    override fun arm(onStop: () -> Unit): PcSwitchStopHandle {
        val entry = Armed(onStop)
        armed.set(entry)
        return PcSwitchStopHandle { armed.compareAndSet(entry, null) }
    }

    override fun isAvailable(): Boolean = availability()

    fun isArmed(): Boolean = armed.get() != null

    fun requestStop(): Boolean {
        val entry = armed.getAndSet(null) ?: return false
        entry.onStop()
        return true
    }
}

object PcSwitchRepeatStop {
    @Volatile
    var availability: () -> Boolean = { false }

    val hook = PcSwitchRepeatStopHook { availability() }

    fun requestStop(): Boolean = hook.requestStop()
}
